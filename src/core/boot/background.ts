import BackgroundTimer from 'react-native-background-timer'
import TrackPlayer, { State } from 'react-native-track-player'

import { setBootBackground } from '@/core/boot/mode'
import { planBootPlayback } from '@/core/boot/plan'
import init from '@/core/init'
import { getListMusics } from '@/core/list'
import { play, playList } from '@/core/player/player'
import playerState from '@/store/player/state'
import settingState from '@/store/setting/state'
import { getPlayInfo } from '@/utils/data'
import { finishBootAutostart } from '@/utils/nativeModules/utils'

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
  delays?: number[]
  confirmMs?: number
}

interface Attempt {
  ok: boolean
  /** 再试也不会变，例如根本没有播放记录。 */
  terminal: boolean
  reason: string
}

const sleep = (ms: number) => new Promise<void>(resolve => {
  BackgroundTimer.setTimeout(() => resolve(), ms)
})

const text = (key: 'boot_autostart_no_history' | 'boot_autostart_missing_track' | 'boot_autostart_not_ready' | 'boot_autostart_prepared') => {
  return global.i18n?.t(key) ?? key
}

const normalizeDelays = (value: unknown): number[] => {
  if (!Array.isArray(value) || value.length === 0) return DEFAULT_DELAYS
  const delays = value.map(item => Number(item)).filter(item => Number.isFinite(item) && item >= 0)
  return delays.length > 0 ? delays : DEFAULT_DELAYS
}

const waitForPlayback = async(confirmMs: number): Promise<boolean> => {
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
    await sleep(400)
  }
  return false
}

const attemptBootPlayback = async(confirmMs: number): Promise<Attempt> => {
  const info = await getPlayInfo()
  if (!info?.listId || info.index < 0) {
    return { ok: false, terminal: true, reason: text('boot_autostart_no_history') }
  }
  const list = await getListMusics(info.listId)
  if (!list[info.index]) {
    return { ok: false, terminal: true, reason: text('boot_autostart_missing_track') }
  }
  if (!playerState.playMusicInfo.musicInfo) {
    global.lx.restorePlayInfo = info
    await playList(info.listId, info.index)
  }
  play()
  if (await waitForPlayback(confirmMs)) return { ok: true, terminal: true, reason: '' }
  return { ok: false, terminal: false, reason: text('boot_autostart_not_ready') }
}

export async function runBackgroundAutostart(data?: BootTaskData) {
  setBootBackground(true)
  let ok = false
  let reason = ''
  try {
    console.log('LXBoot background autostart')
    await init()
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
      if (wait > 0) await sleep(wait)
      const result = await attemptBootPlayback(confirmMs)
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
    setBootBackground(false)
    // 先让播放器自己的前台通知贴上，再撤「正在恢复」。否则进程会在两条通知的空档里被收掉。
    if (ok) await sleep(500)
    finishBootAutostart(ok, reason)
  }
}
