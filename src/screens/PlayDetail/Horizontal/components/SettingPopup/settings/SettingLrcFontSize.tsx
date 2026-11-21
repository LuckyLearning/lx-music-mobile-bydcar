import { useEffect, useRef } from 'react'
import { Pressable, View } from 'react-native'
import CarIcon from '@/components/car/CarIcon'
import { useSettingValue } from '@/store/setting/hook'
import { updateSetting } from '@/core/common'
import { useTheme } from '@/store/theme/hook'
import { useI18n } from '@/lang'
import SettingsText from '../Text'
import { px } from '../scale'
import { MIN_LRC_FONT_PT, getDefaultLrcFontPt, normalizeLrcFontPt, stepLrcFontPt } from '../../../../lrcFontSize'

const REPEAT_DELAY_MS = 400
const REPEAT_INTERVAL_MS = 120

/** 播放页横屏/车机歌词字号：按磅加减，最小 1 磅，不设上限。 */
export default ({ direction }: {
  direction: 'horizontal' | 'vertical'
}) => {
  const settingKey = direction == 'horizontal' ? 'playDetail.horizontal.style.lrcFontSize' : 'playDetail.vertical.style.lrcFontSize'
  const defaultPt = getDefaultLrcFontPt(direction)
  const lrcFontSize = useSettingValue(settingKey)
  const shown = normalizeLrcFontPt(lrcFontSize, defaultPt)
  const canDecrease = shown > MIN_LRC_FONT_PT
  const t = useI18n()
  const theme = useTheme()

  const valueRef = useRef(shown)
  valueRef.current = shown
  const repeatTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const longPressActiveRef = useRef(false)

  const stopRepeat = () => {
    if (repeatTimerRef.current != null) {
      clearInterval(repeatTimerRef.current)
      repeatTimerRef.current = null
    }
  }

  useEffect(() => stopRepeat, [])

  const apply = (next: number) => {
    const normalized = normalizeLrcFontPt(next, defaultPt)
    if (normalized === valueRef.current && lrcFontSize === normalized) return
    valueRef.current = normalized
    updateSetting({ [settingKey]: normalized })
  }

  const startRepeat = (delta: number) => {
    longPressActiveRef.current = true
    stopRepeat()
    const first = stepLrcFontPt(valueRef.current, delta, defaultPt)
    apply(first)
    if (delta < 0 && first <= MIN_LRC_FONT_PT) return
    repeatTimerRef.current = setInterval(() => {
      const next = stepLrcFontPt(valueRef.current, delta, defaultPt)
      apply(next)
      if (delta < 0 && next <= MIN_LRC_FONT_PT) stopRepeat()
    }, REPEAT_INTERVAL_MS)
  }

  const handleTap = (delta: number) => {
    if (longPressActiveRef.current) {
      longPressActiveRef.current = false
      return
    }
    apply(stepLrcFontPt(valueRef.current, delta, defaultPt))
  }

  return (
    <View style={{ minHeight: px(80), flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: px(16) }}>
      <View style={{ flexGrow: 1, flexShrink: 1, minWidth: px(160), flexDirection: 'row', alignItems: 'center', gap: px(12) }}>
        <SettingsText size={26} weight="500" lineHeight={1.4}>{t('play_detail_setting_lrc_font_size')}</SettingsText>
        {shown !== defaultPt
          ? (
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={t('play_detail_setting_playback_rate_reset')}
                onPress={() => { apply(defaultPt) }}
                style={({ pressed }) => ({ minHeight: px(48), borderRadius: px(14), paddingHorizontal: px(12), flexDirection: 'row', gap: px(6), alignItems: 'center', justifyContent: 'center', opacity: pressed ? 0.6 : 1 })}
              >
                <CarIcon name="lucide-rotate-ccw" size={18} color={theme['text-2']} />
                <SettingsText size={18} weight="500" color={theme['text-2']}>{t('play_detail_setting_playback_rate_reset')}</SettingsText>
              </Pressable>
            )
          : null}
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: px(16) }}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${t('play_detail_setting_decrease')} ${t('play_detail_setting_lrc_font_size')}`}
          accessibilityState={{ disabled: !canDecrease }}
          disabled={!canDecrease}
          delayLongPress={REPEAT_DELAY_MS}
          onPressIn={() => { longPressActiveRef.current = false }}
          onLongPress={() => { if (canDecrease) startRepeat(-1) }}
          onPressOut={stopRepeat}
          onPress={() => { if (canDecrease) handleTap(-1) }}
          style={({ pressed }) => ({
            width: px(64),
            height: px(64),
            borderRadius: px(32),
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: theme.glass,
            opacity: !canDecrease ? 0.35 : pressed ? 0.6 : 1,
          })}
        >
          <CarIcon name="lucide-minus" size={26} color={theme['text-2']} />
        </Pressable>
        <SettingsText size={32} weight="600" num lineHeight={1.2} style={{ minWidth: px(120), textAlign: 'center' }}>
          {`${shown} ${t('setting_lyric_desktop_font_unit')}`}
        </SettingsText>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${t('play_detail_setting_increase')} ${t('play_detail_setting_lrc_font_size')}`}
          delayLongPress={REPEAT_DELAY_MS}
          onPressIn={() => { longPressActiveRef.current = false }}
          onLongPress={() => { startRepeat(1) }}
          onPressOut={stopRepeat}
          onPress={() => { handleTap(1) }}
          style={({ pressed }) => ({
            width: px(64),
            height: px(64),
            borderRadius: px(32),
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: theme.glass,
            opacity: pressed ? 0.6 : 1,
          })}
        >
          <CarIcon name="lucide-plus" size={26} color={theme['text-2']} />
        </Pressable>
      </View>
    </View>
  )
}
