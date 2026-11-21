/* node scripts/tests/car-lyric-size-and-settings.cjs */
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const assert = require('node:assert/strict')
const ts = require('typescript')

const root = path.resolve(__dirname, '../..')

function loadTs(rel, mocks = {}, globals = {}) {
  const source = fs.readFileSync(path.join(root, rel), 'utf8')
  const result = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
      jsx: ts.JsxEmit.React,
    },
    reportDiagnostics: true,
  })
  const errors = (result.diagnostics || []).filter(d => d.category === ts.DiagnosticCategory.Error)
  assert.equal(errors.length, 0, `${rel} transpile error: ${JSON.stringify(errors)}`)
  const mod = { exports: {} }
  const ctx = {
    exports: mod.exports,
    module: mod,
    require(name) {
      if (name in mocks) return mocks[name]
      if (name.startsWith('./') || name.startsWith('../')) {
        const resolved = path.relative(root, path.resolve(path.dirname(path.join(root, rel)), name))
        const candidates = [resolved + '.ts', resolved + '.tsx', resolved + '/index.ts', resolved]
        for (const c of candidates) {
          if (c in mocks) return mocks[c]
        }
      }
      throw new Error(`missing mock for ${name} (from ${rel})`)
    },
    console,
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    Date,
    Math,
    Number,
    Object,
    Array,
    String,
    Boolean,
    ...globals,
  }
  vm.runInNewContext(result.outputText, ctx, { filename: rel })
  return mod.exports
}

// 1. lrcFontSize pure math: pt unit, min=1, NO upper limit, car proportion preserved
const lrcMath = loadTs('src/screens/PlayDetail/lrcFontSize.ts')
assert.equal(lrcMath.MIN_LRC_FONT_PT, 1)
assert.equal(lrcMath.getDefaultLrcFontPt('vertical'), 21)
assert.equal(lrcMath.getDefaultLrcFontPt('horizontal'), 22)

assert.equal(lrcMath.normalizeLrcFontPt(0), 1)
assert.equal(lrcMath.normalizeLrcFontPt(-10), 1)
assert.equal(lrcMath.normalizeLrcFontPt(Number.NaN, 22), 22)
assert.equal(lrcMath.normalizeLrcFontPt(500), 500)
assert.equal(lrcMath.normalizeLrcFontPt(9999), 9999)

assert.equal(lrcMath.stepLrcFontPt(1, -1), 1)
assert.equal(lrcMath.stepLrcFontPt(22, 1), 23)
assert.equal(lrcMath.stepLrcFontPt(300, 1), 301)
assert.equal(lrcMath.stepLrcFontPt(1000, 50), 1050)

// Exact 36 / 22 / 20 preservation at default 22 pt
assert.equal(lrcMath.calcHorizontalLyricSize(22, 0), 36)
assert.equal(lrcMath.calcHorizontalLyricSize(22, 1), 22)
assert.equal(lrcMath.calcHorizontalLyricSize(22, 2), 20)
assert.equal(lrcMath.calcHorizontalLyricSize(22, -1), 20)
assert.equal(lrcMath.calcHorizontalPreviewSize(22), 42)
// Very large pt scales linearly without cap
assert.equal(lrcMath.calcHorizontalLyricSize(220, 0), 360)
assert.equal(lrcMath.calcHorizontalPreviewSize(220), 420)
assert.equal(lrcMath.calcVerticalLyricSize(21), 21)
assert.equal(lrcMath.calcVerticalLyricSize(120), 120)
console.log('PASS lrcFontSize: min 1pt, unbounded upper scaling, horizontal 36/22/20 proportions preserved')

// 2. Migration & persistence across restart (via actual src/config/setting.ts!)
const migratePlayDetail = loadTs('src/config/migratePlayDetail.ts').default
const defaultSetting = loadTs('src/config/defaultSetting.ts').default
assert.equal(defaultSetting['playDetail.style.fontUnit'], 'pt')
assert.equal(defaultSetting['playDetail.vertical.style.lrcFontSize'], 21)
assert.equal(defaultSetting['playDetail.horizontal.style.lrcFontSize'], 22)

const legacyMigrated = migratePlayDetail({
  'playDetail.vertical.style.lrcFontSize': 210,
  'playDetail.horizontal.style.lrcFontSize': 300,
})
assert.equal(legacyMigrated['playDetail.style.fontUnit'], 'pt')
assert.equal(legacyMigrated['playDetail.vertical.style.lrcFontSize'], 21)
assert.equal(legacyMigrated['playDetail.horizontal.style.lrcFontSize'], 30)

