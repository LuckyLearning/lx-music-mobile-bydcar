import BackgroundTimer from 'react-native-background-timer'
import TrackPlayer, { State } from 'react-native-track-player'

import { setBootBackground, isBootSession, isBootCancelled, cancelBootBackground, onBootCancel } from '@/core/boot/mode'
import { planBootPlayback } from '@/core/boot/plan'
import init from '@/core/init'
import { getListMusics } from '@/core/list'
import { play, playList } from '@/core/player/player'
import playerState from '@/store/player/state'
import settingState from '@/store/setting/state'
import { getPlayInfo } from '@/utils/data'
import { initial as preparePlayer } from '@/plugins/player'
import { finishBootAutostart, isBootTaskCurrent } from '@/utils/nativeModules/utils'

/**
 * 开机后台启动。听风那条路是 startService 拉播放服务、不打开主界面。
 *
 * 出不出声看「启动后自动播放」：打开才恢复上次那一首并出声；关掉则只把播放器在后台就绪，不强制起播。
 * 没有播放记录时不挑一首在线歌。
 *
 * 重试节奏由原生 {@code BootRetryPolicy} 传进来，缺省值与那边相同。
 */

const DEFAULT_DELAYS = [0, 4_000, 10_000]
const DEFAULT_CONFIRM_MS = 8_000
const PLACEHOLDER_TRACK = /\/\/default$/

export interface BootTaskData {
  token: number
  delays?: number[]
  confirmMs?: number
}

interface Attempt {
  ok: boolean
  /** 再试也不会变，例如根本没有播放记录。 */
  terminal: boolean
  reason: string
}

const sleep = async(ms: number, checkCurrent: () => void) => {
  const deadline = Date.now() + ms
  do {
    checkCurrent()
    await new Promise<void>(resolve => { BackgroundTimer.setTimeout(resolve, Math.min(200, Math.max(0, deadline - Date.now()))) })
  } while (Date.now() < deadline)
  checkCurrent()
}

const text = (key: 'boot_autostart_no_history' | 'boot_autostart_missing_track' | 'boot_autostart_not_ready' | 'boot_autostart_prepared') => {
  return global.i18n?.t(key) ?? key
}

const normalizeDelays = (value: unknown): number[] => {
  if (!Array.isArray(value) || value.length === 0) return DEFAULT_DELAYS
  const delays = value.map(item => Number(item)).filter(item => Number.isFinite(item) && item >= 0)
  return delays.length > 0 ? delays : DEFAULT_DELAYS
}

const waitForPlayback = async(confirmMs: number, checkCurrent: () => void): Promise<boolean> => {
  const deadline = Date.now() + confirmMs
  while (Date.now() < deadline) {
    try {
      const state = await TrackPlayer.getState()
      const trackId = global.lx.playerTrackId
      const realTrack = !!trackId && !PLACEHOLDER_TRACK.test(trackId)
      const started = state == State.Playing || state == State.Buffering
      if (realTrack && started) return true
    } catch {
      // 播放器还在 setup，下一拍再看。
    }
    await sleep(400, checkCurrent)
  }
  return false
}

const attemptBootPlayback = async(confirmMs: number, checkCurrent: () => void): Promise<Attempt> => {
  const info = await getPlayInfo()
  if (!info?.listId || info.index < 0) {
    return { ok: false, terminal: true, reason: text('boot_autostart_no_history') }
  }
  checkCurrent()
  const list = await getListMusics(info.listId)
  checkCurrent()
  if (!list[info.index]) {
    return { ok: false, terminal: true, reason: text('boot_autostart_missing_track') }
  }
  if (!playerState.playMusicInfo.musicInfo) {
    global.lx.restorePlayInfo = info
    await playList(info.listId, info.index)
  }
  checkCurrent()
  play()
  if (await waitForPlayback(confirmMs, checkCurrent)) return { ok: true, terminal: true, reason: '' }
  return { ok: false, terminal: false, reason: text('boot_autostart_not_ready') }
}

export async function runBackgroundAutostart(data: BootTaskData) {
  // Registration claims the generation before this lazy import settles.
  if (!isBootSession(data.token) || !isBootTaskCurrent(data.token)) {
    finishBootAutostart(data.token, false, 'boot_cancelled')
    if (isBootSession(data.token)) setBootBackground(false)
    return
  }
  let ok = false
  let reason = ''
  const token = data.token
  const deadline = Date.now() + 75_000
  const checkCurrent = () => {
    if (!isBootSession(token)) throw new Error('boot_stale_session')
    if (Date.now() >= deadline || !isBootTaskCurrent(token)) cancelBootBackground()
    if (isBootCancelled()) throw new Error('boot_cancelled')
  }
  onBootCancel(() => {
    // In-flight URL requests check this before setResource; avoid delayed audio after switch-off.
    global.lx.isPlayedStop = true
    if (global.lx.playerStatus.isInitialized) void TrackPlayer.pause().catch(() => {})
  })
  const watchdog = BackgroundTimer.setInterval(() => {
    try { checkCurrent() } catch { BackgroundTimer.clearInterval(watchdog) }
  }, 200)
  try {
    checkCurrent()
    console.log('LXBoot background autostart')
    await init()
    checkCurrent()
    // Even with no history, keep the real media service ready (not just this short-lived task).
    const setting = settingState.setting
    await preparePlayer({
      volume: setting['player.volume'],
      playRate: setting['player.playbackRate'],
      cacheSize: setting['player.cacheSize'] ? parseInt(setting['player.cacheSize']) : 0,
      isHandleAudioFocus: setting['player.isHandleAudioFocus'],
      isEnableAudioOffload: setting['player.isEnableAudioOffload'],
    })
    checkCurrent()
    if (!playerState.playMusicInfo.musicInfo) {
      // Pinned TrackPlayer keeps MusicService alive; replace its startup notification with a valid idle one.
      await TrackPlayer.updateNowPlayingMetadata({ title: 'LX Music', artist: '' }, false)
    }
    checkCurrent()
    if (planBootPlayback(!!settingState.setting['player.startupAutoPlay']) === 'prepare') {
      // init 里已经按记录恢复了列表，这里不再调用 play()。
      ok = true
      reason = text('boot_autostart_prepared')
      console.log('LXBoot prepared without autoplay')
      return
    }
    const delays = normalizeDelays(data?.delays)
    const confirmMs = typeof data?.confirmMs === 'number' && data.confirmMs > 0
      ? data.confirmMs
      : DEFAULT_CONFIRM_MS
    for (let attempt = 0; attempt < delays.length; attempt++) {
      const wait = delays[attempt] ?? 0
      if (wait > 0) await sleep(wait, checkCurrent)
      checkCurrent()
      const result = await attemptBootPlayback(confirmMs, checkCurrent)
      checkCurrent()
      reason = result.reason
      if (result.ok) {
        ok = true
        console.log('LXBoot playback started on attempt', attempt + 1)
        return
      }
      console.log('LXBoot attempt failed', attempt + 1, reason)
      if (result.terminal) return
    }
  } catch (err) {
    const message = err instanceof Error ? (err.message || err.name) : String(err)
    reason = message || 'init_failed'
    console.log('LXBoot background autostart failed', reason)
  } finally {
    BackgroundTimer.clearInterval(watchdog)
    // Real MusicService and restored track/idle notification are ready before handoff.
    finishBootAutostart(token, ok, reason)
    if (isBootSession(token)) {
      onBootCancel(null)
      setBootBackground(false)
    }
  }
}
