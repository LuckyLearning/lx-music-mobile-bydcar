import { updateSetting } from '@/core/common'
import { useI18n } from '@/lang'
import { createStyle } from '@/utils/tools'
import { memo } from 'react'
import { View } from 'react-native'
import { useSettingValue } from '@/store/setting/hook'

import CheckBoxItem from '../../components/CheckBoxItem'

export default memo(() => {
  const t = useI18n()
  const carLayout = useSettingValue('player.carLayout')
  const setCarLayout = (enabled: boolean) => {
    updateSetting({ 'player.carLayout': enabled })
  }

  return (
    <View style={styles.content}>
      <CheckBoxItem
        check={carLayout}
        onChange={setCarLayout}
        label={t('setting_player_car_layout')}
        helpDesc={t('setting_player_car_layout_tip')}
      />
    </View>
  )
})

const styles = createStyle({
  content: {
    marginTop: 5,
  },
})
