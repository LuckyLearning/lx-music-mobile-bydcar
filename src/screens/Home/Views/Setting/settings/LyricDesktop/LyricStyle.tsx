// [BYD] 歌词文字样式：加粗 / 内置空心字 2 套 + 大小 / 其它行字号
import { memo, useCallback, useMemo, useState } from 'react'
import { View, StyleSheet } from 'react-native'

import SubTitle from '../../components/SubTitle'
import Slider, { type SliderProps } from '../../components/Slider'
import CheckBoxItem from '../../components/CheckBoxItem'
import CheckBox from '@/components/common/CheckBox'
import { useI18n } from '@/lang'
import { useSettingValue } from '@/store/setting/hook'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { setDesktopLyricStyle, setDesktopLyricOtherTextSize } from '@/core/desktopLyric'
import { updateSetting } from '@/core/common'

type HollowStyle = LX.AppSetting['desktopLyric.style.hollowStyle']

const HOLLOW_LIST = [0, 1, 2] as const

export default memo(() => {
  const t = useI18n()
  const theme = useTheme()
  const bold = useSettingValue('desktopLyric.style.bold')
  const hollowStyle = useSettingValue('desktopLyric.style.hollowStyle')
  const hollowSize = useSettingValue('desktopLyric.style.hollowSize')
  const otherFontSize = useSettingValue('desktopLyric.style.otherFontSize')
  const [sizeSliding, setSizeSliding] = useState(hollowSize)
  const [isSizeSliding, setSizeSlidingFlag] = useState(false)
  const [otherSliding, setOtherSliding] = useState(otherFontSize)
  const [isOtherSliding, setOtherSlidingFlag] = useState(false)

  const hollowLabels = useMemo(() => ({
    0: t('setting_lyric_desktop_hollow_off'),
    1: t('setting_lyric_desktop_hollow_style1'),
    2: t('setting_lyric_desktop_hollow_style2'),
  }), [t])

  const applyStyle = useCallback((patch: Partial<{ bold: boolean, hollowStyle: HollowStyle, hollowSize: number }>) => {
    const nextBold = patch.bold ?? bold
    const nextHollow = patch.hollowStyle ?? hollowStyle
    const nextSize = patch.hollowSize ?? hollowSize
    void setDesktopLyricStyle(nextBold, nextHollow, nextSize).then(() => {
      const setting: Partial<LX.AppSetting> = {}
      if (patch.bold != null) setting['desktopLyric.style.bold'] = patch.bold
      if (patch.hollowStyle != null) setting['desktopLyric.style.hollowStyle'] = patch.hollowStyle
      if (patch.hollowSize != null) setting['desktopLyric.style.hollowSize'] = patch.hollowSize
      updateSetting(setting)
    })
  }, [bold, hollowStyle, hollowSize])

  const onSizeStart = useCallback<NonNullable<SliderProps['onSlidingStart']>>(() => { setSizeSlidingFlag(true) }, [])
  const onSizeChange = useCallback<NonNullable<SliderProps['onValueChange']>>(v => { setSizeSliding(v) }, [])
  const onSizeDone = useCallback<NonNullable<SliderProps['onSlidingComplete']>>(v => {
    setSizeSlidingFlag(false)
    if (v != hollowSize) applyStyle({ hollowSize: v })
  }, [hollowSize, applyStyle])

  const onOtherStart = useCallback<NonNullable<SliderProps['onSlidingStart']>>(() => { setOtherSlidingFlag(true) }, [])
  const onOtherChange = useCallback<NonNullable<SliderProps['onValueChange']>>(v => { setOtherSliding(v) }, [])
  const onOtherDone = useCallback<NonNullable<SliderProps['onSlidingComplete']>>(v => {
    setOtherSlidingFlag(false)
    if (v == otherFontSize) return
    void setDesktopLyricOtherTextSize(v).then(() => {
      updateSetting({ 'desktopLyric.style.otherFontSize': v })
    })
  }, [otherFontSize])

  return (
    <>
      <View style={styles.checkboxRow}>
        <CheckBoxItem check={bold} onChange={v => { applyStyle({ bold: v }) }} label={t('setting_lyric_desktop_text_bold')} />
      </View>
      <SubTitle title={t('setting_lyric_desktop_hollow')}>
        <View style={styles.list}>
          {HOLLOW_LIST.map(id => (
            <CheckBox
              key={id}
              marginBottom={3}
              check={hollowStyle == id}
              label={hollowLabels[id]}
              onChange={() => { applyStyle({ hollowStyle: id }) }}
              need
            />
          ))}
        </View>
      </SubTitle>
      {hollowStyle > 0 ? (
        <SubTitle title={t('setting_lyric_desktop_hollow_size')}>
          <View style={styles.sliderRow}>
            <Text style={{ color: theme['c-primary-font'] }}>{isSizeSliding ? sizeSliding : hollowSize}</Text>
            <Slider
              minimumValue={1}
              maximumValue={12}
              step={1}
              value={hollowSize}
              onSlidingStart={onSizeStart}
              onValueChange={onSizeChange}
              onSlidingComplete={onSizeDone}
            />
          </View>
        </SubTitle>
      ) : null}
      <SubTitle title={t('setting_lyric_desktop_other_text_size')}>
        <View style={styles.sliderRow}>
          <Text style={{ color: theme['c-primary-font'] }}>{isOtherSliding ? otherSliding : otherFontSize}</Text>
          <Slider
            minimumValue={100}
            maximumValue={300}
            step={5}
            value={otherFontSize}
            onSlidingStart={onOtherStart}
            onValueChange={onOtherChange}
            onSlidingComplete={onOtherDone}
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
  list: {
    flexGrow: 0,
    flexShrink: 1,
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
})
