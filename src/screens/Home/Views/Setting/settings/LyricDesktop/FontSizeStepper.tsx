// [BYD] 字号用加减按钮，单位磅。最小 1，不设上限。
import { memo } from 'react'
import { View, TouchableOpacity } from 'react-native'

import { useI18n } from '@/lang'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { createStyle } from '@/utils/tools'

const MIN_PT = 1

export default memo(({ value, onChange }: {
  value: number
  onChange: (next: number) => void
}) => {
  const t = useI18n()
  const theme = useTheme()
  const shown = Math.max(MIN_PT, Math.round(value))
  const canDecrease = shown > MIN_PT

  return (
    <View style={styles.row}>
      <TouchableOpacity
        accessibilityRole="button"
        accessibilityLabel={t('setting_lyric_desktop_font_dec')}
        activeOpacity={0.6}
        disabled={!canDecrease}
        onPress={() => { if (canDecrease) onChange(shown - 1) }}
        style={{ ...styles.btn, borderColor: theme['c-primary'], opacity: canDecrease ? 1 : 0.35 }}
      >
        <Text size={18} color={theme['c-primary-font']}>－</Text>
      </TouchableOpacity>
      <Text style={styles.value} color={theme['c-primary-font']}>{shown} {t('setting_lyric_desktop_font_unit')}</Text>
      <TouchableOpacity
        accessibilityRole="button"
        accessibilityLabel={t('setting_lyric_desktop_font_inc')}
        activeOpacity={0.6}
        onPress={() => { onChange(shown + 1) }}
        style={{ ...styles.btn, borderColor: theme['c-primary'] }}
      >
        <Text size={18} color={theme['c-primary-font']}>＋</Text>
      </TouchableOpacity>
    </View>
  )
})

const styles = createStyle({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
  },
  btn: {
    minWidth: 44,
    minHeight: 36,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderRadius: 6,
    alignItems: 'center',
    justifyContent: 'center',
  },
  value: {
    minWidth: 72,
    marginHorizontal: 12,
    textAlign: 'center',
  },
})
