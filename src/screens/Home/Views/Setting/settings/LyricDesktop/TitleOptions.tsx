// [BYD] 标题行：显示模式和上下位置。字号、对齐、字距、加粗、空心、颜色在打开后单独一组。
import { memo, useCallback, useMemo } from 'react'
import { View } from 'react-native'

import SubTitle from '../../components/SubTitle'
import CheckBox from '@/components/common/CheckBox'
import { useI18n } from '@/lang'
import { useSettingValue } from '@/store/setting/hook'
import { setDesktopLyricTitleOptions } from '@/core/desktopLyric'
import { updateSetting } from '@/core/common'
import { createStyle } from '@/utils/tools'
import LineRoleStyle from './LineRoleStyle'

type Mode = LX.AppSetting['desktopLyric.title.mode']
type Position = LX.AppSetting['desktopLyric.title.position']

const MODES = ['none', 'name', 'name_singer'] as const
const POSITIONS = ['above', 'below'] as const

export default memo(() => {
  const t = useI18n()
  const mode = useSettingValue('desktopLyric.title.mode')
  const position = useSettingValue('desktopLyric.title.position')
  const enabled = mode != 'none'

  const apply = useCallback((patch: Partial<{ mode: Mode, position: Position }>) => {
    const nextMode = patch.mode ?? mode
    const nextPos = patch.position ?? position
    void setDesktopLyricTitleOptions(nextMode, nextPos, undefined, undefined).then(() => {
      const setting: Partial<LX.AppSetting> = {}
      if (patch.mode != null) setting['desktopLyric.title.mode'] = patch.mode
      if (patch.position != null) setting['desktopLyric.title.position'] = patch.position
      updateSetting(setting)
    })
  }, [mode, position])

  const modeList = useMemo(() => MODES.map(id => ({
    id,
    name: t(`setting_lyric_desktop_title_mode_${id}`),
  })), [t])

  const positionList = useMemo(() => POSITIONS.map(id => ({
    id,
    name: t(`setting_lyric_desktop_title_position_${id}`),
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
      {enabled
        ? (
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
              <LineRoleStyle role="title" />
            </>
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
})
