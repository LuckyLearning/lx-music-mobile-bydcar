import { memo } from 'react'
import { View } from 'react-native'

import Section from '../../components/Section'
import IsShowLyric from './IsShowLyric'
import IsLockLyric from './IsLockLyric'
import IsShowToggleAnima from './IsShowToggleAnima'
import IsSingleLine from './IsSingleLine'
import ViewWidth from './ViewWidth'
import MaxLineNum from './MaxLineNum'
import TextOpacity from './TextOpacity'
import TextPositionY from './TextPositionY'
import { useI18n } from '@/lang'
import DisplaySelect from './DisplaySelect'
import ViewHeight from './ViewHeight'
import { PositionX, PositionY } from './Position'
import TitleOptions from './TitleOptions'
import LineRoleStyle from './LineRoleStyle'
import BgStyle from './BgStyle'
import { useSettingValue } from '@/store/setting/hook'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { createStyle } from '@/utils/tools'

const Group = memo(({ title, children }: { title: string, children: React.ReactNode }) => {
  const theme = useTheme()
  return (
    <View style={{ ...styles.group, backgroundColor: theme['c-primary-background'] }}>
      <Text style={{ ...styles.groupTitle, color: theme['c-primary-font'] }} size={15}>{title}</Text>
      {children}
    </View>
  )
})

/**
 * [BYD] 桌面歌词 / 悬浮窗设置。
 * 当前行、其它行、标题行各自一组相同的控件（颜色、字号、对齐、字距、加粗、空心）。
 */
export default memo(() => {
  const t = useI18n()
  const enabled = useSettingValue('desktopLyric.enable')

  return (
    <Section title={t('setting_lyric_desktop')}>
      <IsShowLyric />
      {enabled ? (
        <View style={styles.body}>
          <Group title={t('setting_lyric_desktop_group_basic')}>
            <IsLockLyric />
            <IsShowToggleAnima />
            <IsSingleLine />
            <DisplaySelect />
          </Group>

          <Group title={t('setting_lyric_desktop_group_layout')}>
            <ViewWidth />
            <ViewHeight />
            <MaxLineNum />
            <PositionX />
            <PositionY />
            <TextPositionY />
            <TextOpacity />
          </Group>

          <Group title={t('setting_lyric_desktop_group_current')}>
            <LineRoleStyle role="current" />
          </Group>

          <Group title={t('setting_lyric_desktop_group_other')}>
            <LineRoleStyle role="other" />
          </Group>

          <Group title={t('setting_lyric_desktop_group_title')}>
            <TitleOptions />
          </Group>

          <Group title={t('setting_lyric_desktop_group_bg')}>
            <BgStyle />
          </Group>
        </View>
      ) : null}
    </Section>
  )
})

const styles = createStyle({
  body: {
    marginTop: 4,
  },
  group: {
    marginTop: 14,
    marginBottom: 4,
    paddingTop: 10,
    paddingBottom: 4,
    paddingRight: 8,
    borderRadius: 8,
  },
  groupTitle: {
    marginLeft: 15,
    marginBottom: 10,
    fontWeight: '600',
  },
})
