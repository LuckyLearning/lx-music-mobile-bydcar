# 第三方样本参考：只静态核对，未执行

- 仓库 https://github.com/pdone/lx-music-source ，固定main SHA `07dca9589e3734b140ec4ef396ee29dfe63687a2`；对应提交时间2026-11-22 11:22:56 北京时间UTC+8。
- 仓库是收集/分发/版本别名/探测辅助，不是LX宿主实现。README列desktop Windows/macOS/Linux、mobile Android/iOS等入口，但没有逐脚本逐客户端版本兼容矩阵。健康workflow每日01:00北京时间不是Mobile播放认证；GitHub Releases为空也不代表无版本文件。不要把latest滚动链接当固定验收样本。
- Huibq `huibq/1.2.0.js`：Git blob `f2028068db448a9e0b738106112eb300bcefbcc2`，2727bytes，SHA256 `a9adeb456690b523008038224091cee31977606302d79082eb06adb3c75c9146`。已通过工具读取并核对git blob；静态看到五源kw/kg/tx/wy/mg、musicUrl、128k/320k、on/request/send/utils/env/version，含共享服务key。**不包含脚本字节、key或远程服务请求。**
- QingLiang `qingliang/2.1.0.js`：Git blob `137716abd28c6ec3449ee661e686816f30545370`，26416bytes；只读目录元数据，未审实现，不可假设平台/音质/宿主兼容。
- 原外部Huibq样本保存在box `/workspace/lx-native-reference-samples/huibq-1.2.0.js`，不在git、不在交接包、不在App assets，未运行。云端如确需后续审查，请通过受支持GitHub只读工具获取固定blob并先校验hash，不用latest，不提取/硬编码其key。
- 恶意下架LX源禁止执行；作者声明未被我们动态复现。仓库无根LICENSE，样本分发许可不明，不默认捆绑。

## 云端只读复核（2026-11-24）

`tools/verify_sample_pins.py` 只向 GitHub blob API 取字节并在内存里算 SHA-256，不把正文写入仓库。

- Huibq：2727 字节，SHA-256 `a9adeb456690b523008038224091cee31977606302d79082eb06adb3c75c9146`，与交接记录一致。未审实现细节，未执行。
- QingLiang：26416 字节，SHA-256 `f5d589c4ebe583800b25fe12e35dfd4f807f1758bfbea83054df4b9a4f95aa29`。交接时没有文件哈希；这次补上哈希，内容仍未阅读，未执行。
- 固定树 `07dca9589e3734b140ec4ef396ee29dfe63687a2` 的根上没有 `LICENSE`。未下载已下架关机脚本。

## 未来验收矩阵

| 项目 | 作者声明/静态事实 | 旧Mobile基线 | 新宿主 |
|---|---|---|---|
| 客户端 | README同时列desktop/mobile，未给逐源保证 | 未运行 | 未运行 |
| Huibq初始化/五源交集 | 静态可读；与mobile env有分支 | 未运行 | 未运行 |
| 音质/URL | 静态128k/320k/musicUrl，不证明服务可用 | 未运行 | 未运行 |
| HTTP回调/超时/取消 | 依赖lx.request；服务返回多种code | 未运行 | 未运行 |
| 二进制/crypto | Huibq所用子集不能覆盖所有宿主API | 未运行 | 未运行 |
| 解析后媒体播放 | URL还需HTTP/MIME/过期/Media3实测 | 未运行 | 未运行 |
| QingLiang/其他源 | 尚未静态安全审核 | 不执行 | 不执行 |
