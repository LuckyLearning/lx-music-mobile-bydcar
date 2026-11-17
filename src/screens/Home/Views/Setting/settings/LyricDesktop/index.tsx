import { memo } from 'react'

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
// [BYD] 悬浮窗增强设置
import BgStyle from './BgStyle'
import LetterSpacing from './LetterSpacing'
import DisplaySelect from './DisplaySelect'
import ViewHeight from './ViewHeight'
import { PositionX, PositionY } from './Position'
import LyricStyle from './LyricStyle'
// import { useTranslation } from '@/plugins/i18n'

export default memo(() => {
  const t = useI18n()

  return (
    <Section title={t('setting_lyric_desktop')}>
      <IsShowLyric />
      <IsLockLyric />
      <IsShowToggleAnima />
      <IsSingleLine />
      <Theme />
      <TextSize />
      <ViewWidth />
      <ViewHeight />
      <MaxLineNum />
      <TextOpacity />
      <TextPositionX />
      <TextPositionY />
      <PositionX />
      <PositionY />
      <BgStyle />
      <LetterSpacing />
      <LyricStyle />
      <DisplaySelect />
    </Section>
  )
})
