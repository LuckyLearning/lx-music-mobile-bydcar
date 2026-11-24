// Runs the original musicSdk functions on a fixture or a captured response.
// The host stubs are the same algorithms as CryptoModule.sha1 and AES.java mode "AES".
import { createCipheriv, createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { createContext, runInNewContext } from 'node:vm'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const repo = resolve(process.argv[2] || resolve(here, '../..'))
const mode = process.argv[3] || 'vectors'
const extra = process.argv[4]

function md5(text) {
  return createHash('md5').update(text, 'utf8').digest('hex')
}
function sha1(text) {
  return createHash('sha1').update(text, 'utf8').digest('hex')
}
function decodeName(value) {
  if (!value) return ''
  return String(value).replace(/&(#x[0-9a-fA-F]+|#\d+|amp|lt|gt|quot|apos|nbsp);/g, (token, body) => {
    if (body === 'amp') return '&'
    if (body === 'lt') return '<'
    if (body === 'gt') return '>'
    if (body === 'quot') return '"'
    if (body === 'apos') return "'"
    if (body === 'nbsp') return '\u00A0'
    if (body.startsWith('#x') || body.startsWith('#X')) return String.fromCodePoint(parseInt(body.slice(2), 16))
    if (body.startsWith('#')) return String.fromCodePoint(parseInt(body.slice(1), 10))
    return token
  })
}
function formatPlayTime(time) {
  const m = Math.trunc(time / 60)
  const s = Math.trunc(time % 60)
  const fix = (n) => (n < 10 ? `0${n}` : String(n))
  return m === 0 && s === 0 ? '--/--' : `${fix(m)}:${fix(s)}`
}
function sizeFormate(size) {
  if (!size) return '0 B'
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB']
  const number = Math.floor(Math.log(size) / Math.log(1024))
  return `${(size / Math.pow(1024, Math.floor(number))).toFixed(2)} ${units[number]}`
}
function formatSingerName(singers, nameKey = 'name', join = '、') {
  if (Array.isArray(singers)) {
    const singer = []
    singers.forEach((item) => {
      const name = item[nameKey]
      if (!name) return
      singer.push(name)
    })
    return decodeName(singer.join(join))
  }
  return decodeName(String(singers ?? ''))
}
function formatSinger(raw) {
  return String(raw || '').replace(/&/g, '、')
}

function loadModule(relative) {
  const source = readFileSync(resolve(repo, relative), 'utf8')
    .replace(/^import .*$/gm, '')
    .replace(/export const (\w+)\s*=/g, 'const $1 = exports.$1 =')
    .replace(/export async function /g, 'async function ')
    .replace(/export function /g, 'function ')
    .replace(/export default/, 'exports.default =')
  const sandbox = {
    module: { exports: {} },
    exports: {},
    Buffer,
    Math, String, Number, Array, Object, Set, JSON, Date, parseInt, isNaN, console, Promise, RegExp, Error,
    decodeName, formatPlayTime, sizeFormate, formatSingerName, formatSinger,
    httpFetch() { throw new Error('oracle does not open sockets') },
    toMD5: md5,
    md5,
  }
  sandbox.exports = sandbox.module.exports
  runInNewContext(source, createContext(sandbox), { filename: relative })
  return sandbox.exports
}

function aesEcb(plain) {
  const cipher = createCipheriv('aes-128-ecb', Buffer.from('e82ckenh8dichen8', 'utf8'), null)
  return Buffer.concat([cipher.update(plain, 'utf8'), cipher.final()]).toString('hex').toUpperCase()
}

function originalEapi(url, text) {
  const crypto = readFileSync(resolve(repo, 'src/utils/musicSdk/wy/utils/crypto.js'), 'utf8')
  const source = crypto.slice(crypto.indexOf('export const eapi ='), crypto.indexOf('export const eapiDecrypt'))
    .replace('export const eapi =', 'const eapi =')
  const aesEncrypt = (b64) => {
    const plain = Buffer.from(b64, 'base64')
    const cipher = createCipheriv('aes-128-ecb', Buffer.from('e82ckenh8dichen8', 'utf8'), null)
    return Buffer.concat([cipher.update(plain), cipher.final()]).toString('base64')
  }
  const sandbox = {
    Buffer, JSON,
    toMD5: md5,
    eapiKey: Buffer.from('e82ckenh8dichen8', 'utf8').toString('base64'),
    AES_MODE: { ECB_128_NoPadding: 'AES' },
    aesEncrypt,
  }
  runInNewContext(source + '\nthis.eapi = eapi', createContext(sandbox), { filename: 'eapi.js' })
  return sandbox.eapi(url, text).params
}

function summary(list) {
  return (list || []).slice(0, 5).map((item) => ({
    name: item.name,
    singer: item.singer,
    songmid: String(item.songmid ?? ''),
    albumName: item.albumName || '',
    interval: item.interval || '',
    qualities: (item.types || []).map((type) => type.type),
    hashes: Object.fromEntries((item.types || []).filter((type) => type.hash).map((type) => [type.type, String(type.hash).toLowerCase()])),
  }))
}

function vectors(txDigest) {
  const kg = loadModule('src/utils/musicSdk/kg/util.js')
  const params = 'appid=1058&specialid=0&global_specialid=collection_3_1&format=jsonp'
  const txJson = '{"comm":{"ct":"19","cv":"2151"},"req":{"module":"music.search.SearchCgiService","method":"DoSearchForQQMusicDesktop","param":{"query":"hello","page_num":1,"num_per_page":2,"searchid":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA00000"}}}'
  const mg = loadModule('src/utils/musicSdk/mg/musicSearch.js')
  const kgSearch = loadModule('src/utils/musicSdk/kg/musicSearch.js')
  const txSearch = loadModule('src/utils/musicSdk/tx/musicSearch.js')
  const wySearch = loadModule('src/utils/musicSdk/wy/musicSearch.js')
  const kwSearch = loadModule('src/utils/musicSdk/kw/musicSearch.js')
  const fixture = JSON.parse(readFileSync(resolve(here, 'parity-search.json'), 'utf8'))
  return {
    kgSign: kg.signatureParams(params, 'web', ''),
    txSign: txDigest,
    txJson,
    mgSign: mg.createSignature('1700000000000', 'hello').sign,
    wyParams: originalEapi('/api/search/song/list/page', '{"keyword":"hello","limit":2}'),
    kg: summary(kgSearch.default.handleResult(fixture.kg.data.lists)),
    tx: summary(txSearch.default.handleResult(fixture.tx['music.search.SearchCgiService'].data.body.song.list)),
    wy: summary(wySearch.default.handleResult(fixture.wy.data.resources)),
    mg: summary(mg.default.filterData(fixture.mg.songResultData.resultList)),
    kw: summary(kwSearch.default.handleResult(fixture.kw.abslist)),
  }
}

async function txSign() {
  const source = readFileSync(resolve(repo, 'src/utils/musicSdk/tx/utils/crypto.js'), 'utf8')
    .replace(/^import .*$/gm, '')
    .replace('export async function zzcSign', 'async function zzcSign')
    .replace('hashSHA1', 'sha1')
  const sandbox = { sha1: async (text) => sha1(text), Buffer }
  runInNewContext(source + '\nthis.zzcSign = zzcSign', createContext(sandbox), { filename: 'zzc.js' })
  const txJson = '{"comm":{"ct":"19","cv":"2151"},"req":{"module":"music.search.SearchCgiService","method":"DoSearchForQQMusicDesktop","param":{"query":"hello","page_num":1,"num_per_page":2,"searchid":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA00000"}}}'
  return sandbox.zzcSign(txJson)
}

function parseCaptured() {
  const body = JSON.parse(readFileSync(extra, 'utf8'))
  if (mode === 'parse-kg') {
    const search = loadModule('src/utils/musicSdk/kg/musicSearch.js')
    const lists = body?.data?.lists
    const list = body?.error_code === 0 && Array.isArray(lists) ? search.default.handleResult(lists) : []
    process.stdout.write(JSON.stringify({ count: list.length, first: summary(list)[0] || null, total: body?.data?.total ?? null }))
    return
  }
  if (mode === 'parse-tx') {
    const search = loadModule('src/utils/musicSdk/tx/musicSearch.js')
    const req = body?.['music.search.SearchCgiService'] ?? body?.req
    const data = req?.data
    const rows = data?.body?.song?.list
    const list = body?.code === 0 && req?.code === 0 && Array.isArray(rows) ? search.default.handleResult(rows) : []
    process.stdout.write(JSON.stringify({ count: list.length, first: summary(list)[0] || null, total: data?.meta?.sum ?? null }))
    return
  }
  if (mode === 'parse-mg') {
    const search = loadModule('src/utils/musicSdk/mg/musicSearch.js')
    const rows = body?.songResultData?.resultList
    const list = body?.code === '000000' && Array.isArray(rows) ? search.default.filterData(rows) : []
    process.stdout.write(JSON.stringify({ count: list.length, first: summary(list)[0] || null, total: body?.songResultData?.totalCount ?? null }))
    return
  }
  if (mode === 'parse-wy') {
    const search = loadModule('src/utils/musicSdk/wy/musicSearch.js')
    const rows = body?.data?.resources
    const list = body?.code === 200 && Array.isArray(rows) ? search.default.handleResult(rows) : []
    process.stdout.write(JSON.stringify({ count: list.length, first: summary(list)[0] || null, total: body?.data?.totalCount ?? null }))
    return
  }
  if (mode === 'parse-kw') {
    const search = loadModule('src/utils/musicSdk/kw/musicSearch.js')
    const rows = body?.abslist
    const hidden = body && body.TOTAL !== '0' && body.SHOW === '0'
    if (hidden) {
      process.stdout.write(JSON.stringify({ count: null, first: null, total: body?.TOTAL ?? null, retry: true }))
      return
    }
    const list = Array.isArray(rows) ? search.default.handleResult(rows) : []
    process.stdout.write(JSON.stringify({ count: list == null ? null : list.length, first: summary(list || [])[0] || null, total: body?.TOTAL ?? null }))
  }
}

if (mode === 'vectors') {
  process.stdout.write(JSON.stringify(vectors(await txSign())))
} else {
  parseCaptured()
}
