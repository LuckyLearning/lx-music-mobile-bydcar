import { memo, useCallback } from 'react'
import { TouchableOpacity, View } from 'react-native'
import { useSettingValue } from '@/store/setting/hook'
import { useTheme } from '@/store/theme/hook'
import { useIsPlay, useProgress } from '@/store/player/hook'
import { updateSetting } from '@/core/common'
import { MUSIC_TOGGLE_MODE_LIST } from '@/config/constant'
import { playNext, playPrev, togglePlay, collectMusic, uncollectMusic } from '@/core/player/player'
import { useI18n } from '@/lang'
import { toast } from '@/utils/tools'
import { px } from '@/utils/pixelRatio'
import useWindowSize from '@/utils/hooks/useWindowSize'
import { CarText, SeekBar, useCurrentMusicLoved } from '@/components/car'
import { Icon } from '@/components/common/Icon'
import { useBufferProgress } from '@/plugins/player'

type PlayMode = LX.AppSetting['player.togglePlayMethod']

const MODE_LABEL = {
  listLoop: 'play_list_loop',
  random: 'play_list_random',
  list: 'play_list_order',
  singleLoop: 'play_single_loop',
  none: 'play_single',
} as const

const MODE_ICON = {
  listLoop: 'list-loop',
  random: 'list-random',
  list: 'list-order',
  singleLoop: 'single-loop',
  none: 'single',
} as const

const PLAY_SIZE = 88
const SKIP_SIZE = 72
const SIDE_SIZE = 64

const ControlButton = ({ icon, size, iconSize, color, background, label, onPress }: {
  icon: string
  size: number
  iconSize: number
  color: string
  background?: string
  label: string
  onPress: () => void
}) => {
  return (
    <TouchableOpacity
      accessibilityRole="button"
      accessibilityLabel={label}
      activeOpacity={0.6}
      onPress={onPress}
      style={{
        width: size,
        height: size,
        borderRadius: size / 2,
        backgroundColor: background,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Icon name={icon} rawSize={iconSize} color={color} />
    </TouchableOpacity>
  )
}

/** 底部只放进度和播放控制，歌名歌手在左侧歌词栏。尺寸用 dp，保证车机上够得到。 */
export default memo(({ onInteraction }: { onInteraction: () => void }) => {
  const theme = useTheme()
  const t = useI18n()
  useWindowSize()
  const isPlay = useIsPlay()
  const loved = useCurrentMusicLoved()
  const playMode = useSettingValue('player.togglePlayMethod') as PlayMode
  const { progress, maxPlayTime, nowPlayTimeStr, maxPlayTimeStr } = useProgress()
  const buffered = useBufferProgress()

  const nextPlayMode = useCallback(() => {
    onInteraction()
    let index = MUSIC_TOGGLE_MODE_LIST.indexOf(playMode)
    if (++index >= MUSIC_TOGGLE_MODE_LIST.length) index = 0
    const mode = MUSIC_TOGGLE_MODE_LIST[index]
    updateSetting({ 'player.togglePlayMethod': mode })
    toast(t(MODE_LABEL[mode]))
  }, [onInteraction, playMode, t])

  const toggleLoved = () => {
    onInteraction()
    if (loved) uncollectMusic()
    else collectMusic()
  }

  return (
    <View style={{ paddingHorizontal: px(48), paddingTop: px(8), paddingBottom: px(20) }}>
      <SeekBar
        progress={progress}
        duration={maxPlayTime}
        buffered={buffered}
        barHeight={8}
        touchHeight={96}
        color={theme['text-1']}
        onInteraction={onInteraction}
      />
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: px(4) }}>
        <CarText size={20} weight="500" num color={theme['text-3']}>{nowPlayTimeStr}</CarText>
        <CarText size={20} weight="500" num color={theme['text-3']}>{maxPlayTimeStr}</CarText>
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-evenly', marginTop: px(12) }}>
        <ControlButton
          icon={MODE_ICON[playMode]}
          size={SIDE_SIZE}
          iconSize={32}
          color={theme['text-1']}
          label={t(MODE_LABEL[playMode])}
          onPress={nextPlayMode}
        />
        <ControlButton
          icon="ph-skip-back-fill"
          size={SKIP_SIZE}
          iconSize={36}
          color={theme['text-1']}
          label={t('play_prev')}
          onPress={() => {
            onInteraction()
            void playPrev()
          }}
        />
        <ControlButton
          icon={isPlay ? 'ph-pause-fill' : 'ph-play-fill'}
          size={PLAY_SIZE}
          iconSize={40}
          color={theme['on-primary']}
          background={theme['text-1']}
          label={t(isPlay ? 'pause' : 'play')}
          onPress={() => {
            onInteraction()
            togglePlay()
          }}
        />
        <ControlButton
          icon="ph-skip-forward-fill"
          size={SKIP_SIZE}
          iconSize={36}
          color={theme['text-1']}
          label={t('play_next')}
          onPress={() => {
            onInteraction()
            void playNext()
          }}
        />
        <ControlButton
          icon={loved ? 'ph-heart-fill' : 'ph-heart'}
          size={SIDE_SIZE}
          iconSize={32}
          color={loved ? theme.accent : theme['text-2']}
          label={t('collect')}
          onPress={toggleLoved}
        />
      </View>
    </View>
  )
})
