import type { I18n } from '@/lang/i18n'

declare global {
  namespace LX {
    type AddMusicLocationType = 'top' | 'bottom'

    interface AppSetting {
      version: string
      /**
       * 是否跟随系统切换亮暗主题
       */
      'common.isAutoTheme': boolean

      /**
       * 语言id
       */
      'common.langId': I18n['locale'] | null

      /**
       * api id
       */
      'common.apiSource': string

      /**
       * 音源名称类型，原名、别名
       */
      'common.sourceNameType': 'alias' | 'real'

      /**
       * 歌曲分享方式
       */
      'common.shareType': 'system' | 'clipboard'

      /**
       * 是否同意软件协议
       */
      'common.isAgreePact': boolean

      /**
       * 是否在键盘弹出时隐藏播放栏
       */
      'common.autoHidePlayBar': boolean

      /**
       * 抽屉组件弹出方向
       */
      'common.drawerLayoutPosition': 'left' | 'right'

      /**
       * 启用首页滑动
       */
      'common.homePageScroll': boolean

      /**
       * 允许通过底栏进度条调整进度
       */
      'common.allowProgressBarSeek': boolean

      /**
       * 是否显示返回按钮
       */
      'common.showBackBtn': boolean

      /**
       * 是否显示退出按钮
       */
      'common.showExitBtn': boolean

      /**
       * 使用系统文件选择器
       */
      'common.useSystemFileSelector': boolean

      /**
       * 总是保留状态栏高度
       */
      'common.alwaysKeepStatusbarHeight': boolean

      /**
       * 主题id
       */
      'theme.id': string

      /**
       * 亮色主题id
       */
      'theme.lightId': string

      /**
       * 暗色主题id
       */
      'theme.darkId': string

      /**
       * 隐藏黑色主题背景
       */
      'theme.hideBgDark': boolean

      /**
       * 动态背景
       */
      'theme.dynamicBg': boolean

      /**
       * 字体阴影
       */
      'theme.fontShadow': boolean

      /**
       * 启动时自动播放歌曲
       */
      'player.startupAutoPlay': boolean

      /**
       * 启动后打开歌曲详细界面
       */
      'player.startupPushPlayDetailScreen': boolean

      /**
       * 切歌模式
       */
      'player.togglePlayMethod': 'listLoop' | 'random' | 'list' | 'singleLoop' | 'none'

      /**
       * 优先播放的音质
       */
      'player.playQuality': LX.Quality

      /**
       * 启动软件时是否恢复上次播放进度
       */
      'player.isSavePlayTime': boolean

      /**
       * 音量大小
       */
      'player.volume': number

      /**
       * 播放速率
       */
      'player.playbackRate': number

      /**
       * 缓存大小设置 unit MiB
       */
      'player.cacheSize': string

      /**
       * 定时暂停播放-倒计时时间
       */
      'player.timeoutExit': string

      /**
       * 定时暂停播放-是否等待歌曲播放完毕再暂停
       */
      'player.timeoutExitPlayed': boolean

      /**
       * 点击相同列表内的歌曲切歌时是否清空已播放列表（随机模式下列表内所有歌曲会重新参与随机）
       */
      'player.isAutoCleanPlayedList': boolean

      /**
       * 其他应用播放声音时是否自动暂停
       */
      'player.isHandleAudioFocus': boolean

      /**
       * 是否启用音频卸载功能（这可以节省耗电量，没有播放异常问题不建议关闭）
       */
      'player.isEnableAudioOffload': boolean

      /**
       * 是否显示歌词翻译
       */
      'player.isShowLyricTranslation': boolean

      /**
       * 是否显示歌词罗马音
       */
      'player.isShowLyricRoma': boolean

      /**
       * 是否在通知栏显示歌曲图片
       */
      'player.isShowNotificationImage': boolean

      /**
       * 是否将歌词从简体转换为繁体
       */
      'player.isS2t': boolean

      /**
       * 是否启用蓝牙歌词
       */
      'player.isShowBluetoothLyric': boolean

      /**
       * 是否启用蓝牙完整歌词
       */
      'player.isShowBluetoothFullLyric': boolean

      /**
       * 横屏时使用车机播放页（左侧歌词、右侧方形封面、底部控制）。竖屏不生效。
       */
      'player.carLayout': boolean

      /**
       * 播放详情页-是否缩放当前播放的歌词行
       */
      // 'playDetail.isZoomActiveLrc': boolean

      /**
       * 播放详情页-是否允许通过歌词调整播放进度
       */
      // 'playDetail.isShowLyricProgressSetting': boolean

      /**
       * 播放详情页-歌词对齐方式
       */
      'playDetail.style.align': 'center' | 'left' | 'right'

      /**
       * 竖屏歌词字体大小
       */
      'playDetail.vertical.style.lrcFontSize': number

      /**
       * 横屏歌词字体大小
       */
      'playDetail.horizontal.style.lrcFontSize': number

      /**
       * 播放详情页-是否允许通过歌词调整播放进度
       */
      'playDetail.isShowLyricProgressSetting': boolean

      /**
       * 是否启用桌面歌词
       */
      'desktopLyric.enable': boolean

      /**
       * 是否锁定桌面歌词
       */
      'desktopLyric.isLock': boolean

      /**
       * 桌面歌词窗口宽度
       */
      'desktopLyric.width': number

      /**
       * 桌面歌词最大行数
       */
      'desktopLyric.maxLineNum': number

      /**
       * 桌面歌词是否使用单行显示
       */
      'desktopLyric.isSingleLine': boolean

      /**
       * 桌面歌词是否启用歌词切换动画
       */
      'desktopLyric.showToggleAnima': boolean

      /**
       * 桌面歌词窗口x坐标
       */
      'desktopLyric.position.x': number

      /**
       * 桌面歌词窗口y坐标
       */
      'desktopLyric.position.y': number

      /**
       * 歌词水平对齐方式
       */
      'desktopLyric.textPosition.x': 'left' | 'center' | 'right'

      /**
       * 歌词垂直对齐方式
       */
      'desktopLyric.textPosition.y': 'top' | 'center' | 'bottom'

      /**
       * 当前行字号，单位磅（1 磅 = 旧滑块数值 / 10 = 1sp）
       */
      'desktopLyric.style.fontSize': number

      /**
       * 字号已从旧滑块刻度迁移为磅。用来保证只除一次 10。
       */
      'desktopLyric.style.fontUnit': 'pt'

      /**
       * 桌面歌词字体透明度
       */
      'desktopLyric.style.opacity': number

      /**
       * 桌面歌词未播放字体颜色
       */
      'desktopLyric.style.lyricUnplayColor': string

      /**
        * 桌面歌词已播放字体颜色
        */
      'desktopLyric.style.lyricPlayedColor': string

      /**
        * 桌面歌词字体阴影颜色
        */
      'desktopLyric.style.lyricShadowColor': string

      /**
       * [BYD] 悬浮窗背景色
       */
      'desktopLyric.style.bgColor': string

      /**
       * [BYD] 悬浮窗背景圆角
       */
      'desktopLyric.style.bgRadius': number

      /**
       * [BYD] 悬浮窗边框颜色
       */
      'desktopLyric.style.borderColor': string

      /**
       * [BYD] 悬浮窗边框宽度
       */
      'desktopLyric.style.borderWidth': number

      /**
       * [BYD] 悬浮窗字距
       */
      'desktopLyric.style.letterSpacing': number

      /**
       * [BYD] 悬浮窗歌词加粗
       */
      'desktopLyric.style.bold': boolean

      /**
       * [BYD] 悬浮窗空心字样式：0=关 / 1=细描边空心 / 2=电影字幕空心
       */
      'desktopLyric.style.hollowStyle': 0 | 1 | 2

      /**
       * [BYD] 悬浮窗空心字大小（描边强度，当前行与其它行按比例区分）
       */
      'desktopLyric.style.hollowSize': number

      /**
       * [BYD] 悬浮窗其它行（非当前行）字号，单位磅
       */
      'desktopLyric.style.otherFontSize': number

      /**
       * [BYD] 其它行水平对齐（与当前行分开）
       */
      'desktopLyric.style.otherAlign': 'left' | 'center' | 'right'

      /**
       * [BYD] 其它行字距（滑块刻度，原生 em = 数值 / 100）
       */
      'desktopLyric.style.otherLetterSpacing': number

      /**
       * [BYD] 其它行加粗
       */
      'desktopLyric.style.otherBold': boolean

      /**
       * [BYD] 其它行空心字样式：0=关 / 1=细描边空心 / 2=电影字幕空心
       */
      'desktopLyric.style.otherHollowStyle': 0 | 1 | 2

      /**
       * [BYD] 其它行空心字大小（不再跟当前行按 0.75 捆绑）
       */
      'desktopLyric.style.otherHollowSize': number

      /**
       * [BYD] 悬浮窗标题显示：none / name / name_singer
       */
      'desktopLyric.title.mode': 'none' | 'name' | 'name_singer'

      /**
       * [BYD] 悬浮窗标题相对歌词位置
       */
      'desktopLyric.title.position': 'above' | 'below'

      /**
       * [BYD] 悬浮窗标题水平对齐
       */
      'desktopLyric.title.align': 'left' | 'center' | 'right'

      /**
       * [BYD] 悬浮窗标题颜色
       */
      'desktopLyric.title.color': string

      /**
       * [BYD] 标题行字号，单位磅
       */
      'desktopLyric.title.fontSize': number

      /**
       * [BYD] 标题行字距（滑块刻度，原生 em = 数值 / 100）
       */
      'desktopLyric.title.letterSpacing': number

      /**
       * [BYD] 标题行加粗
       */
      'desktopLyric.title.bold': boolean

      /**
       * [BYD] 标题行空心字样式：0=关 / 1=细描边空心 / 2=电影字幕空心
       */
      'desktopLyric.title.hollowStyle': 0 | 1 | 2

      /**
       * [BYD] 标题行空心字大小
       */
      'desktopLyric.title.hollowSize': number

      /**
       * [BYD] 悬浮窗窗口高度占屏幕高度百分比（0 表示按行数自适应）
       */
      'desktopLyric.height': number

      /**
       * [BYD] 悬浮窗投屏目标显示器 id（-1 表示主屏）
       */
      'desktopLyric.displayId': number

      /**
       * 是否显示热门搜索
       */
      'search.isShowHotSearch': boolean

      /**
       * 是否显示搜索历史
       */
      'search.isShowHistorySearch': boolean

      /**
       * 是否启用双击列表里的歌曲时自动切换到当前列表播放（仅对歌单、排行榜有效）
       */
      'list.isClickPlayList': boolean

      /**
       * 是否显示歌曲来源（仅对我的列表有效）
       */
      'list.isShowSource': boolean

      /**
       * 是否显示歌曲专辑名
       */
      'list.isShowAlbumName': boolean

      /**
       * 是否显示歌曲时长
       */
      'list.isShowInterval': boolean

      /**
       * 是否自动恢复列表滚动位置（仅对我的列表有效）
       */
      'list.isSaveScrollLocation': boolean

      /**
       * 添加歌曲到我的列表时的方式
       */
      'list.addMusicLocationType': AddMusicLocationType

      /**
       * 文件命名方式
       */
      'download.fileName': '歌名 - 歌手' | '歌手 - 歌名' | '歌名'

      /**
       * 是否启用同步
       */
      'sync.enable': boolean
    }
  }
}

