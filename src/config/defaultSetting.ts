const defaultSetting: LX.AppSetting = {
  version: '2.1',
  'common.isAutoTheme': false,
  'common.langId': null,
  'common.apiSource': '',
  'common.sourceNameType': 'alias',
  'common.shareType': 'system',
  'common.isAgreePact': false,
  'common.autoHidePlayBar': true,
  'common.drawerLayoutPosition': 'left',
  'common.homePageScroll': true,
  'common.allowProgressBarSeek': true,
  'common.showBackBtn': false,
  'common.showExitBtn': true,
  'common.useSystemFileSelector': true,
  'common.alwaysKeepStatusbarHeight': false,

  'player.startupAutoPlay': false,
  'player.startupPushPlayDetailScreen': false,
  'player.togglePlayMethod': 'listLoop',
  'player.playQuality': '128k',
  'player.isSavePlayTime': false,
  'player.volume': 1,
  'player.playbackRate': 1,
  'player.cacheSize': '1024',
  'player.timeoutExit': '',
  'player.timeoutExitPlayed': true,
  'player.isAutoCleanPlayedList': false,
  'player.isHandleAudioFocus': true,
  'player.isEnableAudioOffload': false,
  'player.isShowLyricTranslation': false,
  'player.isShowLyricRoma': false,
  'player.isShowNotificationImage': true,
  'player.isS2t': false,
  'player.isShowBluetoothLyric': false,
  'player.isShowBluetoothFullLyric': false,

  // 'playDetail.isZoomActiveLrc': false,
  // 'playDetail.isShowLyricProgressSetting': false,
  'playDetail.style.align': 'left',
  'playDetail.vertical.style.lrcFontSize': 210,
  'playDetail.horizontal.style.lrcFontSize': 220,
  'playDetail.isShowLyricProgressSetting': false,

  'desktopLyric.enable': false,
  'desktopLyric.isLock': false,
  'desktopLyric.width': 100,
  'desktopLyric.maxLineNum': 5,
  'desktopLyric.isSingleLine': false,
  'desktopLyric.showToggleAnima': true,
  'desktopLyric.position.x': 0,
  'desktopLyric.position.y': 0,
  'desktopLyric.textPosition.x': 'left',
  'desktopLyric.textPosition.y': 'top',
  // 字号单位是磅。18 磅对应旧滑块 180（原生 18sp），升级时不会突然变大或变小。
  'desktopLyric.style.fontSize': 18,
  'desktopLyric.style.fontUnit': 'pt',
  'desktopLyric.style.opacity': 100,
  // 当前行 / 其它行默认色区分
  'desktopLyric.style.lyricUnplayColor': 'rgba(255, 255, 255, 0.55)',
  'desktopLyric.style.lyricPlayedColor': 'rgba(7, 197, 86, 1)',
  'desktopLyric.style.lyricShadowColor': 'rgba(0, 0, 0, 0.6)',
  // [BYD] 悬浮窗自定义样式（背景/边框/字距/空心/标题/投屏）
  'desktopLyric.style.bgColor': 'rgba(0, 0, 0, 0.35)',
  'desktopLyric.style.bgRadius': 12,
  'desktopLyric.style.borderColor': 'rgba(255, 255, 255, 0.2)',
  'desktopLyric.style.borderWidth': 0,
  'desktopLyric.style.letterSpacing': 0,
  'desktopLyric.style.bold': false,
  'desktopLyric.style.hollowStyle': 0,
  'desktopLyric.style.hollowSize': 4,
  'desktopLyric.style.otherFontSize': 14,
  // 其它行以前跟当前行共用这些项；空心大小按旧绘制的 0.75 记下来，打开空心时粗细不变。
  'desktopLyric.style.otherAlign': 'left',
  'desktopLyric.style.otherLetterSpacing': 0,
  'desktopLyric.style.otherBold': false,
  'desktopLyric.style.otherHollowStyle': 0,
  'desktopLyric.style.otherHollowSize': 3,
  'desktopLyric.title.mode': 'none',
  'desktopLyric.title.position': 'above',
  'desktopLyric.title.align': 'left',
  'desktopLyric.title.color': 'rgba(255, 255, 255, 0.85)',
  // 标题以前是其它行的 0.95 倍、字距一半、并且始终加粗。
  'desktopLyric.title.fontSize': 13,
  'desktopLyric.title.letterSpacing': 0,
  'desktopLyric.title.bold': true,
  'desktopLyric.title.hollowStyle': 0,
  'desktopLyric.title.hollowSize': 3,
  // [BYD] 窗口高度占屏幕高度百分比（0 = 按行数自适应）
  'desktopLyric.height': 0,
  'desktopLyric.displayId': -1,

  'search.isShowHotSearch': false,
  'search.isShowHistorySearch': false,

  'list.isClickPlayList': false,
  'list.isShowSource': true,
  'list.isShowAlbumName': false,
  'list.isShowInterval': true,
  'list.isSaveScrollLocation': true,
  'list.addMusicLocationType': 'top',

  'download.fileName': '歌名 - 歌手',

  'sync.enable': false,

  // [BYD] 车机 fork 默认主题改为夜曲/白昼（car 组件按设计稿配色渲染）
  // 保留 isAutoTheme/langId/新年皮肤的上游原逻辑，手机竖屏行为不变
  'theme.id': 'nocturne',
  'theme.lightId': 'daylight',
  'theme.darkId': 'nocturne',
  // [/BYD]
  'theme.hideBgDark': false,
  'theme.dynamicBg': false,
  'theme.fontShadow': false,
}


// 使用新年皮肤
if (new Date().getMonth() < 2) {
  defaultSetting['theme.id'] = 'happy_new_year'
  defaultSetting['desktopLyric.style.lyricPlayedColor'] = 'rgba(255, 18, 34, 1)'
}

export default defaultSetting
