import { updateSetting } from '@/core/common'
import { useI18n } from '@/lang'
import { createStyle } from '@/utils/tools'
import { memo, useEffect, useState } from 'react'
import { Platform, View } from 'react-native'
import { useSettingValue } from '@/store/setting/hook'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { getBootStatus, type BootStatusSnapshot } from '@/utils/nativeModules/utils'

import CheckBoxItem from '../../components/CheckBoxItem'

/** 与听风的诊断口径相同：结果一直空着超过一分钟，就当进程在起播途中被回收了。 */
const STUCK_MS = 60_000

const statusLabel = (
  t: ReturnType<typeof useI18n>,
  snap: BootStatusSnapshot,
  now: number,
) => {
  if (!snap.at) return t('setting_basic_boot_autostart_status_never')
  if (snap.action === 'none') return t('setting_basic_boot_autostart_status_off')
  if (snap.ok === 'true') {
    return snap.reason
      ? t('setting_basic_boot_autostart_status_ok_reason', { reason: snap.reason })
      : t('setting_basic_boot_autostart_status_ok')
  }
  if (snap.ok === 'false') {
    return t('setting_basic_boot_autostart_status_fail', {
      reason: snap.reason || t('setting_basic_boot_autostart_status_unknown'),
    })
  }
  if (now - snap.at > STUCK_MS) return t('setting_basic_boot_autostart_status_stuck')
  return t('setting_basic_boot_autostart_status_restoring')
}

export default memo(() => {
  const t = useI18n()
  const theme = useTheme()
  const bootAutostart = useSettingValue('player.bootAutostart')
  const [status, setStatus] = useState('')
  const setBootAutostart = (enabled: boolean) => {
    updateSetting({ 'player.bootAutostart': enabled })
  }

  useEffect(() => {
    if (Platform.OS !== 'android') return
    let cancelled = false
    void getBootStatus().then(snap => {
      if (cancelled || !snap) return
      if (!bootAutostart && !snap.at) return
      setStatus(statusLabel(t, snap, Date.now()))
    }).catch((err: unknown) => {
      console.log('LXBoot status unavailable', err)
    })
    return () => {
      cancelled = true
    }
  }, [bootAutostart, t])

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
      {status ? <Text size={12} color={theme['c-font-label']} style={styles.status}>{status}</Text> : null}
    </View>
  )
})

const styles = createStyle({
  content: {
    marginTop: 5,
  },
  status: {
    marginLeft: 25,
    marginRight: 15,
    marginTop: 2,
  },
})
