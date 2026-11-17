import { NativeModules, NativeEventEmitter } from 'react-native'

const { LyricModule } = NativeModules

const getAlpha = (num: number) => num / 100
// 字号已经是磅，直接交给原生（setTextSize 用 sp，1 磅 = 旧滑块 10）。不要再除 10。
const getTextSize = (num: number) => num

/**
 * 发送歌词事件
 */
export const setSendLyricTextEvent = async(isSend: boolean) => {
  return LyricModule.setSendLyricTextEvent(isSend)
}

/**
 * show lyric
 */
export const showDesktopLyricView = async({
  isShowToggleAnima,
  isSingleLine,
  width,
  maxLineNum,
  isLock,
  unplayColor,
  playedColor,
  shadowColor,
  opacity,
  textSize,
  otherTextSize,
  positionX,
  positionY,
  textPositionX,
  textPositionY,
  bgColor,
  bgRadius,
  borderColor,
  borderWidth,
  letterSpacing,
  bold,
  hollowStyle,
  hollowSize,
  otherAlign,
  otherLetterSpacing,
  otherBold,
  otherHollowStyle,
  otherHollowSize,
  height,
  displayId,
  musicName,
  musicSinger,
  titleMode,
  titlePosition,
  titleAlign,
  titleColor,
  titleTextSize,
  titleLetterSpacing,
  titleBold,
  titleHollowStyle,
  titleHollowSize,
}: {
  isShowToggleAnima: boolean
  isSingleLine: boolean
  width: number
  maxLineNum: number
  isLock: boolean
  unplayColor: string
  playedColor: string
  shadowColor: string
  opacity: number
  textSize: number
  otherTextSize: number
  positionX: number
  positionY: number
  textPositionX: LX.AppSetting['desktopLyric.textPosition.x']
  textPositionY: LX.AppSetting['desktopLyric.textPosition.y']
  bgColor: string
  bgRadius: number
  borderColor: string
  borderWidth: number
  letterSpacing: number
  bold: boolean
  hollowStyle: LX.AppSetting['desktopLyric.style.hollowStyle']
  hollowSize: number
  otherAlign: LX.AppSetting['desktopLyric.style.otherAlign']
  otherLetterSpacing: number
  otherBold: boolean
  otherHollowStyle: LX.AppSetting['desktopLyric.style.hollowStyle']
  otherHollowSize: number
  height: number
  displayId: number
  musicName: string
  musicSinger: string
  titleMode: LX.AppSetting['desktopLyric.title.mode']
  titlePosition: LX.AppSetting['desktopLyric.title.position']
  titleAlign: LX.AppSetting['desktopLyric.title.align']
  titleColor: string
  titleTextSize: number
  titleLetterSpacing: number
  titleBold: boolean
  titleHollowStyle: LX.AppSetting['desktopLyric.style.hollowStyle']
  titleHollowSize: number
}): Promise<void> => {
  return LyricModule.showDesktopLyric({
    isSingleLine,
    isShowToggleAnima,
    isLock,
    unplayColor,
    playedColor,
    shadowColor,
    alpha: getAlpha(opacity),
    textSize: getTextSize(textSize),
    otherTextSize: getTextSize(otherTextSize),
    lyricViewX: positionX,
    lyricViewY: positionY,
    textX: textPositionX.toUpperCase(),
    textY: textPositionY.toUpperCase(),
    width,
    maxLineNum,
    bgColor,
    bgRadius,
    borderColor,
    borderWidth,
    letterSpacing,
    bold,
    hollowStyle,
    hollowSize,
    otherAlign: otherAlign.toUpperCase(),
    otherLetterSpacing,
    otherBold,
    otherHollowStyle,
    otherHollowSize,
    height,
    displayId,
    musicName,
    musicSinger,
    titleMode,
    titlePosition,
    titleAlign,
    titleColor,
    titleTextSize: getTextSize(titleTextSize),
    titleLetterSpacing,
    titleBold,
    titleHollowStyle,
    titleHollowSize,
  })
}

/**
 * hide lyric
 */
export const hideDesktopLyricView = async(): Promise<void> => {
  return LyricModule.hideDesktopLyric()
}


/**
 * play lyric
 * @param {Number} time play time
 * @returns {Promise} Promise
 */
export const play = async(time: number): Promise<void> => {
  return LyricModule.play(time)
}

/**
 * pause lyric
 */
export const pause = async(): Promise<void> => {
  return LyricModule.pause()
}

/**
 * set lyric
 * @param lyric lyric str
 * @param translation lyric translation
 * @param romalrc lyric translation
 */
export const setLyric = async(lyric: string, translation: string, romalrc: string): Promise<void> => {
  return LyricModule.setLyric(lyric, translation || '', romalrc || '')
}

export const setPlaybackRate = async(rate: number): Promise<void> => {
  return LyricModule.setPlaybackRate(rate)
}

/**
 * toggle show translation
 * @param isShowTranslation is show translation
 */
export const toggleTranslation = async(isShowTranslation: boolean): Promise<void> => {
  return LyricModule.toggleTranslation(isShowTranslation)
}

/**
 * toggle show roma lyric
 * @param isShowRoma is show roma lyric
 */
export const toggleRoma = async(isShowRoma: boolean): Promise<void> => {
  return LyricModule.toggleRoma(isShowRoma)
}

/**
 * toggle is lock lyric window
 * @param isLock is lock lyric window
 */
export const toggleLock = async(isLock: boolean): Promise<void> => {
  return LyricModule.toggleLock(isLock)
}

/**
 * set color
 * @param unplayColor
 * @param playedColor
 * @param shadowColor
 */
