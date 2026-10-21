# 公开源码检查记录

检查日期：2026-10-22。

- 上传范围为源码、构建脚本、第三方许可与《奏（かなで）》展示截图；不含开发者签名私钥、用户 Cookie、API 凭据、个人日志、网易云 APK 或反编译文件。
- 上游 Joox 示例 Cookie 已删除；YouTube 注释示例 key 已改为占位符，修改记录见第三方声明。
- `runtime/src/crypto.js`、`kwDES.js`、`provider/kugou.js`、`provider/bodian.js` 已逐个从固定上游提交重新下载，以换行归一化后的 SHA-256 比对，内容一致。其固定加密参数、公开 RSA 公钥和摘要规则是上游公开的协议兼容代码，不是开发者或用户的账号凭据，也不是个人申请的 API key。
- 以上文件可在 [上游固定提交源码目录](https://github.com/UnblockNeteaseMusic/server/tree/1d64281c0fae81a4e3cf21f176706b0baa3a606d/src) 核对。没有从用户设备读取或填入这些常量。
- QQ Cookie 在运行时由使用者填写；本机接口令牌由程序随机生成。测试脚本中的测试令牌、Cookie 占位值和 example.com 地址仅用于隔离的本机假数据测试。

此记录说明本次整理范围，不是对未来提交或第三方服务的安全保证。
