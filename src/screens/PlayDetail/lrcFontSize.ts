export const MIN_LRC_FONT_PT = 1
export const DEFAULT_VERTICAL_LRC_FONT_PT = 21
export const DEFAULT_HORIZONTAL_LRC_FONT_PT = 22

export const getDefaultLrcFontPt = (direction: 'horizontal' | 'vertical') => {
  return direction == 'horizontal' ? DEFAULT_HORIZONTAL_LRC_FONT_PT : DEFAULT_VERTICAL_LRC_FONT_PT
}

export const normalizeLrcFontPt = (value: unknown, fallback = MIN_LRC_FONT_PT): number => {
  if (typeof value != 'number' || !Number.isFinite(value)) return fallback
  return Math.max(MIN_LRC_FONT_PT, Math.round(value))
}

export const stepLrcFontPt = (value: unknown, delta: number, fallback = MIN_LRC_FONT_PT): number => {
  return normalizeLrcFontPt(normalizeLrcFontPt(value, fallback) + delta, fallback)
}

/** 保持车机/横屏歌词三档字号比例（36 / 22 / 20 对应默认 22 磅），不设上限。 */
export const calcHorizontalLyricSize = (lrcFontPt: unknown, distance: number): number => {
  const pt = normalizeLrcFontPt(lrcFontPt, DEFAULT_HORIZONTAL_LRC_FONT_PT)
  const base = distance == 0 ? 36 : distance == 1 ? 22 : 20
  return base * pt / DEFAULT_HORIZONTAL_LRC_FONT_PT
}

export const calcHorizontalPreviewSize = (lrcFontPt: unknown): number => {
  const pt = normalizeLrcFontPt(lrcFontPt, DEFAULT_HORIZONTAL_LRC_FONT_PT)
  return 42 * pt / DEFAULT_HORIZONTAL_LRC_FONT_PT
}

export const calcVerticalLyricSize = (lrcFontPt: unknown): number => {
  return normalizeLrcFontPt(lrcFontPt, DEFAULT_VERTICAL_LRC_FONT_PT)
}
