// [BYD] 悬浮窗字体选择：预设常用 Android 字体族（空串=系统默认）
import { memo } from 'react'
import { View, StyleSheet } from 'react-native'

import SubTitle from '../../components/SubTitle'
import CheckBox from '@/components/common/CheckBox'
import { useSettingValue } from '@/store/setting/hook'
import { useI18n } from '@/lang'
import { updateSetting } from '@/core/common'

// 值为 Android Typeface family 名；空串表示系统默认
const FONT_IDS = [
  { id: '', key: 'setting_lyric_desktop_font_default' },
  { id: 'sans-serif', key: 'setting_lyric_desktop_font_sans' },
  { id: 'serif', key: 'setting_lyric_desktop_font_serif' },
  { id: 'monospace', key: 'setting_lyric_desktop_font_mono' },
  { id: 'sans-serif-light', key: 'setting_lyric_desktop_font_light' },
  { id: 'sans-serif-condensed', key: 'setting_lyric_desktop_font_condensed' },
] as const

export default memo(() => {
  const t = useI18n()
  const fontFamily = useSettingValue('desktopLyric.style.fontFamily')

  const change = (id: string) => {
    updateSetting({ 'desktopLyric.style.fontFamily': id })
  }

  return (
    <SubTitle title={t('setting_lyric_desktop_font')}>
      <View style={styles.list}>
        {FONT_IDS.map(f => (
          <CheckBox
            key={f.id || 'default'}
            marginBottom={3}
            check={fontFamily == f.id}
            label={t(f.key)}
            onChange={() => { change(f.id) }}
            need
          />
        ))}
      </View>
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
