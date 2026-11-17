// [BYD] 把 GitHub Releases API / 公共仓 latest.json 收成检查更新用的同一份结构。
// 不依赖 RN，方便直接用 node 对真实接口返回值做校验。

const ABI_ORDER = ['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86', 'universal']

export const abiOf = (name) => {
  const file = String(name || '')
  for (const abi of ABI_ORDER) {
    if (abi === 'universal') continue
    if (file.endsWith(`-${abi}.apk`)) return abi
  }
  return 'universal'
}

/** markdown → 纯文本。更新弹窗是 Text，不渲染 ** / 表格。 */
export const toPlain = (md) => {
  const out = []
  for (const raw of String(md || '').split(/\r?\n/)) {
    const stripped = raw.trim()
    if (!stripped) continue
    if (stripped.startsWith('|')) continue
    if (stripped && [...stripped].every(ch => '-|: '.includes(ch))) continue
    let line = stripped.replace(/^#{1,6}\s*/, '')
    line = line.replace(/\*\*(.+?)\*\*/g, '$1')
    line = line.replace(/`(.+?)`/g, '$1')
    line = line.replace(/^[-*]\s+/, '· ')
    out.push(line.trim())
  }
  return out.join('\n')
}

const versionParts = (version) => String(version || '').replace(/^v/i, '').split('.').map(n => parseInt(n, 10) || 0)

export const compareVersion = (a, b) => {
  const aa = versionParts(a)
  const bb = versionParts(b)
  const n = Math.max(aa.length, bb.length)
  for (let i = 0; i < n; i++) {
    const d = (aa[i] || 0) - (bb[i] || 0)
    if (d) return d
  }
  return 0
}

export const stripTag = (tag) => String(tag || '').trim().replace(/^v/i, '')

const parseAsset = (asset) => {
  if (!asset || typeof asset !== 'object') return null
  const file = asset.name || asset.file
  if (!file || !String(file).endsWith('.apk')) return null
  if (asset.state && asset.state !== 'uploaded') return null
  const digest = typeof asset.digest === 'string' ? asset.digest.replace(/^sha256:/i, '') : (asset.sha256 || '')
  return {
    abi: asset.abi || abiOf(file),
    file: String(file),
    size: Number(asset.size) || 0,
    sha256: digest,
    browser_download_url: asset.browser_download_url || '',
  }
}

const sortAssets = (assets) => {
  return assets.slice().sort((a, b) => {
    const ia = ABI_ORDER.indexOf(a.abi)
    const ib = ABI_ORDER.indexOf(b.abi)
    return (ia === -1 ? 9 : ia) - (ib === -1 ? 9 : ib)
  })
}

const fromRelease = (release) => {
  const version = stripTag(release.tag_name || release.tag || release.version)
  if (!version) throw new Error('release has no version')
  const assets = sortAssets((release.assets || []).map(parseAsset).filter(Boolean))
  if (!assets.length) throw new Error('release has no apk assets')
  return {
    version,
    desc: release.desc || toPlain(release.body || release.notes || ''),
    history: [],
    assets,
  }
}

/**
 * GitHub `GET /releases` 的数组，或单个 release 对象。
 * 跳过 draft / prerelease，按版本号取最新一份，其余放进 history。
 */
export const manifestFromGithubReleases = (payload) => {
  const list = Array.isArray(payload)
    ? payload
    : (payload && typeof payload === 'object' && (payload.tag_name || payload.assets) ? [payload] : null)
  if (!list) throw new Error('not a github release payload')
  const stable = list.filter(item => item && !item.draft && !item.prerelease && (item.tag_name || item.version))
  const parsed = []
  for (const item of stable) {
    try {
      parsed.push({ manifest: fromRelease(item), raw: item })
    } catch {
      // 没有 apk 的 release 跳过，继续找下一份
    }
  }
  if (!parsed.length) throw new Error('no published release with apk assets')
  parsed.sort((a, b) => compareVersion(b.manifest.version, a.manifest.version))
  const latest = parsed[0].manifest
  latest.history = parsed.slice(1).map(({ manifest }) => ({
    version: manifest.version,
    desc: manifest.desc,
  }))
  return latest
}

/** 公共仓 latest.json。字段不全就抛错，让调用方换下一条线路。 */
export const manifestFromLatestJson = (info) => {
  if (info == null || typeof info !== 'object' || Array.isArray(info)) throw new Error('manifest is not an object')
  const version = stripTag(info.version)
  if (!version) throw new Error('manifest has no version')
  const assets = sortAssets((info.assets || []).map(parseAsset).filter(Boolean))
  if (!assets.length) throw new Error('manifest has no assets')
  return {
    version,
    desc: info.desc || toPlain(info.notes || ''),
    history: Array.isArray(info.history) ? info.history : [],
    assets,
  }
}
