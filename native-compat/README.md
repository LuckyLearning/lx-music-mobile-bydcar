# LX 原生兼容门（未完成的音源兼容，已完成合成契约）

这是隔离分支上的兼容运行时，不是正式客户端，也不是 APK。官方 React Native 应用、`master`、标签和 Release 说明都没有改。

基准提交：`ad77216fdb3d195072cdddbe687006715971ecad`（v0.0.11，tree `d48c9ebb8fd79f926e7ed4c325f98bbbf32aba56`）。

## 现在的阶段

| 项目 | 结果 |
|---|---|
| 官方 `user-api-preload.js` 在受限 QuickJS 里执行，并用测试核对初始化、音质交集、Promise 顺序、歌词边界、定时器、取消、HTTP 整形 | 通过。证据在 `reports/gate-results.txt`，`SUMMARY failed=0` |
| 无限循环、内存上限、父进程静默看门狗、子进程墙钟看门狗 | 通过。退出码分别是 70、70、父进程杀死 sleep（137）、子进程 72 |
| 公开 HTTPS `https://example.com/`，地址钉死且为公网 | 通过。没有请求任何音源服务 |
| arm64-v8a / Android API 21 的 JNI 库编译 | 编过，没有在设备或模拟器加载。见 `reports/android-arm64.txt` |
| 候选 Compose 1.9.5 / Media3 1.8.0 / Room 2.7.2 / DataStore 1.1.7 的运行时 AAR `minSdk` | 176 个读到数字的 AAR 最大是 21，没有高于 21 的。见 `reports/minsdk-closure.txt`。没有做 Gradle manifest 合并，也没有编进 APK |
| 真实第三方音源、旧版手机基线、搜索/歌单移植、播放器、车机 UI | **没有做，不能写成兼容通过** |

详细复现、差距和安全复核：

- [进度与复现](docs/进度与复现.md)
- [契约对照](docs/契约对照.md)
- [安全复核](docs/安全复核.md)

## 两条职责

用户脚本负责它声明的 `musicUrl`（本地源还可以有 `lyric` / `pic`）。搜索、歌单和跨平台找歌在 `src/utils/musicSdk`，不是把脚本跑起来就能代替的。两边以后都要有。这一步只证明脚本宿主契约，没有移植平台适配器。

## 许可证

本目录新增代码沿用仓库 Apache-2.0。QuickJS 2026-06-04 是 MIT，构建时下载，不放进仓库。pdone/lx-music-source 根目录没有 LICENSE，脚本没有打进包，也没有执行。
