/**
 * 设置里的颜色值。
 * 输入接受 CSS hex（#RGB / #RGBA / #RRGGBB / #RRGGBBAA）和 rgb/rgba。
 * 不接受半截或不合法的字符串，调用方应继续沿用上一次有效颜色。
 */

export interface ParsedColor {
  r: number
  g: number
  b: number
  /** 0–1 */
  a: number
  /** CSS #RRGGBBAA */
  hex: string
  rgba: string
}

const clampByte = (n: number) => Math.max(0, Math.min(255, Math.round(n)))

const clamp01 = (n: number) => Math.max(0, Math.min(1, n))

const hexByte = (n: number) => clampByte(n).toString(16).padStart(2, '0')

export const formatColor = (r: number, g: number, b: number, a: number): ParsedColor => {
  const R = clampByte(r)
  const G = clampByte(g)
  const B = clampByte(b)
  const A = clamp01(a)
  const hex = `#${hexByte(R)}${hexByte(G)}${hexByte(B)}${hexByte(Math.round(A * 255))}`
  const rgba = `rgba(${R}, ${G}, ${B}, ${Number(A.toFixed(3))})`
  return { r: R, g: G, b: B, a: A, hex, rgba }
}

const expandHexBody = (body: string): string | null => {
  if (!/^[\da-f]+$/i.test(body)) return null
  if (body.length == 3 || body.length == 4) return body.split('').map(ch => ch + ch).join('')
  if (body.length == 6 || body.length == 8) return body
  return null
}

export const parseColorValue = (input: unknown): ParsedColor | null => {
  if (typeof input != 'string') return null
  const text = input.trim()
  if (!text) return null
  try {
    if (text.startsWith('#')) {
      const body = expandHexBody(text.slice(1))
      if (!body) return null
      const r = parseInt(body.slice(0, 2), 16)
      const g = parseInt(body.slice(2, 4), 16)
      const b = parseInt(body.slice(4, 6), 16)
      const a = body.length == 8 ? parseInt(body.slice(6, 8), 16) / 255 : 1
      if ([r, g, b, a].some(n => Number.isNaN(n))) return null
      return formatColor(r, g, b, a)
    }
    const matched = /^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*(\d*\.?\d+)\s*)?\)$/i.exec(text)
    if (!matched) return null
    const r = Number(matched[1])
    const g = Number(matched[2])
    const b = Number(matched[3])
    if ([r, g, b].some(n => Number.isNaN(n) || n > 255)) return null
    const a = matched[4] == null ? 1 : Number(matched[4])
    if (Number.isNaN(a) || a < 0 || a > 1) return null
    return formatColor(r, g, b, a)
  } catch {
    return null
  }
}

/** 读出 0–100 的透明度。无法解析时按不透明处理，避免把滑块打到 0。 */
export const readAlphaPercent = (input: string): number => {
  const parsed = parseColorValue(input)
  if (!parsed) return 100
  return Math.round(parsed.a * 100)
}

/** 只替换透明度，保留 RGB。无法解析时返回 null，调用方不要写入。 */
export const setAlphaPercent = (input: string, percent: number): string | null => {
  const parsed = parseColorValue(input)
  if (!parsed) return null
  const alpha = Math.max(0, Math.min(100, percent)) / 100
  return formatColor(parsed.r, parsed.g, parsed.b, alpha).hex
}
