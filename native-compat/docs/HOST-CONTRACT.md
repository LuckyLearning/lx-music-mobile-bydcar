# LX Mobile最低宿主契约：源码提取，未完成动态对照

云端已用官方 preload 做了一轮合成对照，结论和仍未覆盖的真实音源见 [契约对照.md](契约对照.md)。下面保留交接时的提取记录。



固定源码基准：`ad77216fdb3d195072cdddbe687006715971ecad`。
前缀 `https://github.com/LuckyLearning/lx-music-mobile-bydcar/blob/ad77216fdb3d195072cdddbe687006715971ecad/`。

## 责任分界

`src/utils/musicSdk/index.js`承担平台元数据、搜索/歌单等适配；用户音源脚本主要解析musicUrl，不能把“能运行URL源”等同完整搜索/歌单移植。
`src/core/init/userApi/index.ts`承担业务请求登记和约20s外层超时；`request.js`是网络broker参考；preload是脚本可见协议。

## preload契约

| 范围 | 原始源码行为 | 新层状态 |
|---|---|---|
| 版本/环境 | `lx.version='2.0.0'`、`lx.env='mobile'`；手机车机都不得冒充desktop | 固定baseline，未连接新宿主 |
| 初始化 | `lx_setup(key,id,name,description,version,author,homepage,rawScript)`；缓存/删除原生hook；`send(inited,{sources})` | baseline未执行 |
| 平台/动作 | kw/kg/tx/wy/mg/local；线上musicUrl，本地musicUrl/lyric/pic；actions与qualitys按宿主交集过滤 | 未做对照 |
| 音质 | 128k/320k/flac/flac24bit，各源取交集；local无这些在线音质 | 未测 |
| 回调 | on(request)以this=lx调用，参数{source,action,info}；期望thenable | 未测 |
| URL/歌词 | musicUrl/pic是HTTP(S)字符串<=2048；lyric<=51200；tlyric/rlyric<5120，lxlyric<8192字符 | baseline参考；新transport会默认拒绝明文HTTP，兼容取舍需记录 |
| request | callback(err,response,body)，response={statusCode,statusMessage,headers,body}；返回cancel函数 | 尚无桥/transport |
| timeout | 网络默认13s，调用方最多60s；业务外层20s要独立管理 | 模型有字段，计时器/桥未实现 |
| timer | setTimeout/clearTimeout；原clear只删JS回调，不取消native timer | native桥未实现 |
| 额度 | 单utility字符串1Mi字符，nativeCall JSON2Mi字符 | Kotlin JSON限制2Mi字符，C wire8Mi字节；完整跨边界未测 |

参考行号：preload L3-87、L128-258、L304-424、L446-528、L535-591。

## 原生工具ABI（必须按调用顺序，不可按工具名称猜）

- `__lx_native_call__(key,action,JSON.stringify(data))`。
- `__lx_native_call__set_timeout(id,timeoutMs)`。
- `utils_str2b64(string)`：UTF8→Base64.NO_WRAP。
- `utils_b642buf(base64)`：返回**JSON字符串**，数组元素是Java signed byte(-128..127)，JS再转Uint8Array。
- `utils_str2md5(encodeURIComponent(string))`：原Java URLDecoder UTF8后MD5、lowerhex。
- `utils_aes_encrypt(dataB64,keyB64,ivB64,mode)`，CBC模式AES/CBC/PKCS7Padding；原“aes-128-ecb”实际传`AES`，不是源码常量名暗示的NoPadding；IV截断/补零到16，空iv走无iv重载。
- `utils_rsa_encrypt(dataB64,publicKeyWithoutPem,padding)`；公开preload传RSA/ECB/NoPadding。

已读Java证据：`android/app/src/main/java/cn/toside/music/mobile/userApi/QuickJS.java` L60-123；`crypto/AES.java` L23-57；`crypto/RSA.java` L70-88。当前Kotlin只是按ABI写了适配，JCA provider错误/空字符串返回/默认padding都需Android golden vectors确认。

## 必须显式记录的兼容陷阱

- preload Buffer不是Node Buffer；手写UTF8只覆盖到3byte路径，非BMP/emoji行为有quirk；`bufToString(base64)`还经过字符串编码，不能假定任意二进制无损。必须有0x80..FF/emoji golden，不能为了跑通悄悄换语义。
- randomBytes用Math.random，不可用于新版App安全密钥。
- 请求完成把aborted置true；重复取消可能触及null；外层取消原实现存在空实现。新核心应消除竞态/泄漏，同时保留脚本可见合理语义并单列修复差异。
- request.js文本body尝试JSON.parse；binary、form、formData、headers、顺序/错误需要真实对照；formData原分支有遍历form的疑似bug。当前Kotlin没有这些传输实现，明确拒绝formData，不得视为通过。
- 原eval/Function限制与对象冻结不是权限沙箱；不能因此把第三方脚本放入裸JVM/Node运行。

## 尚未写的回归清单

初始化成功/失败/重复、五平台交集、thenable/Promise顺序、URL/歌词限额、timer取消、HTTP成功/失败/JSON/text/binary/form、超时、重复取消/取消后响应、代际重启、UTF8/Base64/MD5/AES/RSA、消息/内存/CPU配额、宿主能力拒绝、私网/重定向/DNS重绑定、旧Mobile与新层差分。
