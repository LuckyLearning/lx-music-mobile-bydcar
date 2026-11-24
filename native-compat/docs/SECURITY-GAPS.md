# 安全边界与已知缺口

云端复核之后的当前结论见 [安全复核.md](安全复核.md)。下面保留交接时的原始缺口描述。



## 现在实际验证了什么

Linux runner仅链接QuickJS core，没有quickjs-libc/std/os、Node、Java或反射桥。合成脚本由bwrap `--unshare-all --new-session --cap-drop ALL --clearenv`运行，只只读绑定运行时、系统动态库与合成bundle；不绑定HOME/原仓/用户数据。进程设NO_NEW_PRIVS与seccomp，拒绝新文件open/openat/openat2、socket/connect、exec、fork/clone、ptrace、mount、io_uring等；启动时实际探测openat/socket/exec返回EPERM才运行。
JS堆32MiB、栈512KiB、rlimit AS192MiB/CPU3s/FD16/core0/文件大小0；QuickJS interrupt墙钟约2s；RPC最多512、wire最多8MiB。三项冒烟见日志。**这些是工程初稿的护栏，不是第三方脚本安全认证或Android安全实现。**

## 必须先补的P0缺口

1. 没有Kotlin↔runner RPC broker或事件循环；stdin同步read会阻塞，缺独立宿主watchdog、错误/关闭状态机、stderr总量/全会话字节配额与消息schema审计。当前只跑无RPC的合成脚本。
2. seccomp采用deny-list，不是全面审核的allow-list；需要审查其他syscall与FD边界、CPU job/线程中断和异常退出清理。只有namespace没有语言沙箱漏洞保证。
3. NetworkPolicy只是纯模型，**没有启用网络**。真实transport必须在connect时固定已校验IP、每跳redirect重新审核，禁止本机/私网/特殊地址与本地数据外发，HTTPS证书验证、并发/响应解压后大小/上传限额、取消/超时全部真正实现。先解析后交URL.openConnection重新DNS并不安全。离线不应为兼容放宽策略。
4. RequestLedger有同步取消/代际判定，但未接传输，也没有测试，不能据此声称取消正确或无竞态。
5. Android `isolatedProcess`是独立UID权限边界，不等于任意宿主Binder/网络都安全；需不导出服务、最小Binder协议、禁止Java/文件/进程/任意Socket桥，并配合父进程资源/超时/崩溃重启管理。Linux bwrap不属于Android交付。
6. Crypto/Base64/JSON自写实现未做单测/golden/模糊测试；不要用于App签名验证、账号、数据加密或安全RNG。旧源crypto仅为协议兼容。

## 不可执行的内容

pdone README/提交已警告移除的“LX独家音源”含Node关机逻辑；从未下载/执行该历史样本。不得为测试执行恶意源。普通样本也要先逐个审查，不能因仓库名/health图片就信任。
Huibq已静态阅读，但含共享服务key；不复制进App、交接包、日志，不触发其服务。QingLiang只读了目录/blob元数据，未审脚本内容。两者动态兼容均未知。

## 凭据与许可

交接采用文本文件allow-list，排除第三方源、整个.git目录、工具下载、SDK/JDK、二进制、环境文件、签名keystore及一切凭据。LX Apache-2.0与QuickJS MIT许可证保留。pdone根目录未发现LICENSE、GitHub license=null；不据此授权再分发其脚本。用户主动导入与引用仓库作者声明不是本App可内置来源的许可。
