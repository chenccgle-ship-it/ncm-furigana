# 从源码构建（Windows）

可用 `NCM_SIGNING_KEY` 指定已有兼容密钥文件路径。当前脚本用于个人预览构建，使用固定的测试别名和口令；正式维护时请改为受保护的发布签名流程。

当前项目使用 PowerShell 构建脚本，没有 Gradle 工程。需要 JDK 21、Python 3.10+、Node.js 22+ 和 pnpm 11.25.0；设置 `JAVA_HOME`，将 Python、Node、pnpm 加入 PATH。

```powershell
python scripts/setup-deps.py
Push-Location runtime
pnpm install --prod --frozen-lockfile --ignore-scripts --node-linker=hoisted
Pop-Location
python pack-runtime.py
./build-probe.ps1
./test-readings.ps1
python tests/test-resolver.py
python verify-apk.py
```

`setup-deps.py` 从 Google、Maven Central 和固定的 GitHub 提交获取依赖并校验 SHA-256。下载缓存位于 `.build-cache`，工具位于 `tools`；词典、Android 原生运行时及生成的 resolver.zip 不提交 Git。运行时 JS 的固定依赖记录在锁文件中，不需要执行依赖包安装脚本。

构建输出为 `outputs/ncm-furigana-0.5.4.apk`。首次本地构建会在 `tools/probe-debug.p12` 生成个人测试签名，后续重复使用；该文件不能提交。公开发布如继续使用本地构建脚本，请自行妥善保管签名。自行构建的签名与 Releases 不同，不能直接覆盖已有安装，需先卸载旧模块（会清除模块设置）。

读音测试使用真实 APK 中的词典；音源测试使用本机确定性假数据，验证鉴权、参数、缓存与 QQ Cookie 传递，不连接音乐平台，因此不保证实时音源可用。

手动验证：网易云 9.0.40，普通日语歌词、逐字歌词、云盘歌词、注音开关、原地址保留、备用音源匹配成功/失败，以及底部迷你播放器提示。其他版本的内部类、字段和绘制流程可能发生变化，不能直接视为兼容。

需要替换上游引擎时，保留 `runtime/src`、上游元数据和许可，修改后重新安装依赖并打包。Android 运行时的升级需要适配与真机验证，不能将桌面 Node 可执行文件直接放进 APK。
