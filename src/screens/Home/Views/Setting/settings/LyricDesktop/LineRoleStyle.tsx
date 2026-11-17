// [BYD] 一行歌词的独立样式：颜色、字号、对齐、字距、加粗、空心。当前行 / 其它行 / 标题行各用各的。
import { memo, useCallback, useMemo, useRef, useState } from 'react'
import { View } from 'react-native'

import SubTitle from '../../components/SubTitle'
import Slider, { type SliderProps } from '../../components/Slider'
import CheckBox from '@/components/common/CheckBox'
import CheckBoxItem from '../../components/CheckBoxItem'
import { useI18n } from '@/lang'
import { useSettingValue } from '@/store/setting/hook'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { createStyle } from '@/utils/tools'
import { updateSetting } from '@/core/common'
import { pushDesktopLyricLineStyles, setDesktopLyricColor, setDesktopLyricTitleOptions, type LyricRoleStylePatch } from '@/core/desktopLyric'
import FontSizeStepper from './FontSizeStepper'
import ColorValueField from './ColorValueField'

type Role = 'current' | 'other' | 'title'
type Align = 'left' | 'center' | 'right'
type Hollow = 0 | 1 | 2

const ALIGNS = ['left', 'center', 'right'] as const
const HOLLOWS = [0, 1, 2] as const

const settingPatch = (role: Role, patch: LyricRoleStylePatch): Partial<LX.AppSetting> => {
  const setting: Partial<LX.AppSetting> = {}
  if (role == 'current') {
    if (patch.fontSize != null) setting['desktopLyric.style.fontSize'] = patch.fontSize
    if (patch.align != null) setting['desktopLyric.textPosition.x'] = patch.align
    if (patch.letterSpacing != null) setting['desktopLyric.style.letterSpacing'] = patch.letterSpacing
    if (patch.bold != null) setting['desktopLyric.style.bold'] = patch.bold
    if (patch.hollowStyle != null) setting['desktopLyric.style.hollowStyle'] = patch.hollowStyle
    if (patch.hollowSize != null) setting['desktopLyric.style.hollowSize'] = patch.hollowSize
  } else if (role == 'other') {
    if (patch.otherFontSize != null) setting['desktopLyric.style.otherFontSize'] = patch.otherFontSize
    if (patch.otherAlign != null) setting['desktopLyric.style.otherAlign'] = patch.otherAlign
    if (patch.otherLetterSpacing != null) setting['desktopLyric.style.otherLetterSpacing'] = patch.otherLetterSpacing
    if (patch.otherBold != null) setting['desktopLyric.style.otherBold'] = patch.otherBold
    if (patch.otherHollowStyle != null) setting['desktopLyric.style.otherHollowStyle'] = patch.otherHollowStyle
    if (patch.otherHollowSize != null) setting['desktopLyric.style.otherHollowSize'] = patch.otherHollowSize
  } else {
    if (patch.titleFontSize != null) setting['desktopLyric.title.fontSize'] = patch.titleFontSize
    if (patch.titleAlign != null) setting['desktopLyric.title.align'] = patch.titleAlign
    if (patch.titleLetterSpacing != null) setting['desktopLyric.title.letterSpacing'] = patch.titleLetterSpacing
    if (patch.titleBold != null) setting['desktopLyric.title.bold'] = patch.titleBold
    if (patch.titleHollowStyle != null) setting['desktopLyric.title.hollowStyle'] = patch.titleHollowStyle
    if (patch.titleHollowSize != null) setting['desktopLyric.title.hollowSize'] = patch.titleHollowSize
  }
  return setting
}

