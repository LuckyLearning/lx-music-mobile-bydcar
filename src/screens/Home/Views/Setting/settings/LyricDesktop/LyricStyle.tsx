// [BYD] 歌词文字样式：加粗 / 描边（颜色点选 + 宽度滑块）/ 空心字（电影字幕风格）
import { memo, useCallback, useState } from 'react'
import { View, StyleSheet, TouchableOpacity } from 'react-native'

import SubTitle from '../../components/SubTitle'
import Slider, { type SliderProps } from '../../components/Slider'
import CheckBoxItem from '../../components/CheckBoxItem'
import { useI18n } from '@/lang'
import { useSettingValue } from '@/store/setting/hook'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { setDesktopLyricStyle } from '@/core/desktopLyric'
import { updateSetting } from '@/core/common'

const STROKE_COLORS = [
  'rgba(0, 0, 0, 0.9)',
  'rgba(255, 255, 255, 0.95)',
  'rgba(40, 40, 40, 0.8)',
  'rgba(255, 122, 80, 0.9)',
  'rgba(7, 197, 86, 0.9)',
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
  const bold = useSettingValue('desktopLyric.style.bold')
  const strokeColor = useSettingValue('desktopLyric.style.strokeColor')
  const strokeWidth = useSettingValue('desktopLyric.style.strokeWidth')
  const hollow = useSettingValue('desktopLyric.style.hollow')
  const [widthSliding, setWidthSliding] = useState(strokeWidth)
  const [isWidthSliding, setWidthSlidingFlag] = useState(false)

  const apply = useCallback((patch: Partial<{ bold: boolean, strokeColor: string, strokeWidth: number, hollow: boolean }>) => {
    void setDesktopLyricStyle(patch.bold, patch.strokeColor, patch.strokeWidth, patch.hollow).then(() => {
      const setting: Partial<LX.AppSetting> = {}
      if (patch.bold != null) setting['desktopLyric.style.bold'] = patch.bold
      if (patch.strokeColor != null) setting['desktopLyric.style.strokeColor'] = patch.strokeColor
      if (patch.strokeWidth != null) setting['desktopLyric.style.strokeWidth'] = patch.strokeWidth
      if (patch.hollow != null) setting['desktopLyric.style.hollow'] = patch.hollow
      updateSetting(setting)
    })
  }, [])

  const onWidthStart = useCallback<NonNullable<SliderProps['onSlidingStart']>>(() => { setWidthSlidingFlag(true) }, [])
  const onWidthChange = useCallback<NonNullable<SliderProps['onValueChange']>>(v => { setWidthSliding(v) }, [])
  const onWidthDone = useCallback<NonNullable<SliderProps['onSlidingComplete']>>(v => {
    setWidthSlidingFlag(false)
    if (v != strokeWidth) apply({ strokeWidth: v })
  }, [strokeWidth, apply])

  return (
    <>
      <View style={styles.checkboxRow}>
        <CheckBoxItem check={bold} onChange={v => { apply({ bold: v }) }} label={t('setting_lyric_desktop_text_bold')} />
        <CheckBoxItem check={hollow} onChange={v => { apply({ hollow: v }) }} label={t('setting_lyric_desktop_hollow')} />
      </View>
      <SubTitle title={t('setting_lyric_desktop_stroke_color')}>
        <ColorDots colors={STROKE_COLORS} active={strokeColor} onChange={c => { apply({ strokeColor: c }) }} />
      </SubTitle>
      <SubTitle title={t('setting_lyric_desktop_stroke_width')}>
        <View style={styles.sliderRow}>
          <Text style={{ color: theme['c-primary-font'] }}>{isWidthSliding ? widthSliding : strokeWidth}</Text>
          <Slider
            minimumValue={0}
            maximumValue={10}
            step={1}
            value={strokeWidth}
            onSlidingStart={onWidthStart}
            onValueChange={onWidthChange}
            onSlidingComplete={onWidthDone}
          />
        </View>
      </SubTitle>
    </>
  )
})

const styles = StyleSheet.create({
  checkboxRow: {
    marginTop: 5,
    marginBottom: 15,
    flexDirection: 'row',
    flexWrap: 'wrap',
    columnGap: 24,
  },
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