// Large pt values (> 50, e.g. 120 pt) MUST NOT be divided by 10 on next restart when fontUnit == 'pt'
const persistedLarge = migratePlayDetail({
  'playDetail.style.fontUnit': 'pt',
  'playDetail.vertical.style.lrcFontSize': 88,
  'playDetail.horizontal.style.lrcFontSize': 160,
})
assert.equal(persistedLarge['playDetail.vertical.style.lrcFontSize'], 88)
assert.equal(persistedLarge['playDetail.horizontal.style.lrcFontSize'], 160)

// End-to-end initSetting persistence check with mocked storage
async function testInitSettingPersistence() {
  let stored = {
    version: '2.1',
    'playDetail.vertical.style.lrcFontSize': 240,
    'playDetail.horizontal.style.lrcFontSize': 260,
  }
  const migrateDesktop = loadTs('src/config/migrateDesktopLyric.ts').default
  const settingMod = loadTs('src/config/setting.ts', {
    '@/config/constant': { storageDataPrefix: { setting: 'setting' }, storageDataPrefixOld: { setting: 'old' } },
    '@/config/defaultSetting': { __esModule: true, default: defaultSetting },
    '@/plugins/storage': {
      getData: async() => stored,
      removeData: async() => {},
      saveData: async(_k, v) => { stored = v },
    },
    './migrateSetting': { __esModule: true, default: s => s },
    './migrateDesktopLyric': { __esModule: true, default: migrateDesktop },
    './migratePlayDetail': { __esModule: true, default: migratePlayDetail },
    '@/store/setting/state': { __esModule: true, default: { setting: defaultSetting } },
    './migrate': { migrateMetaData: async() => {}, migrateListData: async() => {} },
    '@/utils/tools': { exitApp() {}, tipDialog: async() => {} },
  })
  const firstRun = await settingMod.initSetting()
  assert.equal(firstRun.setting['playDetail.vertical.style.lrcFontSize'], 24)
  assert.equal(firstRun.setting['playDetail.horizontal.style.lrcFontSize'], 26)
  assert.equal(firstRun.setting['playDetail.style.fontUnit'], 'pt')

  // Simulate user raising horizontal font size to 120 pt and restarting app
  stored = { ...firstRun.setting, 'playDetail.horizontal.style.lrcFontSize': 120, 'playDetail.vertical.style.lrcFontSize': 75 }
  const secondRun = await settingMod.initSetting()
  assert.equal(secondRun.setting['playDetail.horizontal.style.lrcFontSize'], 120)
  assert.equal(secondRun.setting['playDetail.vertical.style.lrcFontSize'], 75)
}

// 3. CarLayout auto-hide + upper-right settings button + modal pause/resume state machine
function testControlsIdle() {
  const { createControlsIdleController } = loadTs('src/screens/PlayDetail/CarLayout/controlsIdle.ts')
  let nextId = 1
  const timers = new Map()
  const events = []
  const fakeSetTimeout = (cb, ms) => {
    const id = nextId++
    timers.set(id, { cb, ms })
    return id
  }
  const fakeClearTimeout = (id) => { timers.delete(id) }
  const fireAllTimers = () => {
    for (const [id, entry] of [...timers.entries()]) {
      timers.delete(id)
      entry.cb()
    }
  }

  const ctrl = createControlsIdleController(5000, {
    onVisibleChange: v => events.push(v),
    setTimeoutFn: fakeSetTimeout,
    clearTimeoutFn: fakeClearTimeout,
  })
  assert.equal(ctrl.isVisible(), true)
  ctrl.noteInteraction()
  assert.equal(timers.size, 1)
  assert.equal([...timers.values()][0].ms, 5000)

  // Idle 5s hides controls + upper-right settings button together
  fireAllTimers()
  assert.equal(ctrl.isVisible(), false)
  assert.deepEqual(events, [false])

  // Screen tap brings controls + upper-right settings button back together
  ctrl.noteInteraction()
  assert.equal(ctrl.isVisible(), true)
  assert.deepEqual(events, [false, true])
  assert.equal(timers.size, 1)

  // Open settings modal: auto-hide is paused and any trailing touchEnd does NOT re-arm hide while open
  ctrl.pause()
  assert.equal(ctrl.isPaused(), true)
  assert.equal(timers.size, 0)
  ctrl.noteInteraction() // trailing onTouchEnd from button press
  assert.equal(timers.size, 0)
  assert.equal(ctrl.isVisible(), true)

  // Close settings modal: resumes and arms a fresh 5s countdown, then auto-hides again
  ctrl.resume()
  assert.equal(ctrl.isPaused(), false)
  assert.equal(ctrl.isVisible(), true)
  assert.equal(timers.size, 1)
  fireAllTimers()
  assert.equal(ctrl.isVisible(), false)
  assert.deepEqual(events, [false, true, false])

  ctrl.dispose()
}

