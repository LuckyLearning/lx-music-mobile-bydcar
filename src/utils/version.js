// ==================================================================
// [BYD] 应用内更新：更新源已改为公共分发仓库 LuckyLearning/Release
// （与 WindTone 同一套链路，见 @/config/release 的注释）。
//
//   清单：  <线路>/LxMusic/latest.json
//   安装包：<线路>/LxMusic/v<版本>/<文件名>（按设备 ABI 选包，拿不准退 universal）
//
// 上游原本读 lyswhut/lx-music-mobile 的 publish/version.json + npm / gitee / stsky
// 多源并从上游 Releases 下载 —— 车机 fork 的版本号与发布渠道均已独立，不再使用那套源。
// 合并上游时：上游对本文件的改动不需要保留，整文件以本实现为准。
// ==================================================================
import { httpGet } from '@/utils/request'
import { downloadFile, stopDownload, temporaryDirectoryPath } from '@/utils/fs'
import { getSupportedAbis, installApk } from '@/utils/nativeModules/utils'
import { APP_PROVIDER_NAME } from '@/config/constant'
import { manifestUrls, releaseFileUrls, releasePath } from '@/config/release'

/** 与公共仓 checksums.txt / latest.json.assets 里的 ABI 命名一致 */
const ABI_ORDER = [
  'arm64-v8a',
  'armeabi-v7a',
  'x86_64',
  'x86',
  'universal',
]

const request = async(url, retryNum = 0) => {
  return new Promise((resolve, reject) => {
    httpGet(url, {
      timeout: 10000,
    }, (err, resp, body) => {
      if (err || resp.statusCode != 200) {
        ++retryNum >= 2
          ? reject(err || new Error(resp.statusMessage || resp.statusCode))
          : request(url, retryNum).then(resolve).catch(reject)
      } else resolve(body)
    })
  })
}

/** 清单里必需的字段校验 + 归一化（坏数据按失败处理，不甩给用户） */
const normalizeManifest = (info) => {
  if (info == null || typeof info !== 'object') throw new Error('manifest is not an object')
  if (info.version == null) throw new Error('manifest has no version')
  if (!Array.isArray(info.assets) || info.assets.length === 0) throw new Error('manifest has no assets')
  return {
    version: info.version,
    // 更新弹窗用 Text 直接渲染，只认纯文本；流水线已在 latest.json 里备好 `desc`
    desc: info.desc || String(info.notes || '').replace(/[*#`|]/g, ''),
    history: [],
    assets: info.assets,
  }
}

/** 最近一次成功拉取的清单（含 assets），下载阶段直接复用，避免重复请求 */
let cachedManifest = null

/**
 * 拉取最新版本信息。
 * 线路按可达性优先级顺序尝试（jsDelivr → 代理 → 官方 raw），失败自动换下一条。
 * @returns {Promise<{version: string, desc: string, history: Array}>}
 */
export const getVersionInfo = async(index = 0) => {
  const urls = manifestUrls()
  if (index >= urls.length) throw new Error('all update sources failed')
  let manifest
  try {
    manifest = normalizeManifest(await request(urls[index]))
  } catch (err) {
    return getVersionInfo(index + 1)
  }
  cachedManifest = manifest
  const { version, desc, history } = manifest
  return { version, desc, history }
}

/**
 * 从清单 assets 里挑本机适用的包：按 ABI_ORDER 优先匹配设备支持的 ABI，
 * 对应包缺失时退到 universal。
 */
const getTargetAsset = async(manifest) => {
  let supportedAbis = []
  try {
    supportedAbis = await getSupportedAbis()
  } catch {}
  for (const abi of ABI_ORDER) {
    if (abi === 'universal') break
    if (supportedAbis.includes(abi)) {
      const asset = manifest.assets.find(a => a.abi === abi)
      if (asset) return asset
    }
  }
  return manifest.assets.find(a => a.abi === 'universal') || manifest.assets[manifest.assets.length - 1]
}

let downloadJobId = null
const noop = (total, download) => {}
let apkSavePath

export const downloadNewVersion = async(version, onDownload = noop) => {
  let manifest = cachedManifest
  if (!manifest || manifest.version !== version) {
    await getVersionInfo()
    manifest = cachedManifest
    if (!manifest || manifest.version !== version) throw new Error('manifest version mismatch')
  }
  const asset = await getTargetAsset(manifest)
  const urls = releaseFileUrls(releasePath('v' + version, asset.file))
  const savePath = temporaryDirectoryPath + '/lx-music-mobile.apk'
  let lastErr

  for (const url of urls) {
    try {
      if (downloadJobId) stopDownload(downloadJobId)

      const { jobId, promise } = downloadFile(url, savePath, {
        progressInterval: 500,
        connectionTimeout: 20000,
        readTimeout: 30000,
        begin({ contentLength }) {
          onDownload(contentLength || asset.size, 0)
        },
        progress({ contentLength, bytesWritten }) {
          onDownload(contentLength || asset.size, bytesWritten)
        },
      })
      downloadJobId = jobId
      await promise

      apkSavePath = savePath
      return updateApp()
    } catch (err) {
      lastErr = err
    }
  }
  throw lastErr ?? new Error('download failed')
}

export const updateApp = async() => {
  if (!apkSavePath) throw new Error('apk Save Path is null')
  await installApk(apkSavePath, APP_PROVIDER_NAME)
}
