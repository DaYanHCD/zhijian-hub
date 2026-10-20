package com.fingertipfuture.hub.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fingertipfuture.hub.core.AppConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 检查更新 / 下载安装包的全部逻辑。
 *
 * 做成 object + Compose 状态，和 [com.fingertipfuture.hub.data.ProviderStore] 一个路数：
 * 这个 App 的界面只有一页，没必要为它引入 ViewModel 那一套（少一层依赖）。
 *
 * 注意：**安装动作由界面层调用 [ApkInstaller] 完成**，这里只负责把安装包准备好
 * 并给出 [UpdateState.ReadyToInstall]。
 */
object UpdateController {

    private const val TAG = "HubUpdate"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var appContext: Context? = null
    private var repository: UpdateRepository? = null

    /** 单个进行中的任务，重复触发时先取消上一个，避免并发下载。 */
    private var task: Job? = null

    /** 启动时的静默检查只执行一次。 */
    private var startupCheckDone = false

    /**
     * 「开机清一次旧安装包」也只做一次。
     *
     * ⚠️ 转屏 / 切深色都会重走 Activity.onCreate → initialize()。如果每次都清，
     * 正在下载的那个 `hub-*.apk.part` 会被删掉：文件被删后句柄还在写，
     * 最后 rename 失败，用户看到的就是"转个屏下载就失败了"。
     */
    private var startupCleanupDone = false

    var state: UpdateState by mutableStateOf(UpdateState.Hidden)
        private set

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (!AppConfig.OPEN_SOURCE_BUILD) {
            repository = UpdateRepository(AppConfig.manifestUrl(debugBaseUrlOverride(app)))
        }
        if (!startupCleanupDone) {
            startupCleanupDone = true
            // 升级成功后会以新版本重启，此时旧安装包已完成使命
            ApkStorage.clearDownloadedApks(app)
        }
    }

    /** 用户主动点击「检查更新」：无论结果如何都给反馈。 */
    fun checkForUpdate() {
        if (AppConfig.OPEN_SOURCE_BUILD) {
            state = UpdateState.UpToDate(AppConfig.CURRENT_VERSION_NAME)
            return
        }
        startCheck(quiet = false)
    }

    /** 应用启动时静默检查：只有真的发现新版本才弹窗，失败不打扰用户。 */
    fun checkForUpdateOnStartup() {
        if (startupCheckDone) return
        startupCheckDone = true
        if (AppConfig.OPEN_SOURCE_BUILD) return
        startCheck(quiet = true)
    }

    private fun startCheck(quiet: Boolean) {
        val repo = repository ?: return
        task?.cancel()
        task = scope.launch {
            if (!quiet) state = UpdateState.Checking
            try {
                val manifest = repo.fetchManifest()
                val latest = manifest.latest
                if (latest.versionCode > AppConfig.CURRENT_VERSION_CODE) {
                    state = UpdateState.Available(latest)
                } else if (!quiet) {
                    state = UpdateState.UpToDate(AppConfig.CURRENT_VERSION_NAME)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                android.util.Log.e(TAG, "检查更新失败: " + t.javaClass.name + " / " + t.message, t)
                if (!quiet) {
                    state = UpdateState.Failed(
                        error = t.toUpdateError(),
                        httpCode = (t as? HttpStatusException)?.code,
                    )
                }
            }
        }
    }

    /** 用户在「发现新版本」弹窗里点了「立即更新」。 */
    fun downloadUpdate(info: VersionInfo) {
        val repo = repository ?: return
        val app = appContext ?: return
        task?.cancel()
        task = scope.launch {
            // 下载前先清掉上一次的残留，保证同时最多只存在一个包
            ApkStorage.clearDownloadedApks(app)
            state = UpdateState.Downloading(info, downloaded = 0L, total = info.apkSize)
            try {
                val apk = repo.downloadApk(info, ApkStorage.downloadDir(app)) { downloaded, total ->
                    state = UpdateState.Downloading(info, downloaded, total)
                }
                state = UpdateState.ReadyToInstall(info, apk)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                android.util.Log.e(TAG, "下载安装包失败: " + t.javaClass.name + " / " + t.message, t)
                state = UpdateState.Failed(
                    error = when (t) {
                        is VerificationException -> UpdateError.VERIFY
                        is HttpStatusException -> UpdateError.SERVER
                        else -> UpdateError.DOWNLOAD
                    },
                    httpCode = (t as? HttpStatusException)?.code,
                    retryWith = info,
                )
            }
        }
    }

    /** 「重试」：下载失败则重下，检查失败则重新检查。 */
    fun retry() {
        val retryWith = (state as? UpdateState.Failed)?.retryWith
        if (retryWith != null) downloadUpdate(retryWith) else checkForUpdate()
    }

    // ---- 更新日志 ----

    /**
     * 更新日志单独用一个任务：它和「检查更新 / 下载」互不干扰
     * —— 用户完全可能一边下载一边点开更新日志看看。
     */
    private var changelogTask: Job? = null

    var changelog: ChangelogState by mutableStateOf(ChangelogState.Hidden)
        private set

    /** 打开「更新日志」：去服务器读清单，把 latest + history 列出来。 */
    fun showChangelog() {
        if (AppConfig.OPEN_SOURCE_BUILD) {
            changelog = ChangelogState.Loaded(
                UpdateManifest(latest = LocalChangelog.entries().first()),
            )
            return
        }
        val repo = repository ?: return
        changelogTask?.cancel()
        changelog = ChangelogState.Loading
        changelogTask = scope.launch {
            changelog = try {
                ChangelogState.Loaded(repo.fetchManifest())
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                android.util.Log.e(TAG, "读更新日志失败: " + t.javaClass.name + " / " + t.message, t)
                ChangelogState.Failed(t.toUpdateError())
            }
        }
    }

    fun dismissChangelog() {
        changelogTask?.cancel()
        changelog = ChangelogState.Hidden
    }

    /**
     * 安装包准备好了、权限也有，但拉起系统安装器失败（个别 ROM 会拒绝）。
     *
     * 这种情况必须让用户看见 —— 否则界面只是"弹窗关掉了"，什么都没发生。
     */
    fun installLaunchFailed() {
        state = UpdateState.Failed(error = UpdateError.INSTALL)
    }

    /** 缺少安装权限时提示用户。 */
    fun showInstallPermission() {
        state = UpdateState.NeedInstallPermission
    }

    /**
     * 关闭弹窗。
     *
     * @param cancelTask 是否中止进行中的任务；下载中关闭弹窗即取消下载。
     */
    fun dismiss(cancelTask: Boolean = true) {
        if (cancelTask) task?.cancel()
        state = UpdateState.Hidden
    }

    /**
     * 已把安装包交给系统安装器。
     *
     * 这里**只关闭弹窗、不删除安装包** —— 安装器此刻可能仍在读该文件，删了会安装失败。
     * 清理留到应用下次启动时（见 [initialize]）。
     */
    fun onInstallLaunched() {
        state = UpdateState.Hidden
    }

    /**
     * debug 包专用：允许用本机偏好覆盖更新地址（正式包永远返回 null）。
     * 详见 [AppConfig.DEBUG_OVERRIDE_PREFS]。
     */
    private fun debugBaseUrlOverride(context: Context): String? {
        if (!AppConfig.IS_DEBUG) return null
        return context.getSharedPreferences(AppConfig.DEBUG_OVERRIDE_PREFS, Context.MODE_PRIVATE)
            .getString(AppConfig.DEBUG_OVERRIDE_KEY, null)
    }

}
