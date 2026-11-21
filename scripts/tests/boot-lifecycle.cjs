/* node scripts/tests/boot-lifecycle.cjs — runs actual TS modules with mocked native boundaries. */
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const assert = require('node:assert/strict')
const ts = require('typescript')
const root = path.resolve(__dirname, '../..')
function load(rel, mocks, globals = {}) {
  const source = fs.readFileSync(path.join(root, rel), 'utf8')
  const result = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, esModuleInterop: true }, reportDiagnostics: true })
  assert.equal((result.diagnostics || []).filter(d => d.category === ts.DiagnosticCategory.Error).length, 0)
  const mod = { exports: {} }
  vm.runInNewContext(result.outputText, { exports: mod.exports, module: mod, require(name) {
    assert.ok(name in mocks, `missing mock: ${name}`)
    return mocks[name]
  }, console: { log() {} }, setTimeout, setImmediate, ...globals }, { filename: rel })
  return mod.exports
}
async function scenario({ autoplay = false, history = false, cancelDuringInit = false, cancelDuringWait = false, nativeValid = true } = {}) {
  const mode = load('src/core/boot/mode.ts', {})
  const plan = load('src/core/boot/plan.ts', {})
  let now = 0, watcher, prepared = 0, played = 0, paused = 0, idle = 0, finished, resultWrites = 0
  const g = { lx: { playerTrackId: null, playerStatus: { isInitialized: false } }, i18n: { t: k => k } }
  const playerState = { playMusicInfo: { musicInfo: history ? {} : null } }
  const timer = {
    setTimeout(cb, ms) { now += ms; if (cancelDuringWait) { nativeValid = false; mode.cancelBootBackground() }; watcher?.(); return setImmediate(cb) },
    setInterval(cb) { watcher = cb; return 1 },
    clearInterval() { watcher = null },
  }
  const api = {
    isBootTaskCurrent: token => nativeValid && token === 7,
    finishBootAutostart(token, ok, reason) { finished = { token, ok, reason }; if (nativeValid) resultWrites++ },
  }
  const track = { pause: async() => { paused++ }, getState: async() => 0, updateNowPlayingMetadata: async(_info, isPlaying) => { assert.equal(isPlaying, false); idle++ } }
  const mocks = {
    'react-native-background-timer': timer,
    'react-native-track-player': { __esModule: true, default: track, State: { Playing: 3, Buffering: 6 } },
    '@/core/boot/mode': mode, '@/core/boot/plan': plan,
    '@/core/init': async() => { if (cancelDuringInit) mode.cancelBootBackground() },
    '@/core/list': { getListMusics: async() => [{}] },
    '@/core/player/player': { play: () => { played++ }, playList: async() => {} },
    '@/store/player/state': playerState,
    '@/store/setting/state': { setting: { 'player.startupAutoPlay': autoplay } },
    '@/utils/data': { getPlayInfo: async() => history ? { listId: 'one', index: 0 } : null },
    '@/plugins/player': { initial: async() => { prepared++; g.lx.playerStatus.isInitialized = true } },
    '@/utils/nativeModules/utils': api,
  }
  const task = load('src/core/boot/background.ts', mocks, { global: g, Date: { now: () => now } })
  mode.beginBootBackground(7) // same early claim as registerBootTask
  await task.runBackgroundAutostart({ token: 7 })
  assert.equal(mode.isBootBackground(), false)
  assert.equal(watcher, null)
  return { prepared, played, paused, idle, finished, resultWrites, g }
}
async function manualForeground() {
  let onLaunch, rootCalls = 0, initCalls = 0
  const mode = load('src/core/boot/mode.ts', {})
  const g = { lx: {} }
  const mocks = {
    '@/utils/errorHandle': {}, '@/utils/log': { init() {} },
    '@/utils/bootLog': { bootLog() {}, getBootLog() {} }, '@/config/globalData': {},
    '@/utils/data': { getFontSize: async() => 16 },
    './utils/nativeModules/utils': { isBootPlaybackRunning: () => true, exitApp() {} },
    './utils/windowSizeTools': { windowSizeTools: { init: async() => {} } },
    './navigation/regLaunchedEvent': { listenLaunchEvent() {} }, './utils/tools': { tipDialog() { throw Error('unexpected error dialog') } },
    '@/core/boot/mode': mode,
    '@/core/init': async() => { initCalls++; return () => {} },
    '@/navigation': { init(cb) { onLaunch = cb }, navigations: { pushHomeScreen: async() => { rootCalls++ } } },
  }
  load('src/app.ts', mocks, { global: g })
  for (let i = 0; i < 10; i++) await new Promise(setImmediate)
  assert.equal(mode.isBootBackground(), true)
  assert.equal(rootCalls, 0) // no Activity launch => no root
  assert.equal(typeof onLaunch, 'function')
  await onLaunch() // RNN emits only on actual Activity resumed
  assert.equal(initCalls, 1)
  assert.equal(rootCalls, 1) // boot service being alive must not swallow this event
}
async function restorationWaits() {
  let release
  const gate = new Promise(resolve => { release = resolve })
  const song = { id: 'song', meta: {} }
  const playerState = { playInfo: {}, playMusicInfo: { musicInfo: song }, musicInfo: { pic: null }, playedList: [] }
  const g = { lx: { restorePlayInfo: { time: 0 }, isPlayedStop: false }, app_event: { setProgress() {}, lyricUpdated() {} } }
  const mocks = {
    '@/plugins/player': { isInitialized: () => true, initTrackInfo: () => gate },
    '@/core/player/playStatus': {}, '@/store/player/state': playerState,
    '@/store/setting/state': { setting: {} },
    '@/core/player/playInfo': { getList: () => [song], setPlayListId() {}, setPlayMusicInfo() {}, setMusicInfo() {} },
    '@/core/player/playedList': { clearPlayedList() {} },
    '@/core/player/tempPlayList': { clearTempPlayeList() {} },
    '@/core/music': { getPicPath: async() => null, getLyricInfo: async() => ({ rawlrcInfo: {} }) },
    '@/utils/message': {}, '@/utils/common': {}, './utils': {},
    'react-native-background-timer': {},
    '@/utils/tools': { debounceBackgroundTimer: fn => fn },
    '@/config/constant': {}, '@/core/list': {}, '@/core/dislikeList': {},
    '@/core/boot/mode': { isBootCancelled: () => false, isBootBackground: () => true },
  }
  const player = load('src/core/player/player.ts', mocks, { global: g })
  let settled = false
  const pending = player.playList('list', 0).then(() => { settled = true })
  await new Promise(setImmediate)
  assert.equal(settled, false)
  release()
  await pending
  assert.equal(settled, true)
}
;(async() => {
  let r = await scenario()
  assert.equal(r.prepared, 1); assert.equal(r.idle, 1); assert.equal(r.played, 0); assert.equal(r.finished.ok, true)
  console.log('PASS no-history autoplay-off: real player setup + idle notification, no play')
  r = await scenario({ history: true })
  assert.equal(r.prepared, 1); assert.equal(r.played, 0)
  console.log('PASS history autoplay-off: no forced playback')
  r = await scenario({ autoplay: true, history: true, cancelDuringWait: true })
  assert.equal(r.played, 1); assert.equal(r.paused, 1); assert.equal(r.resultWrites, 0); assert.equal(r.g.lx.isPlayedStop, true)
  console.log('PASS switch-off during retry: no next play, pause, no stale result')
  r = await scenario({ autoplay: true, history: true, cancelDuringInit: true })
  assert.equal(r.prepared, 0); assert.equal(r.played, 0); assert.equal(r.finished.ok, false)
  console.log('PASS switch-off during init: no later setup/play')
  // A timed-out task's finally must not reset a newer task's mode/cancel handler.
  const mode = load('src/core/boot/mode.ts', {})
  mode.beginBootBackground(1)
  mode.cancelBootBackground()
  mode.beginBootBackground(2)
  if (mode.isBootSession(1)) mode.setBootBackground(false)
  assert.equal(mode.isBootSession(2), true)
  assert.equal(mode.isBootCancelled(), false)
  mode.beginBootBackground(1) // a delayed older registration cannot take over token 2
  assert.equal(mode.isBootSession(2), true)
  console.log('PASS stale JS generation cannot clear newer boot mode')
  await manualForeground()
  console.log('PASS foreground open while boot running: one UI root; no launch means no root')
  await restorationWaits()
  const player = fs.readFileSync(path.join(root, 'src/core/player/player.ts'), 'utf8')
  assert.match(player, /await initTrackInfo\(musicInfo, playerState.musicInfo\)/)
  assert.match(player, /await handleRestorePlay\(global.lx.restorePlayInfo\)/)
  console.log('PASS restoration awaits: handlePlay -> handleRestorePlay -> initTrackInfo')
  const tracks = fs.readFileSync(path.join(root, 'src/plugins/player/playList.ts'), 'utf8')
  assert.equal((tracks.match(/if \(!global.lx.isPlayedStop\) await TrackPlayer.play\(\)/g) || []).length, 2)
  console.log('PASS late URL/seek completion checks stopped state before native play')
})().catch(err => { console.error(err); process.exitCode = 1 })
