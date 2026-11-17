import {
  hideDesktopLyricView,
  showDesktopLyricView,
  setSendLyricTextEvent,
  setLyric,
  play,
  pause,
  setPlaybackRate,
  toggleTranslation,
  toggleRoma,
  toggleLock,
  setColor,
  setAlpha,
  setTextSize,
  setShowToggleAnima,
  setSingleLine,
  setPosition,
  setMaxLineNum,
  setWidth,
  setLyricTextPosition,
  checkOverlayPermission,
  openOverlayPermissionActivity,
  onPositionChange,
  // [BYD] 悬浮窗增强
  setBackgroundStyle,
  setLetterSpacing,
  setTargetDisplay,
  getDisplays,
  // [BYD] 位置 / 高度 / 歌词样式
  setHeight,
  setLyricStyle,
} from '@/utils/nativeModules/lyricDesktop'
import settingState from '@/store/setting/state'
import playerState from '@/store/player/state'
import { tranditionalize } from '@/utils/simplify-chinese-main'
import { getPosition } from '@/plugins/player'
export {
  onLyricLinePlay,
} from '@/utils/nativeModules/lyricDesktop'

export const showDesktopLyric = async() => {
  const setting = settingState.setting
  await showDesktopLyricView({
    isShowToggleAnima: setting['desktopLyric.showToggleAnima'],
    isSingleLine: setting['desktopLyric.isSingleLine'],
    isLock: setting['desktopLyric.isLock'],
    unplayColor: setting['desktopLyric.style.lyricUnplayColor'],
    playedColor: setting['desktopLyric.style.lyricPlayedColor'],
    shadowColor: setting['desktopLyric.style.lyricShadowColor'],
    opacity: setting['desktopLyric.style.opacity'],
    textSize: setting['desktopLyric.style.fontSize'],
    width: setting['desktopLyric.width'],
    maxLineNum: setting['desktopLyric.maxLineNum'],
    positionX: setting['desktopLyric.position.x'],
    positionY: setting['desktopLyric.position.y'],
    textPositionX: setting['desktopLyric.textPosition.x'],
    textPositionY: setting['desktopLyric.textPosition.y'],
    // [BYD] 自定义样式与投屏屏
    bgColor: setting['desktopLyric.style.bgColor'],
    bgRadius: setting['desktopLyric.style.bgRadius'],
    borderColor: setting['desktopLyric.style.borderColor'],
    borderWidth: setting['desktopLyric.style.borderWidth'],
    letterSpacing: setting['desktopLyric.style.letterSpacing'],
    bold: setting['desktopLyric.style.bold'],
    strokeColor: setting['desktopLyric.style.strokeColor'],
    strokeWidth: setting['desktopLyric.style.strokeWidth'],
    hollow: setting['desktopLyric.style.hollow'],
    height: setting['desktopLyric.height'],
    displayId: setting['desktopLyric.displayId'],
  })
  let lrc = playerState.musicInfo.lrc ?? ''
  let tlrc = playerState.musicInfo.tlrc ?? ''
  let rlrc = playerState.musicInfo.rlrc ?? ''
  if (setting['player.isS2t']) {
    lrc = tranditionalize(lrc)
    tlrc = tranditionalize(tlrc)
  }
  await setLyric(lrc, tlrc, rlrc)
  if (playerState.isPlay && !global.lx.gettingUrlId) {
    void getPosition().then(position => {
      void play(position * 1000)
    })
  }
}

export const hideDesktopLyric = async() => {
  return hideDesktopLyricView()
}

export const playDesktopLyric = play
export const pauseDesktopLyric = pause
export const setDesktopLyric = setLyric
export const setDesktopLyricPlaybackRate = setPlaybackRate
export const toggleDesktopLyricTranslation = toggleTranslation
export const toggleDesktopLyricRoma = toggleRoma
export const toggleDesktopLyricLock = toggleLock
export const setDesktopLyricColor = async(unplayColor: string | null, playedColor: string | null, shadowColor: string | null) => {
  return setColor(unplayColor ?? settingState.setting['desktopLyric.style.lyricUnplayColor'],
    playedColor ?? settingState.setting['desktopLyric.style.lyricPlayedColor'],
    shadowColor ?? settingState.setting['desktopLyric.style.lyricShadowColor'],
  )
}
export const setDesktopLyricAlpha = setAlpha
export const setDesktopLyricTextSize = setTextSize
export const setShowDesktopLyricToggleAnima = setShowToggleAnima
export const setDesktopLyricSingleLine = setSingleLine
export const setDesktopLyricPosition = setPosition
export const setDesktopLyricMaxLineNum = setMaxLineNum
export const setDesktopLyricWidth = setWidth
export const setDesktopLyricTextPosition = async(x: LX.AppSetting['desktopLyric.textPosition.x'] | null, y: LX.AppSetting['desktopLyric.textPosition.y'] | null) => {
  return setLyricTextPosition(x ?? settingState.setting['desktopLyric.textPosition.x'], y ?? settingState.setting['desktopLyric.textPosition.y'])
}
export const checkDesktopLyricOverlayPermission = checkOverlayPermission
export const openDesktopLyricOverlayPermissionActivity = openOverlayPermissionActivity
export const onDesktopLyricPositionChange = onPositionChange

// ===== [BYD] 悬浮窗自定义样式与多屏投射 =====
export const setDesktopLyricBackground = async(bgColor?: string, bgRadius?: number, borderColor?: string, borderWidth?: number) => {
  const s = settingState.setting
  return setBackgroundStyle(
    bgColor ?? s['desktopLyric.style.bgColor'],
    bgRadius ?? s['desktopLyric.style.bgRadius'],
    borderColor ?? s['desktopLyric.style.borderColor'],
    borderWidth ?? s['desktopLyric.style.borderWidth'],
  )
}
export const setDesktopLyricLetterSpacing = setLetterSpacing
export const setDesktopLyricDisplay = setTargetDisplay
export const getDesktopLyricDisplays = getDisplays

// ===== [BYD] 位置 / 高度 / 歌词样式 =====

/** 窗口高度百分比（0 = 按行数自适应） */
export const setDesktopLyricHeight = setHeight

/** 按屏幕百分比调整悬浮窗位置（0-100，左上角基准） */
export const setDesktopLyricPositionPercent = setPosition

/** 歌词样式：加粗 / 描边色 / 描边宽 / 空心字 */
export const setDesktopLyricStyle = async(bold?: boolean, strokeColor?: string, strokeWidth?: number, hollow?: boolean) => {
  const s = settingState.setting
  return setLyricStyle(
    bold ?? s['desktopLyric.style.bold'],
    strokeColor ?? s['desktopLyric.style.strokeColor'],
    strokeWidth ?? s['desktopLyric.style.strokeWidth'],
    hollow ?? s['desktopLyric.style.hollow'],
  )
}


export const showRemoteLyric = async(isSend: boolean) => {
  await setSendLyricTextEvent(isSend)
  if (isSend) {
    let lrc = playerState.musicInfo.lrc ?? ''
    let tlrc = playerState.musicInfo.tlrc ?? ''
    let rlrc = playerState.musicInfo.rlrc ?? ''
    if (settingState.setting['player.isS2t']) {
      lrc = tranditionalize(lrc)
      tlrc = tranditionalize(tlrc)
    }
    await setLyric(lrc, tlrc, rlrc)
    if (playerState.isPlay && !global.lx.gettingUrlId) {
      void getPosition().then(position => {
        void play(position * 1000)
      })
    }
  }
}
