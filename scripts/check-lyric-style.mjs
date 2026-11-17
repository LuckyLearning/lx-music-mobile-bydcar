import assert from 'node:assert/strict'
import migrateDesktopLyricStyle from '../src/config/migrateDesktopLyric.ts'
import { parseColorValue, readAlphaPercent, setAlphaPercent } from '../src/utils/colorValue.ts'

const old = {
  'desktopLyric.style.fontSize': 180,
  'desktopLyric.style.otherFontSize': 200,
  'desktopLyric.style.letterSpacing': 20,
  'desktopLyric.style.bold': true,
  'desktopLyric.style.hollowStyle': 2,
  'desktopLyric.style.hollowSize': 4,
  'desktopLyric.textPosition.x': 'center',
  'desktopLyric.title.align': 'right',
}

const next = migrateDesktopLyricStyle({ ...old })
assert.equal(next['desktopLyric.style.fontSize'], 18)
assert.equal(next['desktopLyric.style.otherFontSize'], 20)
assert.equal(next['desktopLyric.style.fontUnit'], 'pt')
assert.equal(next['desktopLyric.style.otherAlign'], 'center')
assert.equal(next['desktopLyric.style.otherLetterSpacing'], 20)
assert.equal(next['desktopLyric.style.otherBold'], true)
assert.equal(next['desktopLyric.style.otherHollowStyle'], 2)
assert.equal(next['desktopLyric.style.otherHollowSize'], 3)
assert.equal(next['desktopLyric.title.fontSize'], 19)
assert.equal(next['desktopLyric.title.letterSpacing'], 10)
assert.equal(next['desktopLyric.title.bold'], true)
assert.equal(next['desktopLyric.title.align'], 'right')
assert.equal(next['desktopLyric.title.hollowSize'], 3)

next['desktopLyric.style.fontSize'] = 40
const again = migrateDesktopLyricStyle(next)
assert.equal(again['desktopLyric.style.fontSize'], 40)
assert.equal(again['desktopLyric.title.fontSize'], 19)

assert.equal(parseColorValue('#ff000080')?.hex, '#ff000080')
assert.equal(parseColorValue('#fff')?.hex, '#ffffffff')
assert.equal(parseColorValue('#abc')?.hex, '#aabbccff')
assert.equal(parseColorValue('#ff'), null)
assert.equal(parseColorValue('rgba(255, 255, 255, 0.55)')?.hex, '#ffffff8c')
assert.equal(parseColorValue('nope'), null)
assert.equal(parseColorValue('#zzzzzz'), null)
assert.equal(setAlphaPercent('rgba(0, 0, 0, 0.35)', 50), '#00000080')
assert.equal(readAlphaPercent('#00000080'), 50)
assert.equal(setAlphaPercent('not-a-color', 10), null)

console.log('lyric style checks ok')
