import { updateSetting } from '@/core/common'
import { useI18n } from '@/lang'
import { createStyle } from '@/utils/tools'
import { memo } from 'react'
import { Platform, View } from 'react-native'
import { useSettingValue } from '@/store/setting/hook'

import CheckBoxItem from '../../components/CheckBoxItem'

export default memo(() => {
  const t = useI18n()
  const bootAutostart = useSettingValue('player.bootAutostart')
  const setBootAutostart = (enabled: boolean) => {
    updateSetting({ 'player.bootAutostart': enabled })
  }

  if (Platform.OS !== 'android') return null

  return (
    <View style={styles.content}>
      <CheckBoxItem
        check={bootAutostart}
        onChange={setBootAutostart}
        label={t('setting_basic_boot_autostart')}
        helpTitle={t('setting_basic_boot_autostart')}
        helpDesc={t('setting_basic_boot_autostart_tip')}
      />
    </View>
  )
})

const styles = createStyle({
  content: {
    marginTop: 5,
  },
})
