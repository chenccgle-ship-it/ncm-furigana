# 第三方来源与许可

本仓库没有将上游音源引擎作为原创成果。发布 APK 时应同时提供本仓库对应版本的源码及本文件所列许可。

本地修改记录（2026-10-22）：`runtime/src/provider/joox.js` 移除上游硬编码示例 Cookie，仅保留环境变量入口；`runtime/src/provider/youtube.js` 将注释中的示例 API key 替换为占位符。本模块界面只启用酷我或 QQ，这两项清理不改变已启用音源的行为。

| 组件 | 版本 / 来源 | 用途与许可 |
| --- | --- | --- |
| UnblockNeteaseMusic/server | 0.28.0，提交 `1d64281c0fae81a4e3cf21f176706b0baa3a606d` | 备用音源匹配；LGPL-3.0-only。`runtime/src` 保留上游源码，适配入口为另写的 `runtime/resolver.js`。 |
| Kuromoji core / IPADIC | 0.9.0 | Apache-2.0；IPADIC 另有词典来源声明，随 APK 保留。 |
| Node.js | 16.15.1，Android arm64 | Node.js 及捆绑依赖许可见 `module/resources/assets/licenses/node`。 |
| dolby_beta | 提交 `258ab9b95671ecfb13a03636842890d9ca36fe75` | `app/src/main/jniLibs/arm64-v8a/libnode.so` 来源；保留 MIT 声明。未复制该项目其他业务功能。 |
| libxposed API | 102.0.0 | Apache-2.0；仅编译使用，不将 API 类打包进 APK。 |
| pino / pino-pretty | 6.14.0 / 7.6.1 | MIT；依赖版本记录在 `runtime/pnpm-lock.yaml`，安装时保留各包许可。 |

上游源码：[UnblockNeteaseMusic 对应提交](https://github.com/UnblockNeteaseMusic/server/tree/1d64281c0fae81a4e3cf21f176706b0baa3a606d)。原包元数据保存在 `runtime/upstream-package.json`，完整 LGPL 文本在 `runtime/COPYING` 和 `runtime/COPYING.LESSER`。可以修改这些脚本并通过 `pack-runtime.py` 重新打包。

Node 二进制：[固定提交下载地址](https://raw.githubusercontent.com/nining377/dolby_beta/258ab9b95671ecfb13a03636842890d9ca36fe75/app/src/main/jniLibs/arm64-v8a/libnode.so)，SHA-256：`eda5e07a48f9a4ffe4c463379d6bca6d382398693c508b262df848e045f2f788`。Node 源码及第三方声明：[v16.15.1](https://github.com/nodejs/node/tree/v16.15.1)。

[jp-furigana](https://github.com/Leleawa/jp-furigana) 为功能参考与灵感来源，不是本 Android 模块的上游发行版。网易云音乐及相关商标属于各自权利人，本项目与其无隶属关系。
