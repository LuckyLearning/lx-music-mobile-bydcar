// ==================================================================
// [BYD] 应用内更新来源
//
// 优先：源码仓 GitHub Releases
//   https://api.github.com/repos/LuckyLearning/lx-music-mobile-bydcar/releases
//   版本号、更新说明、APK 都从 Release 附件读。
// 兜底：公共分发仓 latest.json（jsDelivr → 代理 → raw），目录约定与 WindTone 一致：
//
//   LuckyLearning/Release/
//     LxMusic/
//       latest.json          ← 清单（版本号、更新说明、各 ABI 包大小与 SHA-256）
//       v0.0.1/              ← 每个版本一个目录，只保留最近 5 个
//         lx-music-mobile-v0.0.1-arm64-v8a.apk …
//         notes.md / checksums.txt
//     WindTone/ AnyLyric/ GlucoNote/   ← 其它软件，互不干扰
//
// latest.json 由 .github/workflows/byd-release.yml 打 tag 时经
// scripts/publish-release-repo.sh 自动写入。
// ==================================================================

export const RELEASE_OWNER = 'LuckyLearning'
export const RELEASE_REPO = 'Release'
export const RELEASE_BRANCH = 'main'
/** 源码仓：应用内更新优先读这里的 GitHub Releases（tag / 说明 / APK） */
export const SOURCE_OWNER = 'LuckyLearning'
export const SOURCE_REPO = 'lx-music-mobile-bydcar'
/** 本软件在公共仓里的目录名 */
export const RELEASE_APP_DIR = 'LxMusic'

export const RELEASE_PAGE_URL = `https://github.com/${RELEASE_OWNER}/${RELEASE_REPO}`
/** 公共仓里本软件的目录页（检查失败时可引导用户手动下载） */
export const RELEASE_APP_PAGE_URL = `${RELEASE_PAGE_URL}/tree/main/${RELEASE_APP_DIR}`

const RAW_BASE = `https://raw.githubusercontent.com/${RELEASE_OWNER}/${RELEASE_REPO}/${RELEASE_BRANCH}`
// jsDelivr 多节点国内可达性最好，代理线路次之，官方 raw 放最后兜底
const JSDELIVR_HOSTS = ['https://fastly.jsdelivr.net', 'https://gcore.jsdelivr.net', 'https://cdn.jsdelivr.net']
const MIRROR_PREFIXES = ['https://gh-proxy.com/', 'https://ghproxy.net/']

/** 资源在公共仓内的相对路径，如 `LxMusic/latest.json` */
export const releasePath = (...parts: string[]): string => [RELEASE_APP_DIR, ...parts].join('/')

const githubUrls = (path: string): string[] => [
  ...JSDELIVR_HOSTS.map((host) => `${host}/gh/${RELEASE_OWNER}/${RELEASE_REPO}@${RELEASE_BRANCH}/${path}`),
  ...MIRROR_PREFIXES.map((prefix) => `${prefix}${RAW_BASE}/${path}`),
  `${RAW_BASE}/${path}`,
]

/** 检查更新（latest.json）的候选线路，按可达性优先级排列。GitHub Releases 失败时才用。 */
export const manifestUrls = (): string[] => githubUrls(releasePath('latest.json'))

/** 源码仓 Releases 列表。官方 API 优先，代理兜底（国内经常打不开 api.github.com）。 */
export const githubReleasesApiUrls = (): string[] => {
  const api = `https://api.github.com/repos/${SOURCE_OWNER}/${SOURCE_REPO}/releases?per_page=10`
  return [api, ...MIRROR_PREFIXES.map((prefix) => `${prefix}${api}`)]
}

export const SOURCE_RELEASES_PAGE = `https://github.com/${SOURCE_OWNER}/${SOURCE_REPO}/releases`

/**
 * 安装包下载线路：先下 GitHub Release 附件（browser_download_url），
 * 再走代理，最后才是公共仓里的同一份文件。
 */
export const assetDownloadUrls = (version: string, asset: { file: string, browser_download_url?: string }): string[] => {
  const direct = asset.browser_download_url
    || `https://github.com/${SOURCE_OWNER}/${SOURCE_REPO}/releases/download/v${version}/${asset.file}`
  const urls = [direct, ...MIRROR_PREFIXES.map((prefix) => `${prefix}${direct}`)]
  urls.push(...releaseFileUrls(releasePath(`v${version}`, asset.file)))
  return [...new Set(urls)]
}

/** 指定公共仓文件的下载线路（与清单线路同一优先级） */
export const releaseFileUrls = (path: string): string[] => githubUrls(path)
