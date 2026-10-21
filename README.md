# 网易云日语注音 · NCM Furigana

基于 libxposed API 102 的 Android 模块，在网易云音乐日语歌词的汉字上方显示振假名，并集成可选的备用音源。

> 个人实验项目，目前只在 vivo V2419A / Android 15、Vector 2.2、网易云音乐 9.0.40（9004000，精简版）上验证。其他版本可能需要重新适配 Hook。

## 功能

- **离线日语注音**：使用 Kuromoji IPADIC 分词及读音对齐，在歌词上方绘制假名；支持普通歌词、逐字歌词及可读取的云盘歌词。保留原歌词的滚动和高亮。
- **独立开关**：注音、备用音源分别控制。
- **备用音源**：复用 [UnblockNeteaseMusic/server](https://github.com/UnblockNeteaseMusic/server) 的音源匹配实现，默认酷我，可选择 QQ 音乐并填写自己的 Cookie。
- **播放提示处理**：仅在备用音源匹配成功后，隐藏已适配版本底部迷你播放器的“VIP 听整首”入口。不修改账号会员状态。
- **本机诊断**：查看注音及音源匹配状态，便于反馈兼容性问题。

已有可播放的完整原地址会保留；地址缺失、出错或属于试听时才尝试备用音源。匹配失败会保留原结果。备用音源不保证曲目、版本或音质完全一致。

## 效果

《奏（かなで）》歌词页，截图仅用于展示注音效果，歌曲及歌词权利属于各自权利人。

<img src="docs/images/kanade.png" width="320" alt="奏（かなで）的日语歌词注音效果" />

## 安装

1. 从 [Releases](https://github.com/chenccgle-ship-it/ncm-furigana/releases) 下载 APK。
2. 在兼容 API 102 的框架中启用模块，作用域选择网易云音乐 `com.netease.cloudmusic`。
3. 打开模块，按需开启“注音”和“备用音源”，保存设置。
4. 完全结束网易云进程后重新打开，播放日语歌曲并进入歌词页。

本版本只打包 **arm64-v8a** 原生运行时，最低 Android 10。此最低版本来自构建配置，并不代表所有 Android 10+ 设备均经过测试。QQ Cookie 为可选项，保存在应用私有目录，不应上传到 Issue。

## 实现与限制

Java 负责 Xposed Hook、歌词快照、离线读音计算、Canvas 排版绘制和设置界面；Node.js 运行时负责调用上游音源提供器。解析服务仅绑定本机 `127.0.0.1`，使用随机端口和随机认证令牌。

布局和绘制依赖目标版本内部类，网易云更新可能导致功能失效。日语分词存在专名、多音字和非标准拼写误读；离线注音不是人工校对结果。内置 Node.js 16.15.1 已过维护期，升级运行时是后续工作之一。

构建步骤见 [BUILDING.md](BUILDING.md)，依赖来源及许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。问题反馈见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 致谢与许可

- [UnblockNeteaseMusic/server](https://github.com/UnblockNeteaseMusic/server)：备用音源引擎，保留其 LGPL-3.0-only 许可、版权和对应源码。
- [Leleawa/jp-furigana](https://github.com/Leleawa/jp-furigana)：日语振假名功能的参考与灵感来源。
- [Kuromoji](https://github.com/atilika/kuromoji)：离线日语分词与 IPADIC 读音词典。
- [dolby_beta](https://github.com/nining377/dolby_beta)：Android arm64 Node.js 运行时二进制来源。
- [libxposed](https://github.com/libxposed/api)：现代 Xposed API。

本项目原创代码采用 [MIT](LICENSE)；上游源码、词典和二进制依赖分别遵循原有许可证，详见第三方说明。项目不包含网易云 APK、反编译源码、音乐文件或用户凭据。请遵守音源服务条款及适用的版权要求。
