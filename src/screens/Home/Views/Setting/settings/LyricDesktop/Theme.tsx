import { updateSetting } from '@/core/common'
import { setDesktopLyricColor } from '@/core/desktopLyric'
import { useI18n } from '@/lang'
import { memo, useCallback } from 'react'
import { StyleSheet, View, TouchableOpacity } from 'react-native'

import SubTitle from '../../components/SubTitle'
import { useSettingValue } from '@/store/setting/hook'

const CURRENT_COLORS = [
  '#08e664',
  '#fffa12',
  '#019ce4',
  '#ff1222',
  '#ef6976',
  '#c851d4',
  '#ffa600',
  '#ffffff',
  '#000000',
] as const

const OTHER_COLORS = [
  'rgba(255, 255, 255, 0.55)',
  'rgba(255, 255, 255, 0.85)',
  'rgba(200, 200, 200, 0.7)',
  'rgba(160, 160, 160, 0.75)',
  'rgba(0, 0, 0, 0.55)',
  'rgba(255, 250, 18, 0.55)',
  'rgba(8, 230, 100, 0.55)',
  'rgba(1, 156, 228, 0.55)',
] as const

const SHADOW_FOR_CURRENT: Record<string, string> = {
  '#08e664': 'rgba(0,0,0,0.6)',
  '#fffa12': 'rgba(0,0,0,0.6)',
  '#019ce4': 'rgba(0,0,0,0.6)',
  '#ff1222': 'rgba(0,0,0,0.6)',
  '#ef6976': 'rgba(0,0,0,0.6)',
  '#c851d4': 'rgba(0,0,0,0.6)',
  '#ffa600': 'rgba(0,0,0,0.6)',
  '#ffffff': 'rgba(0,0,0,0.6)',
  '#000000': '#ffffff',
}

const toRgba = (hexOrRgba: string): string => {
  if (hexOrRgba.startsWith('rgba') || hexOrRgba.startsWith('rgb')) return hexOrRgba
  const hex = hexOrRgba.replace('#', '')
  if (hex.length != 6) return hexOrRgba
  const r = parseInt(hex.slice(0, 2), 16)
  const g = parseInt(hex.slice(2, 4), 16)
  const b = parseInt(hex.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, 1)`
}

const ColorDots = ({ colors, active, onChange }: {
  colors: readonly string[]
  active: string
  onChange: (c: string) => void
}) => {
  const normalize = (c: string) => c.replace(/\s+/g, '').toLowerCase()
  return (
    <View style={styles.list}>
      {colors.map((c) => {
        const selected = normalize(active) == normalize(toRgba(c)) || normalize(active) == normalize(c)
        return (
          <TouchableOpacity key={c} style={[styles.item, selected && styles.itemActive]} activeOpacity={0.5} onPress={() => { onChange(c) }}>
            <View style={styles.colorContent}>
              <View style={{ ...styles.image, backgroundColor: c }} />
            </View>
          </TouchableOpacity>
        )
      })}
    </View>
  )
}

export default memo(() => {
  const t = useI18n()
  const playedColor = useSettingValue('desktopLyric.style.lyricPlayedColor')
  const unplayColor = useSettingValue('desktopLyric.style.lyricUnplayColor')

  const setCurrent = useCallback((color: string) => {
    const played = toRgba(color)
    const shadow = SHADOW_FOR_CURRENT[color] ?? 'rgba(0,0,0,0.6)'
    void setDesktopLyricColor(null, played, shadow).then(() => {
      updateSetting({
        'desktopLyric.style.lyricPlayedColor': played,
        'desktopLyric.style.lyricShadowColor': shadow,
      })
    })
  }, [])

  const setOther = useCallback((color: string) => {
    const unplay = toRgba(color)
    void setDesktopLyricColor(unplay, null, null).then(() => {
      updateSetting({ 'desktopLyric.style.lyricUnplayColor': unplay })
    })
  }, [])

  return (
    <>
      <SubTitle title={t('setting_lyric_desktop_theme_current')}>
        <ColorDots colors={CURRENT_COLORS} active={playedColor} onChange={setCurrent} />
      </SubTitle>
      <SubTitle title={t('setting_lyric_desktop_theme_other')}>
        <ColorDots colors={OTHER_COLORS} active={unplayColor} onChange={setOther} />
      </SubTitle>
    </>
  )
})

const styles = StyleSheet.create({
  list: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  item: {
    marginRight: 15,
    marginTop: 5,
    alignItems: 'center',
    width: 26,
    padding: 2,
    borderRadius: 6,
    borderWidth: 1.5,
    borderColor: 'transparent',
  },
  itemActive: {
    borderColor: '#ff7a50',
  },
  colorContent: {
    width: 22,
    height: 22,
    borderRadius: 4,
    alignItems: 'center',
    justifyContent: 'center',
  },
  image: {
    width: 18,
    height: 18,
    borderRadius: 4,
    elevation: 1,
  },
})
