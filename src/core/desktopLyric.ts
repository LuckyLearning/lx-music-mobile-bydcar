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
  setOtherTextSize,
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
  setHeight,
  setLyricStyle,
  setMusicMeta,
  setTitleOptions,
} from '@/utils/nativeModules/lyricDesktop'
import settingState from '@/store/setting/state'
import playerState from '@/store/player/state'
import { tranditionalize } from '@/utils/simplify-chinese-main'
import { getPosition } from '@/plugins/player'
export {
  onLyricLinePlay,
} from '@/utils/nativeModules/lyricDesktop'

const readMusicMeta = () => {
  const info = playerState.musicInfo
  return {
    musicName: info.name ?? '',
    musicSinger: info.singer ?? '',
  }
}

export const showDesktopLyric = async() => {
  const setting = settingState.setting
  const meta = readMusicMeta()
  await showDesktopLyricView({
    isShowToggleAnima: setting['desktopLyric.showToggleAnima'],
    isSingleLine: setting['desktopLyric.isSingleLine'],
    isLock: setting['desktopLyric.isLock'],
    unplayColor: setting['desktopLyric.style.lyricUnplayColor'],
    playedColor: setting['desktopLyric.style.lyricPlayedColor'],
    shadowColor: setting['desktopLyric.style.lyricShadowColor'],
    opacity: setting['desktopLyric.style.opacity'],
    textSize: setting['desktopLyric.style.fontSize'],
    otherTextSize: setting['desktopLyric.style.otherFontSize'],
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
    // 滑块存的是 0-50，原生 letterSpacing 是 em，实时调节走的是 /100
    letterSpacing: setting['desktopLyric.style.letterSpacing'] / 100,
    bold: setting['desktopLyric.style.bold'],
    hollowStyle: setting['desktopLyric.style.hollowStyle'],
    hollowSize: setting['desktopLyric.style.hollowSize'],
    height: setting['desktopLyric.height'],
    displayId: setting['desktopLyric.displayId'],
    musicName: meta.musicName,
    musicSinger: meta.musicSinger,
    titleMode: setting['desktopLyric.title.mode'],
    titlePosition: setting['desktopLyric.title.position'],
    titleAlign: setting['desktopLyric.title.align'],
    titleColor: setting['desktopLyric.title.color'],
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
export const setDesktopLyricOtherTextSize = setOtherTextSize
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

// ===== [BYD] 位置 / 高度 / 歌词样式 / 标题 =====

/** 窗口高度百分比（0 = 按行数自适应） */
export const setDesktopLyricHeight = setHeight

/** 按屏幕百分比调整悬浮窗位置（0-100，左上角基准） */
export const setDesktopLyricPositionPercent = setPosition

/** 歌词样式：加粗 / 内置空心样式 / 空心大小 */
export const setDesktopLyricStyle = async(bold?: boolean, hollowStyle?: number, hollowSize?: number) => {
  const s = settingState.setting
  return setLyricStyle(
    bold ?? s['desktopLyric.style.bold'],
    hollowStyle ?? s['desktopLyric.style.hollowStyle'],
    hollowSize ?? s['desktopLyric.style.hollowSize'],
  )
}

/** 同步当前歌曲名/歌手到悬浮窗 */
export const setDesktopLyricMusicMeta = async(name?: string, singer?: string) => {
  const meta = readMusicMeta()
  return setMusicMeta(name ?? meta.musicName, singer ?? meta.musicSinger)
}

/** 标题显示选项 */
export const setDesktopLyricTitleOptions = async(
  mode?: LX.AppSetting['desktopLyric.title.mode'],
  position?: LX.AppSetting['desktopLyric.title.position'],
  align?: LX.AppSetting['desktopLyric.title.align'],
  color?: string,
) => {
  const s = settingState.setting
  return setTitleOptions(
    mode ?? s['desktopLyric.title.mode'],
    position ?? s['desktopLyric.title.position'],
    align ?? s['desktopLyric.title.align'],
    color ?? s['desktopLyric.title.color'],
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
