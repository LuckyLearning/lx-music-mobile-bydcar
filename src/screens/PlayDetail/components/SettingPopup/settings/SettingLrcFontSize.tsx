import { useEffect, useRef } from 'react'
import { TouchableOpacity, View } from 'react-native'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { useSettingValue } from '@/store/setting/hook'
import { updateSetting } from '@/core/common'
import { useI18n } from '@/lang'
import { createStyle } from '@/utils/tools'
import { MIN_LRC_FONT_PT, getDefaultLrcFontPt, normalizeLrcFontPt, stepLrcFontPt } from '../../../lrcFontSize'
import baseStyles from './style'

const REPEAT_DELAY_MS = 400
const REPEAT_INTERVAL_MS = 120

/** 竖屏播放页歌词字号：按磅加减，最小 1 磅，不设上限。 */
const LrcFontSize = ({ direction }: {
  direction: 'horizontal' | 'vertical'
}) => {
  const theme = useTheme()
  const settingKey = direction == 'horizontal' ? 'playDetail.horizontal.style.lrcFontSize' : 'playDetail.vertical.style.lrcFontSize'
  const defaultPt = getDefaultLrcFontPt(direction)
  const lrcFontSize = useSettingValue(settingKey)
  const shown = normalizeLrcFontPt(lrcFontSize, defaultPt)
  const canDecrease = shown > MIN_LRC_FONT_PT
  const t = useI18n()

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
    <View style={baseStyles.container}>
      <View style={styles.header}>
        <Text>{t('play_detail_setting_lrc_font_size')}</Text>
        {shown !== defaultPt
          ? (
              <TouchableOpacity
                accessibilityRole="button"
                accessibilityLabel={t('play_detail_setting_playback_rate_reset')}
                activeOpacity={0.6}
                onPress={() => { apply(defaultPt) }}
                style={{ ...styles.resetBtn, backgroundColor: theme['c-button-background'] }}
              >
                <Text size={12} color={theme['c-button-font']}>{t('play_detail_setting_playback_rate_reset')}</Text>
              </TouchableOpacity>
            )
          : null}
      </View>
      <View style={styles.row}>
        <TouchableOpacity
          accessibilityRole="button"
          accessibilityLabel={`${t('play_detail_setting_decrease')} ${t('play_detail_setting_lrc_font_size')}`}
          accessibilityState={{ disabled: !canDecrease }}
          activeOpacity={0.6}
          disabled={!canDecrease}
          delayLongPress={REPEAT_DELAY_MS}
          onPressIn={() => { longPressActiveRef.current = false }}
          onLongPress={() => { if (canDecrease) startRepeat(-1) }}
          onPressOut={stopRepeat}
          onPress={() => { if (canDecrease) handleTap(-1) }}
          style={{ ...styles.btn, borderColor: theme['c-primary'], opacity: canDecrease ? 1 : 0.35 }}
        >
          <Text size={18}>－</Text>
        </TouchableOpacity>
        <Text style={styles.value} color={theme['c-font-label']}>{shown} {t('setting_lyric_desktop_font_unit')}</Text>
        <TouchableOpacity
          accessibilityRole="button"
          accessibilityLabel={`${t('play_detail_setting_increase')} ${t('play_detail_setting_lrc_font_size')}`}
          activeOpacity={0.6}
          delayLongPress={REPEAT_DELAY_MS}
          onPressIn={() => { longPressActiveRef.current = false }}
          onLongPress={() => { startRepeat(1) }}
          onPressOut={stopRepeat}
          onPress={() => { handleTap(1) }}
          style={{ ...styles.btn, borderColor: theme['c-primary'] }}
        >
          <Text size={18}>＋</Text>
        </TouchableOpacity>
      </View>
    </View>
  )
}

const styles = createStyle({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginBottom: 8,
  },
  resetBtn: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 4,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
  },
  btn: {
    minWidth: 44,
    minHeight: 36,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderRadius: 6,
    alignItems: 'center',
    justifyContent: 'center',
  },
  value: {
    minWidth: 72,
    marginHorizontal: 12,
    textAlign: 'center',
  },
})

export default LrcFontSize
