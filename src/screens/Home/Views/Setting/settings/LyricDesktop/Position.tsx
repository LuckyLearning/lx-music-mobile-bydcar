// [BYD] 悬浮窗位置：水平/垂直按屏幕百分比调整（0-100，窗口左上角基准）。
// 与百分比宽度/高度配合后，无需拖拽即可把悬浮窗放到屏幕任意位置；
// 拖拽悬浮窗仍然有效，松手后保存的同样是百分比坐标。
import { memo, useCallback, useRef, useState } from 'react'
import { View } from 'react-native'

import SubTitle from '../../components/SubTitle'
import Slider, { type SliderProps } from '../../components/Slider'
import { useI18n } from '@/lang'
import { useSettingValue } from '@/store/setting/hook'
import { useTheme } from '@/store/theme/hook'
import { createStyle } from '@/utils/tools'
import Text from '@/components/common/Text'
import { setDesktopLyricPositionPercent } from '@/core/desktopLyric'
import { updateSetting } from '@/core/common'

const usePositionSlider = (axis: 'x' | 'y') => {
  const x = useSettingValue('desktopLyric.position.x')
  const y = useSettingValue('desktopLyric.position.y')
  const value = axis === 'x' ? x : y
  const xRef = useRef(x)
  const yRef = useRef(y)
  xRef.current = x
  yRef.current = y
  const [sliderSize, setSliderSize] = useState(value)
  const [isSliding, setSliding] = useState(false)
  const slidingRef = useRef(false)

  const pushPosition = useCallback((v: number) => {
    const position = { x: axis === 'x' ? v : xRef.current, y: axis === 'y' ? v : yRef.current }
    void setDesktopLyricPositionPercent(position.x, position.y)
    return position
  }, [axis])

  const handleSlidingStart = useCallback<NonNullable<SliderProps['onSlidingStart']>>(() => {
    slidingRef.current = true
    setSliding(true)
  }, [])
  const handleValueChange = useCallback<NonNullable<SliderProps['onValueChange']>>(v => {
    setSliderSize(v)
    // 只在手指拖动时跟手，避免设置回写触发又一次定位
    if (!slidingRef.current) return
    pushPosition(v)
  }, [pushPosition])
  const handleSlidingComplete = useCallback<NonNullable<SliderProps['onSlidingComplete']>>(v => {
    slidingRef.current = false
    const position = pushPosition(v)
    void updateSetting({ 'desktopLyric.position.x': position.x, 'desktopLyric.position.y': position.y })
    setSliding(false)
  }, [pushPosition])

  return { value, sliderSize, isSliding, handleSlidingStart, handleValueChange, handleSlidingComplete }
}

const PositionSlider = ({ axis, title }: { axis: 'x' | 'y', title: string }) => {
  const theme = useTheme()
  const { value, sliderSize, isSliding, handleSlidingStart, handleValueChange, handleSlidingComplete } = usePositionSlider(axis)

  return (
    <SubTitle title={title}>
      <View style={styles.content}>
        <Text style={{ color: theme['c-primary-font'] }}>{Math.round(isSliding ? sliderSize : value)}%</Text>
        <Slider
          minimumValue={0}
          maximumValue={100}
          onSlidingComplete={handleSlidingComplete}
          onValueChange={handleValueChange}
          onSlidingStart={handleSlidingStart}
          step={1}
          value={isSliding ? sliderSize : value}
        />
      </View>
    </SubTitle>
  )
}

export const PositionX = memo(() => {
  const t = useI18n()
  return <PositionSlider axis="x" title={t('setting_lyric_desktop_position_x')} />
})

export const PositionY = memo(() => {
  const t = useI18n()
  return <PositionSlider axis="y" title={t('setting_lyric_desktop_position_y')} />
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
