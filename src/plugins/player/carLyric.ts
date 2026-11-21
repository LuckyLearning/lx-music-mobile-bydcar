import { getLyricLines, getLyricText } from '@/plugins/lyric'
import { buildCarLyricFields } from '@/plugins/player/carLyricWindow'
import { updateNowPlayingTitles } from '@/plugins/player/utils'

let lastKey = ''
let seq = 0

/**
 * 把当前歌词写进正在播放的媒体会话。
 *
 * 不改歌名。蓝牙歌词那个开关仍然只负责把当前句塞进标题；车机要的是 extras 里的三行。
 */
export const publishCarLyricLine = (index: number) => {
  const fields = buildCarLyricFields(getLyricLines(), index, getLyricText())
  const key = [fields.lyric, fields.lrc_line_1, fields.lrc_line_2, fields.lrc_line_3].join('\u0000')
  if (key === lastKey) return
  const id = ++seq
  lastKey = key
  void updateNowPlayingTitles(fields).catch(() => {
    if (id === seq) lastKey = ''
  })
}
