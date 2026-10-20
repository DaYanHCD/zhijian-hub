package com.fingertipfuture.hub.update

import com.fingertipfuture.hub.core.AppConfig

/**
 * 内置更新日志：服务器清单拉不到时用来兜底，保证更新日志页不会空白。
 *
 * 服务器可用时仍以服务器清单为准；这里只保留当前版本这一条，
 * 不复制整套历史，免得客户端和服务端两份内容长期漂移。
 */
object LocalChangelog {

    fun entries(): List<VersionInfo> = listOf(
        VersionInfo(
            versionCode = AppConfig.CURRENT_VERSION_CODE,
            versionName = AppConfig.CURRENT_VERSION_NAME,
            releasedAt = "2026-10-18",
            changelog = listOf(
                "新增：语音气泡改为圆形播放键 + 波形 + 分:秒，宽度随语音时长增长，最长支持 60 秒",
                "新增：数据管理附件总览页，图片直接显示；对话入口可直达搜索并返回数据管理",
                "新增：屏幕时间、日历、应用列表、联系人、短信和彩信的读取步骤加入思考链",
                "优化：已安装应用读取完整列表，并区分用户应用和系统应用",
                "优化：全局权限同步所有助手；进入权限页时提示先开启系统权限",
                "优化：记忆只在与问题相关或明确询问时注入，不再每条消息自动调用",
                "优化：聊天页搜索只搜索当前会话；副屏与全界面内容延伸到透明手势栏下方",
                "调整：语音转文字暂未开放；关于页新增「赞赏作者」暂未开发入口",
            ),
        ),
    )
}
