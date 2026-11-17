# [BYD] 修改清单

> 记录本 fork 相对上游 `lyswhut/lx-music-mobile` 的**每一处改动**。
> 用途：上游 merge 冲突时，据此判断哪些是必须保留的车机定制。
> 原则：新增文件优先；必改的上游文件在此登记。

## A. 新增文件（与上游零冲突）

| 文件 | 说明 |
| --- | --- |
| `.github/workflows/byd-release.yml` | BYD 独立出包工作流（tag 触发 / 手动触发，WindTone 同款签名） |
| `scripts/publish-release-repo.sh` | 发布到公共分发仓 `LuckyLearning/Release`（与 WindTone 同一套目录约定，孤立提交 + force push，保留最近 5 版） |
| `scripts/gen-release-manifest.py` | 生成 `latest.json`（含纯文本 `desc`，供更新弹窗直接显示）并刷新公共仓 README |
| `src/config/release.ts` | 公共分发仓常量与镜像线路（jsDelivr → gh-proxy → ghproxy.net → 官方 raw 兜底） |
| `android/.../lyric/StrokeTextView.java` | 多行歌词的描边/空心字视图（8 向偏移模拟 outline），多行模式不再依赖纯 TextView |
| `src/.../LyricDesktop/ViewHeight.tsx` / `Position.tsx` / `LyricStyle.tsx` | 窗口高度百分比 / 位置百分比 / 加粗描边空心字 设置项 |
| `docs/BYD-上游同步指南.md` | 上游同步操作规范 |
| `docs/BYD-修改清单.md` | 本文件 |
| `android/keystore.properties` | 本地签名配置（已 gitignore，不入库，仅本地存在） |

## B. 修改的上游文件（merge 冲突高发，逐一登记）

（随开发推进逐条补充；每条注明文件、改动内容、`[BYD]` 标记位置。）

| 文件 | 改动内容 | 标记 | 冲突处理 |
| --- | --- | --- | --- |
| `android/app/src/main/AndroidManifest.xml` | 三七分屏：application/Activity 加 `resizeableActivity=true`、两条 meta-data（`BYD_SUPPORT_SPLIT_ACTIVITY` / `allow_multiple_resumed_activities`）、`configChanges` 补 `density` | XML 注释 `[BYD]` | 上游若改 Manifest，保留这 4 处；上游新增的权限/组件照单全收 |
| `.github/workflows/release.yml` | 关闭上游自动出包：`on.push` 改为 `workflow_dispatch`，避免按 package.json 版本号抢先建同名 tag/Release 与 `byd-release.yml` 撞车 | YAML 注释 `[BYD]` | 冲突时保留 `workflow_dispatch` 触发方式 |
| `src/utils/version.js` | 更新源整体改为公共分发仓 `LuckyLearning/Release` 的 `LxMusic/latest.json`（多镜像），下载按清单 `assets` 选 ABI | 文件头 `[BYD]` 块 | 上游对本文件的改动**不保留**，以本实现为准 |
| `src/screens/Home/Views/Setting/settings/About.tsx` | 开源地址/反馈 Issue/最新版下载页指向本 fork 与公共分发仓（FAQ 与许可协议仍指上游） | `// [BYD]` 注释 | 保留四处链接改动 |
| `android/.../lyric/LyricView.java` 等 lyric 包 | v0.0.2：位置/高度百分比滑块（补齐 `setPosition`/`setHeight` 桥接）、加粗/描边/空心字、无翻译时下一行补位让行数设置生效、移除字体族设置 | `// [BYD]` 注释 | 保留全部 `[BYD]` 块；`StrokeTextView` 为新增文件零冲突 |
| `package.json` | `version`=`0.0.1`、`versionCode`=`1`（车机适配线，与上游 `1.9.x` 解耦） | — | 上游升版本时**保留**本 fork 的 0.0.x，上游 `1.9.x` 只记入基线说明 |
| `src/types/theme.d.ts` | `ActiveTheme` 交叉 `DesignTokens` 类型 | TS 注释 `[BYD]` | 保留 `& DesignTokens` |
| `src/utils/pixelRatio.ts` | 追加 `px()`/`sp()`（车机 1920×1080 设计稿换算） | `// [BYD]` 块 | 追加块整体保留，上游函数不动 |
| `src/store/theme/state.ts` | 初始 state 注入 `NOCTURNE_TOKENS` | `// [BYD]` | 保留 import 与 `...NOCTURNE_TOKENS` |
| `src/theme/themes/index.ts` | 注入 `buildDesignTokens` + 自动主题夜曲/白昼切换 + 兜底主题 | `// [BYD]` | 保留 3 处 `[BYD]` 块 |
| `src/theme/themes/createThemes.js` | 新增 nocturne/daylight 主题定义 | `// [BYD]` 块 | 保留两段主题定义 |
| `src/theme/themes/themes.ts` | 生成产物，新增 2 个主题（纯新增 508 行） | — | 重新 `npm run build:theme` 可再生成 |
| `src/config/defaultSetting.ts` | 默认主题改为 nocturne/daylight（保留 isAutoTheme/langId/新年皮肤） | `// [BYD]` | 保留主题三行 |
| `src/components/common/Image.tsx` | 新增可选 `fallback` prop（向后兼容） | `// [BYD]` | 保留 `fallback` 相关 4 处 |
| `src/resources/fonts/selection.json` + `android/.../icomoon.ttf` | 替换为超集字体（110 图标 = 原 52 + 车机 58），旧图标全保留 | — | 上游若更新图标字体，需重新合并两份 |
| `src/lang/{zh-cn,en-us,zh-tw}.json` | 各追加 49 个车机 i18n key | — | 保留追加的 key |
| `src/screens/Home/Horizontal/index.tsx` | 外壳换成 `CarShell` 车机导航，Header/Main/PlayerBar 与 Views 不变 | `// [BYD]` | 保留 CarShell 包裹 |
| `src/screens/PlayDetail/Horizontal/*` | 整体替换为车机版（Cover/Meta/TopBar/Transport/Secondary/ProgressRow/Lyric/index） | — | 上游若改横屏播放页，需重新移植 |
| `src/screens/PlayDetail/Horizontal/components/SettingPopup/*` | 车机版设置抽屉（隔离到横屏目录，不污染竖屏共用版） | — | 独立目录，冲突低 |
| `android/.../lyric/LyricView.java` | 多屏投射（`resolveWindowManagerForTarget`）、自定义背景/边框/圆角、字距/字体、`setTargetDisplay`/`setBackgroundStyle`/`setLetterSpacing` | `// [BYD]` | 保留新增字段与方法；上游若改窗口逻辑需手工合并 |
| `android/.../lyric/LyricTextView.java` | 文字描边（`setStroke`，alpha 钳 254 解耦），onDraw 先描边后填充 | `// [BYD]` | 保留 strokePaint 与 onDraw 改法 |
| `android/.../lyric/Lyric.java` | 启停前台保活服务；透传背景/字距/投屏屏设置 | `// [BYD]` | 保留 service 启停两处 |
| `android/.../lyric/LyricModule.java` | 新增桥接方法 `getDisplays`/`setBackgroundStyle`/`setLetterSpacing`/`setTargetDisplay` | `// [BYD]` | 保留 4 个 `@ReactMethod` |
| `android/app/src/main/AndroidManifest.xml` | 前台服务声明 + `FOREGROUND_SERVICE*` / `POST_NOTIFICATIONS` 权限 | `[BYD]` | 与分屏改动同文件，保留 |
| `src/config/defaultSetting.ts` | 新增悬浮窗样式与投屏屏默认值（7 项） | `// [BYD]` | 保留新增 7 行 |
| `src/types/app_setting.d.ts` | 对应 7 个类型声明 | `[BYD]` | 保留 |
| `src/core/desktopLyric.ts` | 透传新样式参数 + 导出 `setDesktopLyricBackground`/`LetterSpacing`/`Display`/`getDisplays` | `// [BYD]` | 保留 |
| `src/utils/nativeModules/lyricDesktop.ts` | 桥接新方法 + 扩展 `showDesktopLyricView` 入参 | `[BYD]` | 保留 |
| `src/screens/Home/Views/Setting/settings/LyricDesktop/index.tsx` | 注册 BgStyle/LetterSpacing/FontFamily/DisplaySelect | `[BYD]` | 保留 4 个新组件挂载 |

