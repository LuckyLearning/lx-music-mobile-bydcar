// [BYD] 窗口高度百分比：0 = 按行数自适应，10-100 = 占屏幕高度的百分比。
// 与百分比宽度、百分比位置配合，悬浮窗可在屏幕任意位置、任意大小。
import { memo, useCallback, useState } from 'react'
import { View } from 'react-native'

import SubTitle from '../../components/SubTitle'
import Slider, { type SliderProps } from '../../components/Slider'
import { useI18n } from '@/lang'
import { useSettingValue } from '@/store/setting/hook'
import { useTheme } from '@/store/theme/hook'
import { createStyle } from '@/utils/tools'
import Text from '@/components/common/Text'
import { setDesktopLyricHeight } from '@/core/desktopLyric'
import { updateSetting } from '@/core/common'

export default memo(() => {
  const t = useI18n()
  const height = useSettingValue('desktopLyric.height')
  const theme = useTheme()
  const [sliderSize, setSliderSize] = useState(height)
  const [isSliding, setSliding] = useState(false)
  const handleSlidingStart = useCallback<NonNullable<SliderProps['onSlidingStart']>>(() => {
    setSliding(true)
  }, [])
  const handleValueChange = useCallback<NonNullable<SliderProps['onValueChange']>>(value => {
    setSliderSize(value)
  }, [])
  const handleSlidingComplete = useCallback<NonNullable<SliderProps['onSlidingComplete']>>(value => {
    if (height == value) return
    void setDesktopLyricHeight(value).then(() => {
      updateSetting({ 'desktopLyric.height': value })
    }).finally(() => {
      setSliding(false)
    })
  }, [height])

  const label = isSliding ? sliderSize : height

  return (
    <SubTitle title={t('setting_lyric_desktop_view_height')}>
      <View style={styles.content}>
        <Text style={{ color: theme['c-primary-font'] }}>
          {label == 0 ? t('setting_lyric_desktop_view_height_auto') : label + '%'}
        </Text>
        <Slider
          minimumValue={0}
          maximumValue={100}
          onSlidingComplete={handleSlidingComplete}
          onValueChange={handleValueChange}
          onSlidingStart={handleSlidingStart}
          step={1}
          value={height}
        />
      </View>
    </SubTitle>
  )
})

const styles = createStyle({
  content: {
    flexGrow: 0,
    flexShrink: 1,
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
  },
})
