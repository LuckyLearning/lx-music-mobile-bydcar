// [BYD] 悬浮窗背景与边框：颜色用取色盘，透明度 / 圆角 / 边框宽度仍用滑块。
import { memo, useCallback, useState } from 'react'
import { View, StyleSheet } from 'react-native'

import SubTitle from '../../components/SubTitle'
import Slider, { type SliderProps } from '../../components/Slider'
import { useI18n } from '@/lang'
import { useSettingValue } from '@/store/setting/hook'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { setDesktopLyricBackground } from '@/core/desktopLyric'
import { updateSetting } from '@/core/common'
import { readAlphaPercent, setAlphaPercent } from '@/utils/colorValue'
import ColorValueField from './ColorValueField'

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
  const [opacitySliding, setOpacitySliding] = useState(readAlphaPercent(bgColor))
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
    const newColor = setAlphaPercent(bgColor, v)
    if (newColor && newColor != bgColor) apply({ bgColor: newColor })
  }, [bgColor, apply])

  const opacity = readAlphaPercent(bgColor)

  return (
    <>
      <SubTitle title={t('setting_lyric_desktop_bg_color')}>
        <ColorValueField title={t('setting_lyric_desktop_bg_color')} value={bgColor} onChange={c => { apply({ bgColor: c }) }} />
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
        <ColorValueField title={t('setting_lyric_desktop_border_color')} value={borderColor} onChange={c => { apply({ borderColor: c }) }} />
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
})
