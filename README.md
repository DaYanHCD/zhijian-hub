# 指尖Hub

<p align="center">
  <a href="https://fingertipfuture.top/">官网</a> ·
  <a href="https://github.com/DaYanHCD/zhijian-hub/releases/latest">下载</a> ·
  <a href="https://github.com/DaYanHCD/zhijian-hub">GitHub</a> ·
  <a href="LICENSE">MIT License</a>
</p>

> 指尖Hub-OS 是一个独立的、纯原生、本地优先的 Android AI 助手。
> 它不依赖自有账号或聊天后端，用户用自己的 API Key 直连模型服务；除聊天外，
> 还支持悬浮搜题、设备工具、自动化任务、长期记忆、本地数据恢复和可校验的开源更新。

**当前开源版本：1.0.1**<br>
**桌面名称：指尖Hub-OS**<br>
**包名：`com.fingertipfuture.hub.os`**<br>
**系统要求：Android 7.0+**

## 下载

开源版安装包发布在 GitHub Releases，也可以从[项目官网](https://fingertipfuture.top/)下载。

- [下载最新开源版](https://github.com/DaYanHCD/zhijian-hub/releases/latest)
- [查看全部 Releases](https://github.com/DaYanHCD/zhijian-hub/releases)
- 1.0.1 安装包大小：5,488,022 字节
- SHA-256：`1b9e6dd4beac3904a9d96a20dfb2a8512272e44d04dff4197e87571ebe83f6d8`
- [第三方组件与素材许可](THIRD_PARTY_NOTICES.md)

安装包使用正式签名，不包含任何 API Key。安装后需要在应用内添加自己的模型供应商。

开源版使用独立的更新清单，发布 1.0.1 后可在「设置 → 关于 → 检查更新」中直接升级到
后续的 1.0.2、1.0.3 等版本。

## 界面

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/01-welcome.webp" width="220" alt="新对话"><br><sub>新对话</sub></td>
    <td align="center"><img src="docs/screenshots/02-automation.png" width="220" alt="自动化任务"><br><sub>自动化任务</sub></td>
    <td align="center"><img src="docs/screenshots/03-storage.png" width="220" alt="存储与扩展"><br><sub>存储与扩展</sub></td>
    <td align="center"><img src="docs/screenshots/04-extensions.png" width="220" alt="动态表情扩展"><br><sub>动态表情扩展</sub></td>
  </tr>
  <tr>
    <td align="center" colspan="4"><img src="docs/screenshots/05-update.png" width="220" alt="1.0.1 更新日志"><br><sub>独立更新日志</sub></td>
  </tr>
</table>

## 它解决什么问题

- **不想被单一模型绑定**：内置常见供应商预设，也可以填写任意 OpenAI 兼容地址和模型名。
- **希望助手真正能做事**：让模型按需调用安卓工具、创建自动化任务、管理长期记忆，并在敏感操作前请求授权。
- **希望知道助手在做什么**：把模型思考、工具执行、搜索、设备操作和组织回答放进同一条时间线。
- **希望数据留在本机**：对话、附件、记忆、API Key 和备份默认由用户自己掌握，并可选择跨卸载加密恢复。

## 主要功能

- **多供应商与多助手**：OpenAI、Gemini、DeepSeek、Kimi、智谱、千问等 25 家预设，也支持任意 OpenAI 兼容接口；不同助手可以拥有独立模型、参数、提示词、记忆和技能。
- **流式回答与思考时间线**：原生 SSE 流式解析，可随时停止；模型 reasoning、工具执行、搜索和设备操作统一进入时间线。
- **悬浮搜题**：用悬浮球截取屏幕、自由圈选题目或文字并交给图像理解模型，答案支持复制，Token 单独统计。
- **自动化任务与提醒**：支持每天、指定日期、每周任务、精确闹钟、后台重试、系统通知，以及开机、修改时间和时区变化后的恢复。
- **原生工具调用**：模型可以按需加载技能并连续调用安卓工具，包括通知、剪贴板、日历、联系人、短信编辑页、媒体库、应用、蓝牙、Wi-Fi 和网络状态。
- **长期记忆**：助手可以读取、保存、修改和删除记忆；自动沉淀前请求确认，并可配置全局记忆和历史聊天引用。
- **联网搜索与天气**：支持并行搜索、短时缓存、结果条数设置、Bing 备用入口，以及按地点规划天气查询。
- **多模态输入**：支持图片、文件、位置和语音消息；长文本使用独立全屏输入页，图片支持全屏查看和保存到手机。
- **本地优先数据**：手写 SQLite 会话库、附件和 Keystore 密钥都在本机；授权公共存储后，聊天、附件和提醒可跨卸载加密恢复。
- **备份与恢复**：支持本机导出、恢复和 WebDAV 加密备份，聊天与文件可分别恢复，并包含大小、路径和完整性检查。
- **存储与扩展管理**：分别查看软件、扩展、图片、语音和文件占用；动态 Noto 表情按需下载、SHA-256 校验并自动启用。
- **完整界面体验**：简体中文、繁体中文、应用内语言切换、浅色 / 深色主题、Markdown、表格、代码块、LaTeX 和长会话分页阅读。
- **用量统计**：聊天热力图、输入/输出 Token、缓存节省、搜索费用、模型使用次数和悬浮搜题 Token。
- **独立开源更新**：从 1.0.1 起可检查、下载、校验并安装后续版本；更新清单强制校验渠道、包名、大小和 SHA-256。

## 隐私与数据

指尖Hub 不运营自有聊天后端，也不要求注册账号。

- API Key 使用 Android Keystore 保护的本地密钥加密保存。
- 聊天记录和附件默认保存在本机 SQLite 与应用私有目录。
- 用户授权公共存储后，聊天、附件和提醒会加密增量保存到 `Documents/指尖Hub`，从而支持卸载重装后的恢复。
- 请求只发送给用户选择的模型、搜索或 WebDAV 服务。
- 应用不包含广告 SDK、统计 SDK 或第三方 UI / 网络 / Markdown 渲染库。
- WebDAV 备份在上传前由“地址 + 用户名 + 密码”派生的密钥加密。
- 短信能力只打开系统编辑页，不会静默发送；敏感设备能力均需用户单独授权。

具体说明见[隐私政策](https://fingertipfuture.top/privacy.html)。

## 技术实现

- Kotlin + Jetpack Compose + Material 3
- `HttpURLConnection` + `org.json`，没有引入 OkHttp、Retrofit 或第三方 JSON 库
- 原生 SSE 流式解析
- 手写 SQLite 数据层：会话、节点、消息、附件与用量
- Android Keystore + AES-GCM 保存本机密钥
- WorkManager、AlarmManager 与系统通知实现可恢复的自动化任务
- 自建工具注册、权限策略和原生工具调用循环
- 单 Activity、Compose 页面状态管理，没有 WebView 和跨平台框架

## 构建

需要 JDK 17+、Android SDK（compileSdk 36）。

```bash
./gradlew assembleOpen
```

Windows：

```powershell
.\gradlew.bat -g 'D:\安卓开发环境\gradle-home' '-Dorg.gradle.java.home=D:\安卓开发环境\jdk' --no-daemon assembleOpen
```

`local.properties` 中的 SDK 路径不随仓库分发。未提供签名材料时仍可构建调试或未签名包；
仓库不包含发布密钥、API Key 或用户数据。

## 项目结构

```text
app/src/main/java/com/fingertipfuture/hub/
├── MainActivity.kt          应用入口与初始化
├── core/                    版本与应用常量
├── data/                    AI 客户端、SQLite、设置、备份、附件与搜索
│   └── agent/               工具注册、权限策略、技能目录与原生调用循环
├── ui/
│   ├── ChatScreen.kt        对话主页面
│   ├── chat/                消息、输入、思考时间线、副屏等组件
│   ├── settings/            设置、助手、技能、数据管理与关于页
│   └── provider/            供应商配置
├── floating/                悬浮搜题与屏幕圈选
├── update/                  更新清单、下载与安装逻辑
└── util/                    格式化工具
```

## 开源说明与致谢

本项目是独立项目，不是 RikkaHub 的官方分支。信息架构和交互设计参考了
[RikkaHub](https://github.com/rikkahub/rikkahub)（AGPL-3.0），但没有复制它的代码或素材。

第三方素材：

- 聊天页细线图标来自 [Lucide](https://lucide.dev)（ISC）。
- 设置与功能页图标来自 Google Material Symbols（Apache-2.0）。
- 供应商 Logo 来自 [LobeHub Icons](https://github.com/lobehub/lobe-icons)（MIT）。
- 静态与动画表情来自 Google Noto Emoji（SIL OFL 1.1）。

指尖Hub 与 OpenAI、Google、DeepSeek 等模型或搜索服务商没有隶属关系，
相关名称和商标归各自权利人所有。

## 许可证

本项目使用 MIT License，详见 [LICENSE](LICENSE)。
