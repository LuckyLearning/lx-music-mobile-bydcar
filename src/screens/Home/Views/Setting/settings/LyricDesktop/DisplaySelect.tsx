// [BYD] 投屏屏幕选择：枚举 DisplayManager 的显示器，选择悬浮歌词投射到哪块屏
import { memo, useEffect, useState } from 'react'
import { View, StyleSheet } from 'react-native'

import SubTitle from '../../components/SubTitle'
import CheckBox from '@/components/common/CheckBox'
import { useSettingValue } from '@/store/setting/hook'
import { useI18n } from '@/lang'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { getDesktopLyricDisplays, setDesktopLyricDisplay } from '@/core/desktopLyric'
import { updateSetting } from '@/core/common'
import type { LyricDisplayInfo } from '@/utils/nativeModules/lyricDesktop'

export default memo(() => {
  const t = useI18n()
  const theme = useTheme()
  const displayId = useSettingValue('desktopLyric.displayId')
  const [displays, setDisplays] = useState<LyricDisplayInfo[]>([])

  useEffect(() => {
    void getDesktopLyricDisplays().then(list => {
      setDisplays(list)
    }).catch(() => { setDisplays([]) })
  }, [])

  const change = (id: number) => {
    void setDesktopLyricDisplay(id).then(() => {
      updateSetting({ 'desktopLyric.displayId': id })
    })
  }

  return (
    <SubTitle title={t('setting_lyric_desktop_display')}>
      {
        displays.length <= 1
          ? <Text style={{ color: theme['c-font-label'] }}>{t('setting_lyric_desktop_display_default')}</Text>
          : (
              <View style={styles.list}>
                {displays.map(d => (
                  <CheckBox
                    key={d.id}
                    marginBottom={3}
                    check={displayId == d.id || (displayId == -1 && d.isDefault)}
                    label={`${d.name || (d.isDefault ? t('setting_lyric_desktop_display_default') : t('setting_lyric_desktop_display_presentation'))} (${d.width}×${d.height})`}
                    onChange={() => { change(d.id) }}
                    need
                  />
                ))}
              </View>
            )
      }
    </SubTitle>
  )
})

const styles = StyleSheet.create({
  list: {
    flexGrow: 0,
    flexShrink: 1,
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
})
