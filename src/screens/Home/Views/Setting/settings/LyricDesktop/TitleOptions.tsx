// [BYD] 悬浮窗歌名/歌手显示：模式、上下位置、对齐、颜色；与模式开关联动
import { memo, useCallback, useMemo } from 'react'
import { View, TouchableOpacity } from 'react-native'

import SubTitle from '../../components/SubTitle'
import CheckBox from '@/components/common/CheckBox'
import { useI18n } from '@/lang'
import { useSettingValue } from '@/store/setting/hook'
import { setDesktopLyricTitleOptions } from '@/core/desktopLyric'
import { updateSetting } from '@/core/common'
import { createStyle } from '@/utils/tools'

type Mode = LX.AppSetting['desktopLyric.title.mode']
type Position = LX.AppSetting['desktopLyric.title.position']
type Align = LX.AppSetting['desktopLyric.title.align']

const MODES = ['none', 'name', 'name_singer'] as const
const POSITIONS = ['above', 'below'] as const
const ALIGNS = ['left', 'center', 'right'] as const

const TITLE_COLORS = [
  'rgba(255, 255, 255, 0.85)',
  'rgba(255, 255, 255, 1)',
  'rgba(200, 200, 200, 0.9)',
  'rgba(255, 250, 18, 0.95)',
  'rgba(8, 230, 100, 0.95)',
  'rgba(1, 156, 228, 0.95)',
  'rgba(255, 122, 80, 0.95)',
  'rgba(0, 0, 0, 0.85)',
] as const

export default memo(() => {
  const t = useI18n()
  const mode = useSettingValue('desktopLyric.title.mode')
  const position = useSettingValue('desktopLyric.title.position')
  const align = useSettingValue('desktopLyric.title.align')
  const color = useSettingValue('desktopLyric.title.color')
  const enabled = mode != 'none'

  const apply = useCallback((patch: Partial<{
    mode: Mode
    position: Position
    align: Align
    color: string
  }>) => {
    const nextMode = patch.mode ?? mode
    const nextPos = patch.position ?? position
    const nextAlign = patch.align ?? align
    const nextColor = patch.color ?? color
    void setDesktopLyricTitleOptions(nextMode, nextPos, nextAlign, nextColor).then(() => {
      const setting: Partial<LX.AppSetting> = {}
      if (patch.mode != null) setting['desktopLyric.title.mode'] = patch.mode
      if (patch.position != null) setting['desktopLyric.title.position'] = patch.position
      if (patch.align != null) setting['desktopLyric.title.align'] = patch.align
      if (patch.color != null) setting['desktopLyric.title.color'] = patch.color
      updateSetting(setting)
    })
  }, [mode, position, align, color])

  const modeList = useMemo(() => MODES.map(id => ({
    id,
    name: t(`setting_lyric_desktop_title_mode_${id}`),
  })), [t])

  const positionList = useMemo(() => POSITIONS.map(id => ({
    id,
    name: t(`setting_lyric_desktop_title_position_${id}`),
  })), [t])

  const alignList = useMemo(() => ALIGNS.map(id => ({
    id,
    name: t(`setting_lyric_desktop_text_x_${id}`),
  })), [t])

  return (
    <>
      <SubTitle title={t('setting_lyric_desktop_title_mode')}>
        <View style={styles.list}>
          {modeList.map(({ id, name }) => (
            <CheckBox
              key={id}
              marginBottom={3}
              check={mode == id}
              label={name}
              onChange={() => { apply({ mode: id }) }}
              need
            />
          ))}
        </View>
      </SubTitle>
      {enabled ? (
        <>
          <SubTitle title={t('setting_lyric_desktop_title_position')}>
            <View style={styles.list}>
              {positionList.map(({ id, name }) => (
                <CheckBox
                  key={id}
                  marginBottom={3}
                  check={position == id}
                  label={name}
                  onChange={() => { apply({ position: id }) }}
                  need
                />
              ))}
            </View>
          </SubTitle>
          <SubTitle title={t('setting_lyric_desktop_title_align')}>
            <View style={styles.list}>
              {alignList.map(({ id, name }) => (
                <CheckBox
                  key={id}
                  marginBottom={3}
                  check={align == id}
                  label={name}
                  onChange={() => { apply({ align: id }) }}
                  need
                />
              ))}
            </View>
          </SubTitle>
          <SubTitle title={t('setting_lyric_desktop_title_color')}>
            <View style={styles.dots}>
              {TITLE_COLORS.map(c => (
                <TouchableOpacity
                  key={c}
                  activeOpacity={0.6}
                  onPress={() => { apply({ color: c }) }}
                  style={[styles.dotWrap, color == c && styles.dotActive]}
                >
                  <View style={[styles.dot, { backgroundColor: c }]} />
                </TouchableOpacity>
              ))}
            </View>
          </SubTitle>
        </>
      ) : null}
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
