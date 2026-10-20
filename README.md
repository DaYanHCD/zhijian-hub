# 指尖Hub

原生 Android AI 对话客户端：多供应商、流式回答、思考时间线、联网搜索、记忆与本地数据。

当前开源版本：**1.0.0**，桌面名「指尖Hub-OS」，包名
`com.fingertipfuture.hub.os`。安装包从 GitHub Releases 下载。

- 纯原生实现：Compose + `HttpURLConnection` + `org.json`
- 供应商预设 + 自定义兼容地址
- 会话存本地 SQLite，支持导出 / 导入 / WebDAV 备份
- 浅色 / 深色主题，界面可跟随系统

## 构建

需要 JDK 17+ 与 Android SDK（compileSdk 36）。

```bash
./gradlew assembleOpen
```

Windows：

```powershell
.\gradlew.bat -g 'D:\安卓开发环境\gradle-home' '-Dorg.gradle.java.home=D:\安卓开发环境\jdk' --no-daemon assembleOpen
```

`local.properties` 里的 `sdk.dir` 指向本机 SDK，不随仓库分发。直接构建 `assembleOpen`
不需要发布签名配置；需要签名的版本由维护者在仓库外单独配置签名材料。

## 目录

```
app/src/main/java/com/fingertipfuture/hub/
├── MainActivity.kt          入口
├── data/                    OpenAI 兼容客户端、本地库、设置存储
├── ui/ChatScreen.kt         对话页
├── ui/chat/                 对话页组件（副屏、输入、消息）
└── update/                  应用内更新
```

## 说明

本项目在信息架构与交互上参考了 RikkaHub（AGPL-3.0）的产品思路，
未复制其代码，也没有引入 WebView 或第三方 UI / 网络 / Markdown 渲染库。

开源版不连接闭源 Beta / 正式版的更新清单；更新记录取自本仓库内置日志。

## 许可证

MIT License，详见 [LICENSE](LICENSE)。
