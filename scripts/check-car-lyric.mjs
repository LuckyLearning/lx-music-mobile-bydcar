import assert from 'node:assert/strict'
import { buildCarLyricFields, formatCarLyricLine } from '../src/plugins/player/carLyricWindow.ts'

const line = (text, ...extendedLyrics) => ({ text, extendedLyrics })

assert.equal(formatCarLyricLine(undefined), '')
assert.equal(formatCarLyricLine(line('  ')), '')
assert.equal(formatCarLyricLine(line('一句', ' 译文 ', '')), '一句 / 译文')

const lines = [line('前奏后'), line('当前', 'translation'), line('下一句'), line('再下一句')]
const intro = buildCarLyricFields(lines, -1, '[00:01.00]前奏后')
assert.equal(intro.lrc_line_1, '前奏后')
assert.equal(intro.lrc_line_2, '当前 / translation')
assert.equal(intro.lrc_line_3, '下一句')
assert.equal(intro.lyric, '[00:01.00]前奏后')
assert.equal(intro.lyrics, intro.lyric)

const mid = buildCarLyricFields(lines, 1, '[00:12.00]当前')
assert.equal(mid.lrc_line_1, '当前 / translation')
assert.equal(mid.lrc_line_2, '下一句')
assert.equal(mid.lrc_line_3, '再下一句')

const tail = buildCarLyricFields(lines, 3, '')
assert.equal(tail.lrc_line_1, '再下一句')
assert.equal(tail.lrc_line_2, '')
assert.equal(tail.lrc_line_3, '')

console.log('car lyric window checks ok')