### 新增文件（无冲突）

| 文件 | 说明 |
| --- | --- |
| `android/.../lyric/LyricDisplays.java` | 多屏枚举辅助类 |
| `android/.../lyric/LyricOverlayService.java` | 桌面歌词前台保活服务 |
| `src/screens/Home/Views/Setting/settings/LyricDesktop/BgStyle.tsx` | 背景/边框样式（色块+圆角/边框宽滑块） |
| `src/screens/Home/Views/Setting/settings/LyricDesktop/LetterSpacing.tsx` | 字距滑块 |
| `src/screens/Home/Views/Setting/settings/LyricDesktop/FontFamily.tsx` | 字体选择 |
| `src/screens/Home/Views/Setting/settings/LyricDesktop/DisplaySelect.tsx` | 投屏屏幕选择 |

### 新增文件（无冲突）

| 文件 | 说明 |
| --- | --- |
| `src/components/car/`（22 文件） | 车机设计系统组件库 |
| `src/theme/tokens.ts` / `src/theme/fonts.ts` | 设计令牌 + 字体映射 |
| `src/resources/images/ambient/`（5 图） | 车机氛围背景 |
| `android/app/src/main/assets/fonts/Inter-*.ttf`（4 字重） | 打包数字/英文字体 |
| `android/app/src/main/res/values/integers.xml` | `byd_support_split_activity=0`（API<32 档） |
| `android/app/src/main/res/values-v32/integers.xml` | `byd_support_split_activity=1`（API≥32 档） |
| `.github/workflows/byd-release.yml` | BYD 独立出包工作流 |

## C. 签名与凭据（不入库）

| 项 | 值来源 | 说明 |
| --- | --- | --- |
| 签名证书 | WindTone `.toolchain/keystore/windtone.jks` | 指纹 `75EA...7398`，alias `windtone` |
| `.github-token` | 复制自 WindTone | 已 gitignore |
| GitHub Secrets | `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` | 与 WindTone 同名同值 |

## D. 版本与发布

| 项 | 值 |
| --- | --- |
| 当前版本 | `0.0.1`（`package.json` 的 `version` / `versionCode` = `1`） |
| 上游基线 | LX Music `v1.9.2`（`0669d1b`） |
| 出包方式 | 打 tag `v0.0.1` → `[BYD] Build & Release` 工作流 → GitHub Release 挂 5 个 APK |
| 发布形态 | 与 WindTone 一致：源码仓 Release 挂产物；**公共分发仓** `LuckyLearning/Release/LxMusic/`（`latest.json` + 版本目录，保留最近 5 版）供应用内更新与直链下载 |
| 更新检查 | 应用内读 `LxMusic/latest.json`（jsDelivr → gh-proxy → ghproxy.net → 官方 raw），下载按设备 ABI 选包 |
| 签名兜底 | 未配置 `KEYSTORE_BASE64` 时降级用仓库内 `android/app/debug.keystore`（`androiddebugkey` / `android`） |

版本号语义：`0.0.x` 为车机适配线，与上游 `1.9.x` 版本号解耦；后续合入上游更新时只更新基线说明，不动本版本号。

