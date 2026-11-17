// [BYD] 悬浮窗字距滑块
import { memo, useCallback, useState } from 'react'
import { View, StyleSheet } from 'react-native'

import SubTitle from '../../components/SubTitle'
import Slider, { type SliderProps } from '../../components/Slider'
import { useI18n } from '@/lang'
import { useSettingValue } from '@/store/setting/hook'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { setDesktopLyricLetterSpacing } from '@/core/desktopLyric'
import { updateSetting } from '@/core/common'

// 原生 letterSpacing 为 em 倍率，滑块用 0-50 表示 0-0.5em
export default memo(() => {
  const t = useI18n()
  const theme = useTheme()
  const letterSpacing = useSettingValue('desktopLyric.style.letterSpacing')
  const [sliding, setSliding] = useState(letterSpacing)
  const [isSliding, setSlidingFlag] = useState(false)

  const onStart = useCallback<NonNullable<SliderProps['onSlidingStart']>>(() => { setSlidingFlag(true) }, [])
  const onChange = useCallback<NonNullable<SliderProps['onValueChange']>>(v => { setSliding(v) }, [])
  const onDone = useCallback<NonNullable<SliderProps['onSlidingComplete']>>(v => {
    setSlidingFlag(false)
    if (v == letterSpacing) return
    void setDesktopLyricLetterSpacing(v / 100).then(() => {
      updateSetting({ 'desktopLyric.style.letterSpacing': v })
    })
  }, [letterSpacing])

  return (
    <SubTitle title={t('setting_lyric_desktop_letter_spacing')}>
      <View style={styles.sliderRow}>
        <Text style={{ color: theme['c-primary-font'] }}>{isSliding ? sliding : letterSpacing}</Text>
        <Slider
          minimumValue={0}
          maximumValue={50}
          step={1}
          value={isSliding ? sliding : letterSpacing}
          onSlidingStart={onStart}
          onValueChange={onChange}
          onSlidingComplete={onDone}
        />
      </View>
    </SubTitle>
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