export default memo(({ role }: { role: Role }) => {
  const t = useI18n()
  const theme = useTheme()
  const currentSize = useSettingValue('desktopLyric.style.fontSize')
  const otherSize = useSettingValue('desktopLyric.style.otherFontSize')
  const titleSize = useSettingValue('desktopLyric.title.fontSize')
  const currentAlign = useSettingValue('desktopLyric.textPosition.x')
  const otherAlign = useSettingValue('desktopLyric.style.otherAlign')
  const titleAlign = useSettingValue('desktopLyric.title.align')
  const currentLetter = useSettingValue('desktopLyric.style.letterSpacing')
  const otherLetter = useSettingValue('desktopLyric.style.otherLetterSpacing')
  const titleLetter = useSettingValue('desktopLyric.title.letterSpacing')
  const currentBold = useSettingValue('desktopLyric.style.bold')
  const otherBold = useSettingValue('desktopLyric.style.otherBold')
  const titleBold = useSettingValue('desktopLyric.title.bold')
  const currentHollow = useSettingValue('desktopLyric.style.hollowStyle')
  const otherHollow = useSettingValue('desktopLyric.style.otherHollowStyle')
  const titleHollow = useSettingValue('desktopLyric.title.hollowStyle')
  const currentHollowSize = useSettingValue('desktopLyric.style.hollowSize')
  const otherHollowSize = useSettingValue('desktopLyric.style.otherHollowSize')
  const titleHollowSize = useSettingValue('desktopLyric.title.hollowSize')
  const playedColor = useSettingValue('desktopLyric.style.lyricPlayedColor')
  const unplayColor = useSettingValue('desktopLyric.style.lyricUnplayColor')
  const shadowColor = useSettingValue('desktopLyric.style.lyricShadowColor')
  const titleColor = useSettingValue('desktopLyric.title.color')

  const fontSize = role == 'current' ? currentSize : role == 'other' ? otherSize : titleSize
  const align = role == 'current' ? currentAlign : role == 'other' ? otherAlign : titleAlign
  const letter = role == 'current' ? currentLetter : role == 'other' ? otherLetter : titleLetter
  const bold = role == 'current' ? currentBold : role == 'other' ? otherBold : titleBold
  const hollow = role == 'current' ? currentHollow : role == 'other' ? otherHollow : titleHollow
  const hollowSize = role == 'current' ? currentHollowSize : role == 'other' ? otherHollowSize : titleHollowSize
  const color = role == 'current' ? playedColor : role == 'other' ? unplayColor : titleColor
  const colorTitle = role == 'current'
    ? t('setting_lyric_desktop_theme_current')
    : role == 'other'
      ? t('setting_lyric_desktop_theme_other')
      : t('setting_lyric_desktop_title_color')

  const [letterSliding, setLetterSliding] = useState(letter)
  const [isLetterSliding, setLetterSlidingFlag] = useState(false)
  const [sizeSliding, setSizeSliding] = useState(hollowSize)
  const [isSizeSliding, setSizeSlidingFlag] = useState(false)

  const alignList = useMemo(() => ALIGNS.map(id => ({ id, name: t(`setting_lyric_desktop_text_x_${id}`) })), [t])
  const hollowLabels = useMemo(() => ({
    0: t('setting_lyric_desktop_hollow_off'),
    1: t('setting_lyric_desktop_hollow_style1'),
    2: t('setting_lyric_desktop_hollow_style2'),
  }), [t])

  // 连续点加减时先写入设置，再按顺序把当前设置推到悬浮窗，避免后一次被先返回的旧值盖掉。
  const pending = useRef(Promise.resolve())
  const enqueue = useCallback((task: () => Promise<unknown>) => {
    pending.current = pending.current.then(task).then(() => undefined, () => undefined)
  }, [])

  const commitStyle = useCallback((patch: LyricRoleStylePatch) => {
    updateSetting(settingPatch(role, patch))
    enqueue(() => pushDesktopLyricLineStyles())
  }, [role, enqueue])

  const onFontSize = useCallback((next: number) => {
    const size = Math.max(1, Math.round(next))
    if (role == 'current') commitStyle({ fontSize: size })
    else if (role == 'other') commitStyle({ otherFontSize: size })
    else commitStyle({ titleFontSize: size })
  }, [role, commitStyle])

  const onAlign = useCallback((id: Align) => {
    if (role == 'current') commitStyle({ align: id })
    else if (role == 'other') commitStyle({ otherAlign: id })
    else commitStyle({ titleAlign: id })
  }, [role, commitStyle])

  const onBold = useCallback((next: boolean) => {
    if (role == 'current') commitStyle({ bold: next })
    else if (role == 'other') commitStyle({ otherBold: next })
    else commitStyle({ titleBold: next })
  }, [role, commitStyle])

  const onHollow = useCallback((id: Hollow) => {
    if (role == 'current') commitStyle({ hollowStyle: id })
    else if (role == 'other') commitStyle({ otherHollowStyle: id })
    else commitStyle({ titleHollowStyle: id })
  }, [role, commitStyle])

  const onHollowSize = useCallback((next: number) => {
    if (role == 'current') commitStyle({ hollowSize: next })
    else if (role == 'other') commitStyle({ otherHollowSize: next })
    else commitStyle({ titleHollowSize: next })
  }, [role, commitStyle])

  const onLetterDone = useCallback((next: number) => {
    setLetterSlidingFlag(false)
    if (next == letter) return
    if (role == 'current') commitStyle({ letterSpacing: next })
    else if (role == 'other') commitStyle({ otherLetterSpacing: next })
    else commitStyle({ titleLetterSpacing: next })
  }, [role, letter, commitStyle])

  const onColor = useCallback((hex: string) => {
    if (role == 'title') {
      updateSetting({ 'desktopLyric.title.color': hex })
      enqueue(() => setDesktopLyricTitleOptions())
      return
    }
    if (role == 'current') updateSetting({ 'desktopLyric.style.lyricPlayedColor': hex })
    else updateSetting({ 'desktopLyric.style.lyricUnplayColor': hex })
    enqueue(() => setDesktopLyricColor(null, null, null))
  }, [role, enqueue])

  const onShadow = useCallback((hex: string) => {
    updateSetting({ 'desktopLyric.style.lyricShadowColor': hex })
    enqueue(() => setDesktopLyricColor(null, null, null))
  }, [enqueue])

  const onLetterStart = useCallback<NonNullable<SliderProps['onSlidingStart']>>(() => {
    setLetterSliding(letter)
    setLetterSlidingFlag(true)
  }, [letter])
  const onLetterChange = useCallback<NonNullable<SliderProps['onValueChange']>>(v => { setLetterSliding(v) }, [])
  const onSizeStart = useCallback<NonNullable<SliderProps['onSlidingStart']>>(() => {
    setSizeSliding(hollowSize)
    setSizeSlidingFlag(true)
  }, [hollowSize])
  const onSizeChange = useCallback<NonNullable<SliderProps['onValueChange']>>(v => { setSizeSliding(v) }, [])
  const onSizeDone = useCallback<NonNullable<SliderProps['onSlidingComplete']>>(v => {
    setSizeSlidingFlag(false)
    if (v != hollowSize) onHollowSize(v)
  }, [hollowSize, onHollowSize])

  const letterShown = isLetterSliding ? letterSliding : letter
  const hollowShown = isSizeSliding ? sizeSliding : hollowSize

  return (
    <>
      <SubTitle title={t('setting_lyric_desktop_color')}>
        <ColorValueField title={colorTitle} value={color} onChange={onColor} />
      </SubTitle>
      {role == 'current'
        ? (
            <SubTitle title={t('setting_lyric_desktop_shadow_color')}>
              <ColorValueField title={t('setting_lyric_desktop_shadow_color')} value={shadowColor} onChange={onShadow} />
            </SubTitle>
          )
        : null}
      <SubTitle title={t('setting_lyric_desktop_font_size')}>
        <FontSizeStepper value={fontSize} onChange={onFontSize} />
      </SubTitle>
      <SubTitle title={t('setting_lyric_desktop_align')}>
        <View style={styles.list}>
          {alignList.map(({ id, name }) => (
            <CheckBox key={id} marginBottom={3} check={align == id} label={name} onChange={() => { onAlign(id) }} need />
          ))}
        </View>
      </SubTitle>
      <SubTitle title={t('setting_lyric_desktop_letter_spacing')}>
        <View style={styles.sliderRow}>
          <Text style={{ color: theme['c-primary-font'] }}>{Number.isInteger(letterShown) ? letterShown : letterShown.toFixed(1)}</Text>
          <Slider
            minimumValue={0}
            maximumValue={50}
            step={1}
            value={letterShown}
            onSlidingStart={onLetterStart}
            onValueChange={onLetterChange}
            onSlidingComplete={onLetterDone}
          />
        </View>
      </SubTitle>
      <View style={styles.checkboxRow}>
        <CheckBoxItem check={bold} onChange={onBold} label={t('setting_lyric_desktop_bold')} />
      </View>
      <SubTitle title={t('setting_lyric_desktop_hollow')}>
        <View style={styles.list}>
          {HOLLOWS.map(id => (
            <CheckBox key={id} marginBottom={3} check={hollow == id} label={hollowLabels[id]} onChange={() => { onHollow(id) }} need />
          ))}
        </View>
      </SubTitle>
      {hollow > 0
        ? (
            <SubTitle title={t('setting_lyric_desktop_hollow_size')}>
              <View style={styles.sliderRow}>
                <Text style={{ color: theme['c-primary-font'] }}>{hollowShown}</Text>
                <Slider
                  minimumValue={1}
                  maximumValue={12}
                  step={1}
                  value={hollowShown}
                  onSlidingStart={onSizeStart}
                  onValueChange={onSizeChange}
                  onSlidingComplete={onSizeDone}
                />
              </View>
            </SubTitle>
          )
        : null}
    </>
  )
})

const styles = createStyle({
  list: {
    flexGrow: 0,
    flexShrink: 1,
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  sliderRow: {
    flexGrow: 0,
    flexShrink: 1,
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
  },
  checkboxRow: {
    marginBottom: 8,
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
})
