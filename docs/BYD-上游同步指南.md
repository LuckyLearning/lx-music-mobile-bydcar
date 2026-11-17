# [BYD] 上游同步指南（lx-music-mobile → 本 fork）

> 本仓库是 `lyswhut/lx-music-mobile` 的 GitHub fork（GitHub 已识别 parent）。
> 目标：**上游持续更新时，能干净地把更新合并过来，不被我们的车机定制阻塞。**

## 一、仓库关系

| remote | 地址 | 用途 |
| --- | --- | --- |
| `origin` | `LuckyLearning/lx-music-mobile-bydcar.git` | 本 fork，日常开发与发版 |
| `upstream` | `lyswhut/lx-music-mobile.git` | 上游，只读同步源 |

当前基线：`master` 与 `upstream/master` 在 `0669d1b`（v1.9.2）完全一致。

## 二、改动隔离原则（必须遵守）

为了让上游更新能自动合并，定制改动遵循三条铁律：

1. **优先新增文件**：新功能一律放新文件，绝不塞进上游文件。新增文件与上游零冲突。
2. **必改上游文件时最小化 + 标记**：确需修改上游文件时，改动范围压到最小，并用 `// [BYD] ... // [/BYD]`（JS/TS）或 `<!-- [BYD] ... -->`（XML）包裹，方便 merge 时识别。下游改动清单见 `docs/BYD-修改清单.md`。
3. **配置类改动下沉到构建参数**：签名、版本号等通过 `keystore.properties` / Gradle `-P` 参数 / GitHub Secrets 注入，不写入被上游跟踪的构建脚本。

## 三、同步操作（每次上游发版后）

```bash
# 1. 拉取上游
git fetch upstream master

# 2. 切到 master 并合并（master 是定制+发版分支）
git checkout master
git merge upstream/master

# 3. 处理冲突：只会出现在「修改清单」登记的文件里，按 [BYD] 标记保留定制
#    新增的 BYD 文件（components/car、byd-release.yml 等）不会冲突

# 4. 验证
npm ci && npx tsc --noEmit && npm run lint
cd android && ./gradlew assembleDebug --stacktrace

# 5. 推送
git push origin master
```

## 四、冲突高发点（重点核对）

| 文件 | 冲突原因 | 处理 |
| --- | --- | --- |
| `package.json` | 上游升版本/依赖 | 手动合并：保留上游依赖更新，保留 BYD 新增脚本/字段 |
| `src/config/defaultSetting.ts` | 上游改默认设置 | 按 `[BYD]` 标记保留车机默认值 |
| `src/screens/*/index.tsx` | 上游改页面入口 | 见修改清单，保留横屏分支接入 |
| `android/app/src/main/AndroidManifest.xml` | 上游加权限/组件 | 保留 BYD 分屏 meta-data 与悬浮窗声明 |

## 五、禁止事项

- 不要在 `master` 上 `git rebase upstream/master`（会改写已发版的提交历史）
- 不要直接覆盖上游的 `release.yml`（BYD 出包走独立的 `byd-release.yml`）
- 不要把 `keystore.properties`、`.github-token`、`*.jks` 提交入库（已在 `.gitignore`）
