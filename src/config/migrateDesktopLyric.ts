/**
 * 悬浮窗歌词样式迁移。
 *
 * 必须在和默认设置合并之前跑在「已保存的设置」上：
 * 字号从旧滑块（数值 / 10 = sp）换成磅，并用 fontUnit 保证只换一次。
 * 标题行 / 其它行以前跟当前行共用字距、加粗、空心、对齐，这里把当时真正画出来的值
 * 抄进各自的新字段，避免升级后样子突然变掉。
 */

type SettingBag = Record<string, any>

const isAlign = (value: unknown): value is 'left' | 'center' | 'right' => {
  return value == 'left' || value == 'center' || value == 'right'
}

const num = (value: unknown, fallback: number) => {
  return typeof value == 'number' && Number.isFinite(value) ? value : fallback
}

const hollowStyle = (value: unknown): 0 | 1 | 2 => {
  return value == 1 || value == 2 ? value : 0
}

/** 旧滑块最小 100。已经是磅的小数不会进到这里（有 fontUnit 门闩）。 */
const legacyFontToPt = (value: number) => {
  if (value >= 50) return Math.max(1, Math.round(value / 10))
  return Math.max(1, Math.round(value))
}

const effectiveHollowSize = (value: number) => {
  const scaled = Math.round(value * 0.75 * 10) / 10
  return scaled > 0 ? scaled : value
}

const migrateDesktopLyricStyle = <T extends SettingBag | null | undefined>(setting: T): T => {
  if (!setting || typeof setting != 'object') return setting

  if (setting['desktopLyric.style.fontUnit'] !== 'pt') {
    if (typeof setting['desktopLyric.style.fontSize'] == 'number') {
      setting['desktopLyric.style.fontSize'] = legacyFontToPt(setting['desktopLyric.style.fontSize'])
    }
    if (typeof setting['desktopLyric.style.otherFontSize'] == 'number') {
      setting['desktopLyric.style.otherFontSize'] = legacyFontToPt(setting['desktopLyric.style.otherFontSize'])
    }
    setting['desktopLyric.style.fontUnit'] = 'pt'
  }

  const letter = num(setting['desktopLyric.style.letterSpacing'], 0)
  const bold = setting['desktopLyric.style.bold'] === true
  const hollow = hollowStyle(setting['desktopLyric.style.hollowStyle'])
  const hollowSize = num(setting['desktopLyric.style.hollowSize'], 4)
  const align = isAlign(setting['desktopLyric.textPosition.x']) ? setting['desktopLyric.textPosition.x'] : 'left'
  const otherPt = num(setting['desktopLyric.style.otherFontSize'], 14)

  if (setting['desktopLyric.style.otherAlign'] == null) setting['desktopLyric.style.otherAlign'] = align
  if (setting['desktopLyric.style.otherLetterSpacing'] == null) setting['desktopLyric.style.otherLetterSpacing'] = letter
  if (setting['desktopLyric.style.otherBold'] == null) setting['desktopLyric.style.otherBold'] = bold
  if (setting['desktopLyric.style.otherHollowStyle'] == null) setting['desktopLyric.style.otherHollowStyle'] = hollow
  if (setting['desktopLyric.style.otherHollowSize'] == null) setting['desktopLyric.style.otherHollowSize'] = effectiveHollowSize(hollowSize)

  // 旧版标题：字号 max(10sp, 其它行 * 0.95)，字距是歌词的一半，并且始终加粗，空心按非当前行的 0.75。
  if (setting['desktopLyric.title.fontSize'] == null) {
    setting['desktopLyric.title.fontSize'] = Math.max(1, Math.round(Math.max(10, otherPt * 0.95)))
  }
  if (setting['desktopLyric.title.letterSpacing'] == null) setting['desktopLyric.title.letterSpacing'] = letter / 2
  if (setting['desktopLyric.title.bold'] == null) setting['desktopLyric.title.bold'] = true
  if (setting['desktopLyric.title.hollowStyle'] == null) setting['desktopLyric.title.hollowStyle'] = hollow
  if (setting['desktopLyric.title.hollowSize'] == null) setting['desktopLyric.title.hollowSize'] = effectiveHollowSize(hollowSize)

  return setting
}

export default migrateDesktopLyricStyle
