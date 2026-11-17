// ==================================================================
// [BYD] 应用内更新
//
// 优先读源码仓 GitHub Releases（tag、说明、APK 附件）。
// 打不开 api.github.com 时，再退到公共仓 LuckyLearning/Release 的 latest.json。
// 安装包同样先下 Release 附件，公共仓文件只做兜底。
//
// 上游原本读 lyswhut/lx-music-mobile 的 publish/version.json，车机 fork 不再使用。
// 合并上游时：上游对本文件的改动不需要保留，整文件以本实现为准。
// ==================================================================
import { httpGet } from '@/utils/request'
import { downloadFile, stopDownload, temporaryDirectoryPath } from '@/utils/fs'
import { getSupportedAbis, installApk } from '@/utils/nativeModules/utils'
import { APP_PROVIDER_NAME } from '@/config/constant'
import { assetDownloadUrls, githubReleasesApiUrls, manifestUrls } from '@/config/release'
import { manifestFromGithubReleases, manifestFromLatestJson } from '@/utils/releaseManifest'

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

/** 最近一次成功拉取的清单（含 assets），下载阶段直接复用，避免重复请求 */
let cachedManifest = null

const loadFirst = async(urls, parse, index = 0) => {
  if (index >= urls.length) throw new Error('all update sources failed')
  try {
    return parse(await request(urls[index]))
  } catch (err) {
    return loadFirst(urls, parse, index + 1)
  }
}

/**
 * 拉取最新版本信息。
 * 先读源码仓 GitHub Releases，失败再读公共仓 latest.json。
 * @returns {Promise<{version: string, desc: string, history: Array}>}
 */
export const getVersionInfo = async() => {
  let manifest
  try {
    manifest = await loadFirst(githubReleasesApiUrls(), manifestFromGithubReleases)
  } catch (err) {
    manifest = await loadFirst(manifestUrls(), manifestFromLatestJson)
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
  const urls = assetDownloadUrls(version, asset)
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
