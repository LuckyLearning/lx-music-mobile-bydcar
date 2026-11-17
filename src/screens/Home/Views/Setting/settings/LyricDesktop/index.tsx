import { memo } from 'react'
import { View } from 'react-native'

import Section from '../../components/Section'
import IsShowLyric from './IsShowLyric'
import IsLockLyric from './IsLockLyric'
import IsShowToggleAnima from './IsShowToggleAnima'
import IsSingleLine from './IsSingleLine'
import TextSize from './TextSize'
import ViewWidth from './ViewWidth'
import MaxLineNum from './MaxLineNum'
import TextOpacity from './TextOpacity'
import TextPositionX from './TextPositionX'
import TextPositionY from './TextPositionY'
import { useI18n } from '@/lang'
import Theme from './Theme'
import BgStyle from './BgStyle'
import LetterSpacing from './LetterSpacing'
import DisplaySelect from './DisplaySelect'
import ViewHeight from './ViewHeight'
import { PositionX, PositionY } from './Position'
import LyricStyle from './LyricStyle'
import TitleOptions from './TitleOptions'
import { useSettingValue } from '@/store/setting/hook'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { createStyle } from '@/utils/tools'

const Group = memo(({ title, children }: { title: string, children: React.ReactNode }) => {
  const theme = useTheme()
  return (
    <View style={styles.group}>
      <Text style={{ ...styles.groupTitle, color: theme['c-primary-font'] }} size={14}>{title}</Text>
      {children}
    </View>
  )
})

/**
 * [BYD] 桌面歌词 / 悬浮窗设置独立成块。
 * 开关联动：总开关开启后展示可调项；标题/空心等子开关控制各自明细是否出现。
 * 所有展示出来的控件都会实时写入原生悬浮窗。
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
            <TextPositionX />
            <TextPositionY />
          </Group>

          <Group title={t('setting_lyric_desktop_group_lyric')}>
            <Theme />
            <TextSize />
            <TextOpacity />
            <LetterSpacing />
            <LyricStyle />
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
    marginTop: 8,
    marginBottom: 6,
  },
  groupTitle: {
    marginBottom: 8,
    fontWeight: '600',
  },
})
