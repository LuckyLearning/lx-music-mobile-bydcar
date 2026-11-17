// [BYD] 悬浮窗背景与边框样式：背景色/透明度/圆角 + 边框色/宽度。
// 背景色板只管 RGB，透明度独立滑块，两者组合出最终 bgColor 写入设置。
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

// 背景色板：只定义 RGB，透明度由独立滑块控制
const BG_COLORS = [
  'rgb(0, 0, 0)',
  'rgb(30, 30, 30)',
  'rgb(70, 70, 70)',
  'rgb(255, 255, 255)',
  'rgb(255, 122, 80)',
  'rgb(7, 197, 86)',
] as const

const BORDER_COLORS = [
  'rgba(255, 255, 255, 0.2)',
  'rgba(255, 255, 255, 0.5)',
  'rgba(0, 0, 0, 0.5)',
  'rgba(255, 122, 80, 0.8)',
  'rgba(7, 197, 86, 0.8)',
] as const

/** 提取颜色的 "r,g,b" 部分，用于色板选中态比对与透明度重组 */
const rgbKey = (color: string): string => {
  const m = /rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/.exec(color)
  return m ? `${m[1]},${m[2]},${m[3]}` : '0,0,0'
}

/** 用当前透明度重组背景色 */
const withOpacity = (color: string, opacity: number): string => {
  const m = /rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/.exec(color)
  const r = m ? m[1] : '0'
  const g = m ? m[2] : '0'
  const b = m ? m[3] : '0'
  return `rgba(${r}, ${g}, ${b}, ${(opacity / 100).toFixed(2)})`
}

/** 从现有 bgColor 读出透明度（0-100） */
const readOpacity = (color: string): number => {
  const m = /rgba\([\s\S]*?,\s*([\d.]+)\s*\)/.exec(color)
  return m ? Math.round(parseFloat(m[1]) * 100) : 100
}

const ColorDots = ({ colors, active, opacity, onChange }: {
  colors: readonly string[]
  active: string
  opacity: number
  onChange: (c: string) => void
}) => {
  return (
    <View style={styles.dots}>
      {colors.map(c => (
        <TouchableOpacity key={c} activeOpacity={0.6} onPress={() => { onChange(c) }} style={[styles.dotWrap, rgbKey(active) == rgbKey(c) && styles.dotActive]}>
          <View style={[styles.dot, { backgroundColor: withOpacity(c, opacity) }]} />
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
  const [opacitySliding, setOpacitySliding] = useState(readOpacity(bgColor))
  const [isOpacitySliding, setOpacitySlidingFlag] = useState(false)

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

  const onOpacityStart = useCallback<NonNullable<SliderProps['onSlidingStart']>>(() => { setOpacitySlidingFlag(true) }, [])
  const onOpacityChange = useCallback<NonNullable<SliderProps['onValueChange']>>(v => { setOpacitySliding(v) }, [])
  const onOpacityDone = useCallback<NonNullable<SliderProps['onSlidingComplete']>>(v => {
    setOpacitySlidingFlag(false)
    const newColor = withOpacity(bgColor, v)
    if (newColor != bgColor) apply({ bgColor: newColor })
  }, [bgColor, apply])

  const opacity = readOpacity(bgColor)

  return (
    <>
      <SubTitle title={t('setting_lyric_desktop_bg_color')}>
        <ColorDots colors={BG_COLORS} active={bgColor} opacity={opacity} onChange={c => { apply({ bgColor: withOpacity(c, opacity) }) }} />
      </SubTitle>
      <SubTitle title={t('setting_lyric_desktop_bg_opacity')}>
        <View style={styles.sliderRow}>
          <Text style={{ color: theme['c-primary-font'] }}>{isOpacitySliding ? opacitySliding : opacity}%</Text>
          <Slider
            minimumValue={0}
            maximumValue={100}
            step={1}
            value={isOpacitySliding ? opacitySliding : opacity}
            onSlidingStart={onOpacityStart}
            onValueChange={onOpacityChange}
            onSlidingComplete={onOpacityDone}
          />
        </View>
      </SubTitle>
      <SubTitle title={t('setting_lyric_desktop_bg_radius')}>
        <View style={styles.sliderRow}>
          <Text style={{ color: theme['c-primary-font'] }}>{isRadiusSliding ? radiusSliding : bgRadius}</Text>
          <Slider
            minimumValue={0}
            maximumValue={40}
            step={1}
            value={isRadiusSliding ? radiusSliding : bgRadius}
            onSlidingStart={onRadiusStart}
            onValueChange={onRadiusChange}
            onSlidingComplete={onRadiusDone}
          />
        </View>
      </SubTitle>
      <SubTitle title={t('setting_lyric_desktop_border_color')}>
        <ColorDots colors={BORDER_COLORS} active={borderColor} opacity={100} onChange={c => { apply({ borderColor: c }) }} />
      </SubTitle>
      <SubTitle title={t('setting_lyric_desktop_border_width')}>
        <View style={styles.sliderRow}>
          <Text style={{ color: theme['c-primary-font'] }}>{isBwSliding ? bwSliding : borderWidth}</Text>
          <Slider
            minimumValue={0}
            maximumValue={8}
            step={1}
            value={isBwSliding ? bwSliding : borderWidth}
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
