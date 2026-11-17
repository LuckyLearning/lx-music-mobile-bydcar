// [BYD] 悬浮窗颜色：色块打开 ColorPickerView，不能手填色值。色值只作读出。
import { memo, useCallback, useState } from 'react'
import { View, TouchableOpacity } from 'react-native'

import { useI18n } from '@/lang'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { createStyle } from '@/utils/tools'
import { parseColorValue } from '@/utils/colorValue'
import { pickLyricColor } from '@/utils/nativeModules/colorPicker'

export default memo(({ value, title, onChange }: {
  value: string
  title?: string
  onChange: (hex: string) => void
}) => {
  const t = useI18n()
  const theme = useTheme()
  const parsed = parseColorValue(value)
  const chip = parsed?.rgba ?? 'transparent'
  const readout = parsed?.hex ?? ''
  const dialogTitle = title ?? t('setting_lyric_desktop_color')
  const [opening, setOpening] = useState(false)

  const onPress = useCallback(() => {
    if (opening) return
    setOpening(true)
    void pickLyricColor(value, dialogTitle, t('confirm'), t('cancel')).then(next => {
      if (!next) return
      const current = parseColorValue(value)
      if (current && current.hex == next) return
      onChange(next)
    }, () => undefined).finally(() => {
      setOpening(false)
    })
  }, [opening, value, dialogTitle, t, onChange])

  return (
    <View>
      <TouchableOpacity
        activeOpacity={0.7}
        disabled={opening}
        accessibilityRole="button"
        accessibilityLabel={dialogTitle}
        onPress={onPress}
        style={styles.row}
      >
        <View style={{ ...styles.chip, backgroundColor: chip, borderColor: theme['c-primary'] }} />
        <View style={styles.copy}>
          {readout
            ? <Text size={14} color={theme['c-font']}>{readout}</Text>
            : null}
          <Text style={styles.hint} size={12} color={theme['c-font-label']}>{t('setting_lyric_desktop_color_pick')}</Text>
        </View>
      </TouchableOpacity>
      {!parsed
        ? <Text style={styles.invalid} size={12} color={theme['c-primary-font']}>{t('setting_lyric_desktop_color_invalid')}</Text>
        : null}
    </View>
  )
})

const styles = createStyle({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  chip: {
    width: 28,
    height: 28,
    borderRadius: 6,
    borderWidth: 1,
    marginRight: 10,
  },
  copy: {
    flexGrow: 1,
    flexShrink: 1,
  },
  hint: {
    marginTop: 2,
  },
  invalid: {
    marginTop: 6,
  },
})
