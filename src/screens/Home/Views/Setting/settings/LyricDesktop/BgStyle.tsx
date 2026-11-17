// [BYD] 悬浮窗背景与边框样式：背景圆角 / 边框宽度 滑块 + 背景色 / 边框色 选择
import { memo, useCallback, useState } from 'react'
import { View, StyleSheet, TouchableOpacity } from 'react-native'

import SubTitle from '../../components/SubTitle'
import Slider, { type SliderProps } from '../../components/Slider'
import { useI18n } from '@/lang'
import { useSettingValue } from '@/store/setting/hook'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { setDesktopLyricBackground } from '@/core/desktopLyric'
import { updateSetting } from '@/core/common'

const BG_COLORS = [
  'rgba(0, 0, 0, 0.35)',
  'rgba(0, 0, 0, 0.6)',
  'rgba(0, 0, 0, 0.85)',
  'rgba(255, 255, 255, 0.25)',
  'rgba(255, 255, 255, 0.6)',
  'rgba(0, 0, 0, 0)',
] as const

const BORDER_COLORS = [
  'rgba(255, 255, 255, 0.2)',
  'rgba(255, 255, 255, 0.5)',
  'rgba(0, 0, 0, 0.5)',
  'rgba(255, 122, 80, 0.8)',
  'rgba(7, 197, 86, 0.8)',
] as const

const ColorDots = ({ colors, active, onChange }: {
  colors: readonly string[]
  active: string
  onChange: (c: string) => void
}) => {
  return (
    <View style={styles.dots}>
      {colors.map(c => (
        <TouchableOpacity key={c} activeOpacity={0.6} onPress={() => { onChange(c) }} style={[styles.dotWrap, active == c && styles.dotActive]}>
          <View style={[styles.dot, { backgroundColor: c }]} />
        </TouchableOpacity>
      ))}
    </View>
  )
}

export default memo(() => {
  const t = useI18n()
  const theme = useTheme()
  const bgColor = useSettingValue('desktopLyric.style.bgColor')
  const bgRadius = useSettingValue('desktopLyric.style.bgRadius')
  const borderColor = useSettingValue('desktopLyric.style.borderColor')
  const borderWidth = useSettingValue('desktopLyric.style.borderWidth')
  const [radiusSliding, setRadiusSliding] = useState(bgRadius)
  const [isRadiusSliding, setRadiusSlidingFlag] = useState(false)
  const [bwSliding, setBwSliding] = useState(borderWidth)
  const [isBwSliding, setBwSlidingFlag] = useState(false)

  const apply = useCallback((patch: Partial<{ bgColor: string, bgRadius: number, borderColor: string, borderWidth: number }>) => {
    void setDesktopLyricBackground(patch.bgColor, patch.bgRadius, patch.borderColor, patch.borderWidth).then(() => {
      const setting: Partial<LX.AppSetting> = {}
      if (patch.bgColor != null) setting['desktopLyric.style.bgColor'] = patch.bgColor
      if (patch.bgRadius != null) setting['desktopLyric.style.bgRadius'] = patch.bgRadius
      if (patch.borderColor != null) setting['desktopLyric.style.borderColor'] = patch.borderColor
      if (patch.borderWidth != null) setting['desktopLyric.style.borderWidth'] = patch.borderWidth
      updateSetting(setting)
    })
  }, [])

  const onRadiusStart = useCallback<NonNullable<SliderProps['onSlidingStart']>>(() => { setRadiusSlidingFlag(true) }, [])
  const onRadiusChange = useCallback<NonNullable<SliderProps['onValueChange']>>(v => { setRadiusSliding(v) }, [])
  const onRadiusDone = useCallback<NonNullable<SliderProps['onSlidingComplete']>>(v => {
    setRadiusSlidingFlag(false)
    if (v != bgRadius) apply({ bgRadius: v })
  }, [bgRadius, apply])

  const onBwStart = useCallback<NonNullable<SliderProps['onSlidingStart']>>(() => { setBwSlidingFlag(true) }, [])
  const onBwChange = useCallback<NonNullable<SliderProps['onValueChange']>>(v => { setBwSliding(v) }, [])
  const onBwDone = useCallback<NonNullable<SliderProps['onSlidingComplete']>>(v => {
    setBwSlidingFlag(false)
    if (v != borderWidth) apply({ borderWidth: v })
  }, [borderWidth, apply])

  return (
    <>
      <SubTitle title={t('setting_lyric_desktop_bg_color')}>
        <ColorDots colors={BG_COLORS} active={bgColor} onChange={c => { apply({ bgColor: c }) }} />
      </SubTitle>
      <SubTitle title={t('setting_lyric_desktop_bg_radius')}>
        <View style={styles.sliderRow}>
          <Text style={{ color: theme['c-primary-font'] }}>{isRadiusSliding ? radiusSliding : bgRadius}</Text>
          <Slider
            minimumValue={0}
            maximumValue={40}
            step={1}
            value={bgRadius}
            onSlidingStart={onRadiusStart}
            onValueChange={onRadiusChange}
            onSlidingComplete={onRadiusDone}
          />
        </View>
      </SubTitle>
      <SubTitle title={t('setting_lyric_desktop_border_color')}>
        <ColorDots colors={BORDER_COLORS} active={borderColor} onChange={c => { apply({ borderColor: c }) }} />
      </SubTitle>
      <SubTitle title={t('setting_lyric_desktop_border_width')}>
        <View style={styles.sliderRow}>
          <Text style={{ color: theme['c-primary-font'] }}>{isBwSliding ? bwSliding : borderWidth}</Text>
          <Slider
            minimumValue={0}
            maximumValue={8}
            step={1}
            value={borderWidth}
            onSlidingStart={onBwStart}
            onValueChange={onBwChange}
            onSlidingComplete={onBwDone}
          />
        </View>
      </SubTitle>
    </>
  )
})

const styles = StyleSheet.create({
  sliderRow: {
    flexGrow: 0,
    flexShrink: 1,
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
  },
  dots: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  dotWrap: {
    marginRight: 12,
    marginTop: 4,
    padding: 3,
    borderRadius: 6,
    borderWidth: 1.5,
    borderColor: 'transparent',
  },
  dotActive: {
    borderColor: '#ff7a50',
  },
  dot: {
    width: 22,
    height: 22,
    borderRadius: 4,
  },
})
