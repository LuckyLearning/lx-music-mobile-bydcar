// [BYD] 颜色用可编辑的色值，旁边放一小块预览。输到一半或不合法时不写入、不崩。
import { memo, useEffect, useState } from 'react'
import { View, TextInput } from 'react-native'

import { useI18n } from '@/lang'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { createStyle } from '@/utils/tools'
import { parseColorValue } from '@/utils/colorValue'

export default memo(({ value, onChange }: {
  value: string
  onChange: (hex: string) => void
}) => {
  const t = useI18n()
  const theme = useTheme()
  const parsedValue = parseColorValue(value)
  const [draft, setDraft] = useState(parsedValue?.hex ?? '')
  const [chip, setChip] = useState(parsedValue?.rgba ?? 'rgba(0, 0, 0, 1)')
  const [invalid, setInvalid] = useState(false)

  useEffect(() => {
    const parsed = parseColorValue(value)
    if (!parsed) return
    setChip(parsed.rgba)
    setDraft(prev => {
      const prevParsed = parseColorValue(prev)
      if (prevParsed && prevParsed.hex == parsed.hex) return prev
      return parsed.hex
    })
    setInvalid(false)
  }, [value])

  const onChangeText = (text: string) => {
    setDraft(text)
    const parsed = parseColorValue(text)
    if (!parsed) {
      setInvalid(true)
      return
    }
    setInvalid(false)
    setChip(parsed.rgba)
    if (parsed.hex != parseColorValue(value)?.hex) onChange(parsed.hex)
  }

  return (
    <View>
      <View style={styles.row}>
        <View style={{ ...styles.chip, backgroundColor: chip, borderColor: theme['c-primary'] }} />
        <TextInput
          value={draft}
          onChangeText={onChangeText}
          autoCapitalize="none"
          autoCorrect={false}
          underlineColorAndroid="transparent"
          placeholder="#RRGGBBAA"
          placeholderTextColor={theme['c-font-label']}
          style={{ ...styles.input, color: theme['c-font'], borderColor: invalid ? '#c45c26' : theme['c-primary-alpha-500'], backgroundColor: theme['c-primary-input-background'] }}
        />
      </View>
      {invalid
        ? <Text style={styles.hint} size={12} color={theme['c-primary-font']}>{t('setting_lyric_desktop_color_invalid')}</Text>
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
  input: {
    flexGrow: 1,
    flexShrink: 1,
    minWidth: 140,
    minHeight: 36,
    borderWidth: 1,
    borderRadius: 6,
    paddingHorizontal: 10,
    paddingVertical: 4,
    fontSize: 14,
  },
  hint: {
    marginTop: 6,
  },
})