// 4. Layout selection & CarLayout/SettingPopup structural checks
function testLayoutAndComponents() {
  const { selectPlayerLayout } = loadTs('src/screens/PlayDetail/playerLayout.ts', {
    '@/utils/tools': { isHorizontalMode: (w, h) => w / h > 1.2 },
  })
  assert.equal(selectPlayerLayout(1920, 1080, true), 'car')
  assert.equal(selectPlayerLayout(1920, 1080, false), 'horizontal')
  assert.equal(selectPlayerLayout(1080, 1920, true), 'vertical')
  assert.equal(selectPlayerLayout(1080, 1920, false), 'vertical')

  const carLayoutSrc = fs.readFileSync(path.join(root, 'src/screens/PlayDetail/CarLayout/index.tsx'), 'utf8')
  // Upper-right settings button lives inside {visible ? ... : null}, NOT permanently visible outside it
  const visibleMatches = carLayoutSrc.match(/\{visible\s*\?\s*\(/g) || []
  assert.equal(visibleMatches.length, 2, 'Expected both upper-right settings button and bottom Controls gated by visible')
  assert.match(carLayoutSrc, /lucide-sliders-horizontal/)
  assert.match(carLayoutSrc, /position:\s*'absolute',\s*top:\s*px\(16\),\s*right:\s*px\(28\)/)
  assert.match(carLayoutSrc, /<SettingPopup\s+ref=\{settingPopupRef\}\s+position="right"\s+direction="horizontal"\s+onHide=\{resumeIdle\}/)
  assert.match(carLayoutSrc, /flexGrow:\s*7/) // preserves car lyric/cover 7:3 proportions

  // No upper clamp in either SettingLrcFontSize
  const hStepper = fs.readFileSync(path.join(root, 'src/screens/PlayDetail/Horizontal/components/SettingPopup/settings/SettingLrcFontSize.tsx'), 'utf8')
  const vStepper = fs.readFileSync(path.join(root, 'src/screens/PlayDetail/components/SettingPopup/settings/SettingLrcFontSize.tsx'), 'utf8')
  assert.doesNotMatch(hStepper, /maximumValue|300/)
  assert.doesNotMatch(vStepper, /maximumValue|300/)
  assert.match(hStepper, /setting_lyric_desktop_font_unit/)
  assert.match(vStepper, /setting_lyric_desktop_font_unit/)

  // Stepper placed before preview in Horizontal SettingPopup so button never moves as font grows huge
  const hPopup = fs.readFileSync(path.join(root, 'src/screens/PlayDetail/Horizontal/components/SettingPopup/index.tsx'), 'utf8')
  assert.ok(hPopup.indexOf('<SettingLrcFontSize') < hPopup.indexOf("t('play_detail_setting_preview')"))

  // Verify package.json / lockfile / minSdk / zh-cn keys
  const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'))
  assert.equal(pkg.version, '0.0.11')
  assert.equal(pkg.versionCode, 11)
  const lock = JSON.parse(fs.readFileSync(path.join(root, 'package-lock.json'), 'utf8'))
  assert.equal(lock.version, '1.9.2')
  const zh = JSON.parse(fs.readFileSync(path.join(root, 'src/lang/zh-cn.json'), 'utf8'))
  assert.equal(zh.setting_lyric_desktop_font_unit, '磅')
  assert.ok(zh.play_detail_setting_title)
}

;(async() => {
  await testInitSettingPersistence()
  console.log('PASS migration & persistence: legacy 240/260 -> 24/26 pt once; 120/75 pt preserved across restart')
  testControlsIdle()
  console.log('PASS CarLayout controlsIdle: 5s hide, tap restore, pause during SettingPopup, resume fresh 5s')
  testLayoutAndComponents()
  console.log('PASS structural checks: car upper-right visible-gated settings, no font upper limit, stepper before preview, v0.0.11')
})().catch(err => {
  console.error(err)
  process.exitCode = 1
})
