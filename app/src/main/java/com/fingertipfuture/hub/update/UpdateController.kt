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
 * 版本检查、APK 下载和安装状态。
 *
 * 更新日志仍随 APK 本地打包；这里只负责查询服务器版本清单和处理安装包。
 */
object UpdateController {

    private const val TAG = "HubUpdate"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var appContext: Context? = null
    private var repository: UpdateRepository? = null
    private var task: Job? = null
    private var startupCheckDone = false
    private var startupCleanupDone = false

    var state: UpdateState by mutableStateOf(UpdateState.Hidden)
        private set

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        LocalChangelog.initialize(app)
        repository = UpdateRepository(AppConfig.manifestUrl(debugBaseUrlOverride(app)))
        if (!startupCleanupDone) {
            startupCleanupDone = true
            ApkStorage.clearDownloadedApks(app)
        }
    }

    /** 用户打开 App 时静默查询；只有发现新版本才弹窗。 */
    fun checkForUpdateOnStartup() {
        if (startupCheckDone) return
        startupCheckDone = true
        startCheck(quiet = true)
    }

    /** 用户主动点击「检查更新」：无论成功失败都提供反馈。 */
    fun checkForUpdate() {
        startCheck(quiet = false)
    }

    private fun startCheck(quiet: Boolean) {
        val repo = repository ?: return
        task?.cancel()
        task = scope.launch {
            if (!quiet) state = UpdateState.Checking
            try {
                val manifest = repo.fetchManifest()
                requireMatchingUpdateChannel(
                    manifest = manifest,
                    expectedChannel = AppConfig.UPDATE_CHANNEL,
                    expectedPackage = AppConfig.APPLICATION_ID,
                    requireExplicit = AppConfig.OPEN_SOURCE_BUILD,
                )
                val latest = manifest.latest
                if (latest.versionCode > AppConfig.CURRENT_VERSION_CODE) {
                    state = UpdateState.Available(latest)
                } else if (!quiet) {
                    state = UpdateState.UpToDate(AppConfig.CURRENT_VERSION_NAME)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                android.util.Log.e(TAG, "检查更新失败: ${t.javaClass.name} / ${t.message}", t)
                if (!quiet) {
                    state = UpdateState.Failed(
                        error = t.toUpdateError(),
                        httpCode = (t as? HttpStatusException)?.code,
                    )
                }
            }
        }
    }

    fun downloadUpdate(info: VersionInfo) {
        val repo = repository ?: return
        val app = appContext ?: return
        task?.cancel()
        task = scope.launch {
            ApkStorage.clearDownloadedApks(app)
            state = UpdateState.Downloading(info, downloaded = 0L, total = info.apkSize)
            try {
                val apk = repo.downloadApk(info, ApkStorage.downloadDir(app)) { downloaded, total ->
                    state = UpdateState.Downloading(info, downloaded, total)
                }
                state = UpdateState.ReadyToInstall(info, apk)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                android.util.Log.e(TAG, "下载安装包失败: ${t.javaClass.name} / ${t.message}", t)
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

    fun retry() {
        val retryWith = (state as? UpdateState.Failed)?.retryWith
        if (retryWith != null) downloadUpdate(retryWith) else checkForUpdate()
    }

    fun installLaunchFailed() {
        state = UpdateState.Failed(error = UpdateError.INSTALL)
    }

    fun showInstallPermission() {
        state = UpdateState.NeedInstallPermission
    }

    fun dismiss(cancelTask: Boolean = true) {
        if (cancelTask) task?.cancel()
        state = UpdateState.Hidden
    }

    fun onInstallLaunched() {
        state = UpdateState.Hidden
    }

    private fun debugBaseUrlOverride(context: Context): String? {
        if (!AppConfig.IS_DEBUG) return null
        return context.getSharedPreferences(AppConfig.DEBUG_OVERRIDE_PREFS, Context.MODE_PRIVATE)
            .getString(AppConfig.DEBUG_OVERRIDE_KEY, null)
    }

}
