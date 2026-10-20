package com.fingertipfuture.hub.update

/**
 * 「更新日志」弹窗的状态。
 *
 * 日志内容来自服务器清单：`latest` 是当前最新版，`history` 是更早的版本
 * （发布脚本会把每一版追加进 history）。
 */
sealed interface ChangelogState {

    data object Hidden : ChangelogState

    data object Loading : ChangelogState

    data class Loaded(val manifest: UpdateManifest) : ChangelogState

    data class Failed(val error: UpdateError) : ChangelogState
}