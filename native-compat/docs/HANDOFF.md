# Cursor云端续做说明

## 当前停止点（必须按此理解）

用户改用Cursor云端后box已停止新增开发，仅保存与检查当前代码。**G1部分实现，G2未通过。** 所有实现均需review；编译成功不证明API21、协议或安全正确。
独立目录/分支与base见README；新增文本文件尚未commit，交接包及patch是实际新增文件来源。原始RN正式版无修改。

## 不可丢失的产品约束

1. Kotlin全原生Android，手机+BYD两个同等场景，共享核心。手动车机开关，不靠厂商品牌字符串强切。保留minSdk21，不能用overrideLibrary偷升。
2. BYD系统固定3:7、两个分区：应用全屏 / 占30%区 / 占70%区三种窗口状态。不是3/7窗口、也不是任意比例。与App内部歌词/封面7:3是两件事。
3. Android12、DiLink5.0、arm64-v8a，1920x984px，density1.5=240dpi。理论1280x656dp；实际扣insets/系统栏/56dp Dock后运行时测量。SDK_INT尚未读取，不能认定实测API32。2837MB RAM / 127068MB存储是用户当时可用量，不是总容量或App独占预算。
4. 手机型号/系统未知，不阻塞；准备API21、小/大屏、竖横屏、旋转、后台、耳机/蓝牙焦点测试。
5. 不兼容/不迁移旧RN播放数据：新App可从空数据开始，不读旧历史、队列/进度、歌单/收藏/设置/源/配对；不因此卸载旧版、删除数据。测试applicationId应独立；正式包名/签名/versionCode与更新渠道仍需单独决策。
6. 首个正式原生版保留已交付功能，不以MVP名义丢掉现有能力：车机内部7:3、56dp Dock、5秒设置/控制栏同显隐、磅字号无上限、悬浮歌词颜色/样式/交互、开机启动与启动自动播放两个开关、搜索/歌单/收藏/本地音乐/音质/失败重试等逐项对照Page。
7. 不默认新增双设备同步、下载、完整备份、账号、语音/Auto/AAOS。保留原有同步能力的迁移核对，但不能承诺手机车机自动双向同步。
8. 用户已授权开发，不需再次问同样问题；**发布/覆盖安装/改master/删除数据未授权**。不要重试dismiss的gh OAuth。现在按用户选择转Cursor云端隔离分支继续。

## 建议接续顺序（没有完成这些就不做完整客户端）

- Review当前草稿，补合成单测与legacy golden vectors；旧preload的已知bug行为要明确“兼容/修复/不支持”，不要默默更改。
- 完成宿主RPC/事件桥、资源与消息配额、超时取消、会话关闭/重启代际、红acted日志、mock transport与自动差分回归。当前C runner的阻塞stdin**缺少墙钟watchdog**。
- Android API21/arm64技术gate：选可编译的JNI/QuickJS方案，独立isolate UID Service与安全Binder代理；运行时/完整依赖闭包/新旧系统启动都要有证据。Linux bwrap/seccomp不能直接移植成Android安全承诺。
- 先合成安全与契约测试，再冻结pdone样本SHA/许可/权限。只有安全环境与审查达标才可做离线真实样本；再在已允许边界内做公开服务请求，遇挑战/限制停下，不绕过。
- 旧Mobile与新层对照初始化/URL/音质/异常/超时/取消/二进制/crypto；脚本本身失效与宿主不兼容分开计。
- Gate有完整结果后回报，再推进共享播放/数据核心→手机/车机自适应UI与现有全量功能→实车/手机→独立测试包与发布审查。

## API21依赖警告

Page中Compose1.9.5、ActivityCompose1.10.1、Media3 1.8.0、Room2.7.2、DataStore1.1.7仅核查过单AAR manifest/官方记录，不是完整传递依赖验证；本交接没有采用它们或生成Gradle工程。
Kotlin2.1.20只是当前宿主编译工具，`-jvm-target 1.8`不代表Android API21可用性。Android完整依赖图、API调用、manifest合并、R8/DEX和所有ABI均未检查。较新版Compose/Media3/Room有更高minSdk，必须固定兼容闭包或按Page原生View替代路线，不可静默升minSdk。
