/**
 * 播放页歌词字号迁移：从旧滑块刻度（100..300，数值 / 10）迁为磅，并由 fontUnit 保证只换算一次。
 */

type SettingBag = Record<string, any>

export const legacyPlayDetailFontToPt = (value: unknown): number | undefined => {
  if (typeof value != 'number' || !Number.isFinite(value) || value <= 0) return undefined
  if (value >= 50) return Math.max(1, Math.round(value / 10))
  return Math.max(1, Math.round(value))
}

const KEYS = [
  'playDetail.vertical.style.lrcFontSize',
  'playDetail.horizontal.style.lrcFontSize',
] as const

const migratePlayDetailStyle = <T extends SettingBag | null | undefined>(setting: T): T => {
  if (!setting || typeof setting != 'object') return setting

  if (setting['playDetail.style.fontUnit'] !== 'pt') {
    for (const key of KEYS) {
      if (!(key in setting) || setting[key] === undefined) continue
      const converted = legacyPlayDetailFontToPt(setting[key])
      if (converted !== undefined) setting[key] = converted
      else delete setting[key]
    }
    setting['playDetail.style.fontUnit'] = 'pt'
  }

  return setting
}

export default migratePlayDetailStyle