export const setColor = async(unplayColor: string, playedColor: string, shadowColor: string): Promise<void> => {
  return LyricModule.setColor(unplayColor, playedColor, shadowColor)
}

/**
 * set text alpha
 * @param alpha text alpha
 */
export const setAlpha = async(alpha: number): Promise<void> => {
  return LyricModule.setAlpha(getAlpha(alpha))
}

/**
 * set text size
 * @param size text size
 */
export const setTextSize = async(size: number): Promise<void> => {
  return LyricModule.setTextSize(getTextSize(size))
}

export const setOtherTextSize = async(size: number): Promise<void> => {
  return LyricModule.setOtherTextSize(getTextSize(size))
}

export const setShowToggleAnima = async(isShowToggleAnima: boolean): Promise<void> => {
  return LyricModule.setShowToggleAnima(isShowToggleAnima)
}

export const setSingleLine = async(isSingleLine: boolean): Promise<void> => {
  return LyricModule.setSingleLine(isSingleLine)
}

export const setPosition = async(x: number, y: number): Promise<void> => {
  return LyricModule.setPosition(x, y)
}

export const setMaxLineNum = async(maxLineNum: number): Promise<void> => {
  return LyricModule.setMaxLineNum(maxLineNum)
}

export const setWidth = async(width: number): Promise<void> => {
  return LyricModule.setWidth(width)
}

export const setLyricTextPosition = async(textX: LX.AppSetting['desktopLyric.textPosition.x'], textY: LX.AppSetting['desktopLyric.textPosition.y']): Promise<void> => {
  return LyricModule.setLyricTextPosition(textX.toUpperCase(), textY.toUpperCase())
}

export const checkOverlayPermission = async(): Promise<void> => {
  return LyricModule.checkOverlayPermission()
}

export const openOverlayPermissionActivity = async(): Promise<void> => {
  return LyricModule.openOverlayPermissionActivity()
}

// ===== [BYD] 悬浮窗增强：多屏投射 / 背景边框样式 / 字距 =====

export interface LyricDisplayInfo {
  id: number
  name: string
  width: number
  height: number
  density: number
  isDefault: boolean
  isPresentation: boolean
}

/** 枚举可投屏显示器（车机副驾屏/后排屏） */
export const getDisplays = async(): Promise<LyricDisplayInfo[]> => {
  return LyricModule.getDisplays()
}

/** 设置背景样式：背景色 / 圆角 / 边框色 / 边框宽 */
export const setBackgroundStyle = async(bgColor: string, bgRadius: number, borderColor: string, borderWidth: number): Promise<void> => {
  return LyricModule.setBackgroundStyle(bgColor, bgRadius, borderColor, borderWidth)
}

/** 设置字距 */
export const setLetterSpacing = async(letterSpacing: number): Promise<void> => {
  return LyricModule.setLetterSpacing(letterSpacing)
}

/** 切换投屏目标显示器 */
export const setTargetDisplay = async(displayId: number): Promise<void> => {
  return LyricModule.setTargetDisplay(displayId)
}

/** [BYD] 窗口高度百分比（0 = 按行数自适应） */
export const setHeight = async(height: number): Promise<void> => {
  return LyricModule.setHeight(height)
}

/** [BYD] 歌词样式：加粗 / 内置空心样式 / 空心大小 */
export const setLyricStyle = async(bold: boolean, hollowStyle: number, hollowSize: number): Promise<void> => {
  return LyricModule.setLyricStyle(bold, hollowStyle, hollowSize)
}

/** [BYD] 歌名/歌手 */
export const setMusicMeta = async(name: string, singer: string): Promise<void> => {
  return LyricModule.setMusicMeta(name, singer)
}

export interface LyricLineStylePayload {
  textSize: number
  textX: string
  letterSpacing: number
  bold: boolean
  hollowStyle: number
  hollowSize: number
  otherTextSize: number
  otherAlign: string
  otherLetterSpacing: number
  otherBold: boolean
  otherHollowStyle: number
  otherHollowSize: number
  titleTextSize: number
  titleAlign: string
  titleLetterSpacing: number
  titleBold: boolean
  titleHollowStyle: number
  titleHollowSize: number
}

/** [BYD] 当前行 / 其它行 / 标题行各自的文字样式。字号是磅，字距已经是 em。 */
export const setLineStyles = async(styles: LyricLineStylePayload): Promise<void> => {
  return LyricModule.setLineStyles(styles)
}

/** [BYD] 标题显示选项 */
export const setTitleOptions = async(
  mode: LX.AppSetting['desktopLyric.title.mode'],
  position: LX.AppSetting['desktopLyric.title.position'],
  align: LX.AppSetting['desktopLyric.title.align'],
  color: string,
): Promise<void> => {
  return LyricModule.setTitleOptions(mode, position, align, color)
}

export const onPositionChange = (handler: (position: { x: number, y: number }) => void): () => void => {
  // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
  const eventEmitter = new NativeEventEmitter(LyricModule)
  const eventListener = eventEmitter.addListener('set-position', event => {
    handler(event as { x: number, y: number })
  })

  return () => {
    eventListener.remove()
  }
}

export const onLyricLinePlay = (handler: (lineInfo: { text: string, extendedLyrics: string[] }) => void): () => void => {
  // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
  const eventEmitter = new NativeEventEmitter(LyricModule)
  const eventListener = eventEmitter.addListener('lyric-line-play', event => {
    handler(event as { text: string, extendedLyrics: string[] })
  })

  return () => {
    eventListener.remove()
  }
}
