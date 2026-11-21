/**
 * 车机滚动歌词窗口。
 *
 * 迪链（以及 AnyLyric 的 L0b）把「当前句」放在 PlaybackState.extras 的
 * `lrc_line_1`，后面两句是 `lrc_line_2` / `lrc_line_3`；整段带时间轴的 LRC 放在 `lyrics`。
 * 下标小于 0（前奏、刚设词）时从第一句开始，这样前奏期间车机也能看到词。
 */
export interface LyricWindowLine {
  text: string
  extendedLyrics?: readonly string[]
}

export interface CarLyricFields {
  lyric: string
  lyrics: string
  lrc_line_1: string
  lrc_line_2: string
  lrc_line_3: string
}

export const formatCarLyricLine = (line: LyricWindowLine | undefined): string => {
  if (!line) return ''
  const parts: string[] = []
  const text = line.text?.trim()
  if (text) parts.push(text)
  for (const extra of line.extendedLyrics ?? []) {
    const value = extra?.trim()
    if (value) parts.push(value)
  }
  return parts.join(' / ')
}

export const buildCarLyricFields = (
  lines: readonly LyricWindowLine[],
  index: number,
  fullLyric: string,
): CarLyricFields => {
  const start = index < 0 ? 0 : index
  return {
    lyric: fullLyric,
    lyrics: fullLyric,
    lrc_line_1: formatCarLyricLine(lines[start]),
    lrc_line_2: formatCarLyricLine(lines[start + 1]),
    lrc_line_3: formatCarLyricLine(lines[start + 2]),
  }
}
