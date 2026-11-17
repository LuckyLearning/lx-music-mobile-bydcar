import { memo, useCallback, useEffect, useRef, useState } from 'react'
import { AppState, LayoutAnimation, Platform, TouchableOpacity, UIManager, View } from 'react-native'
import { screenkeepAwake, screenUnkeepAwake } from '@/utils/nativeModules/utils'
import { AmbientBackground, CarText } from '@/components/car'
import { Icon } from '@/components/common/Icon'
import { setComponentId } from '@/core/common'
import { COMPONENT_IDS, NAV_SHEAR_NATIVE_IDS } from '@/config/constant'
import PageContent from '@/components/PageContent'
import commonState, { type InitState as CommonState } from '@/store/common/state'
import { pop } from '@/navigation'
import { px } from '@/utils/pixelRatio'
import { useTheme } from '@/store/theme/hook'
import { usePlayerMusicInfo } from '@/store/player/hook'
import { useStatusbarHeight } from '@/store/common/hook'
import { useI18n } from '@/lang'
import useWindowSize from '@/utils/hooks/useWindowSize'
import { CAR_CONTROLS_IDLE_MS, CAR_DOCK_RESERVE_DP } from '../playerLayout'
import Lyric from '../Horizontal/Lyric'
import Cover from './Cover'
import Controls from './Controls'

if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true)
}

const animateLayout = () => {
  LayoutAnimation.configureNext({
    duration: 200,
    create: { type: LayoutAnimation.Types.easeInEaseOut, property: LayoutAnimation.Properties.opacity },
    update: { type: LayoutAnimation.Types.easeInEaseOut },
    delete: { type: LayoutAnimation.Types.easeInEaseOut, property: LayoutAnimation.Properties.opacity },
  })
}

const useControlsIdle = () => {
  const [visible, setVisible] = useState(true)
  const visibleRef = useRef(true)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const noteInteraction = useCallback(() => {
    if (!visibleRef.current) {
      visibleRef.current = true
      animateLayout()
      setVisible(true)
    }
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = setTimeout(() => {
      timerRef.current = null
      visibleRef.current = false
      animateLayout()
      setVisible(false)
    }, CAR_CONTROLS_IDLE_MS)
  }, [])

  useEffect(() => {
    noteInteraction()
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
    }
  }, [noteInteraction])

  return { visible, noteInteraction }
}

const BACK_SIZE = 56
const headerPad = () => px(28)
const headerGap = () => px(12)

const PinnedTitle = () => {
  const theme = useTheme()
  const musicInfo = usePlayerMusicInfo()
  return (
    <View style={{ flex: 1, minWidth: 0 }}>
      <CarText size={60} weight="700" numberOfLines={1}>{musicInfo.name}</CarText>
      {musicInfo.singer
        ? <CarText size={26} color={theme['text-2']} numberOfLines={1} style={{ marginTop: px(8) }}>{musicInfo.singer}</CarText>
        : null}
    </View>
  )
}

export default memo(({ componentId }: { componentId: string }) => {
  const theme = useTheme()
  const t = useI18n()
  useWindowSize()
  const statusBarHeight = useStatusbarHeight()
  const { visible, noteInteraction } = useControlsIdle()

  useEffect(() => {
    setComponentId(COMPONENT_IDS.playDetail, componentId)
    screenkeepAwake()
    const appstateListener = AppState.addEventListener('change', (state) => {
      switch (state) {
        case 'active':
          if (!commonState.componentIds.comment) screenkeepAwake()
          break
        case 'background':
          screenUnkeepAwake()
          break
      }
    })

    const handleComponentIdsChange = (ids: CommonState['componentIds']) => {
      if (ids.comment) screenUnkeepAwake()
      else if (AppState.currentState == 'active') screenkeepAwake()
    }

    global.state_event.on('componentIdsUpdated', handleComponentIdsChange)

    return () => {
      global.state_event.off('componentIdsUpdated', handleComponentIdsChange)
      appstateListener.remove()
      screenUnkeepAwake()
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const back = () => {
    noteInteraction()
    void pop(componentId)
  }

  return (
    <PageContent>
      <View style={{ flex: 1 }}>
        <AmbientBackground />
        <View
          style={{ flex: 1, paddingTop: statusBarHeight, overflow: 'hidden' }}
          onTouchStart={noteInteraction}
          onTouchMove={noteInteraction}
          onTouchEnd={noteInteraction}
          onTouchCancel={noteInteraction}
          onStartShouldSetResponderCapture={() => {
            noteInteraction()
            return false
          }}
          onMoveShouldSetResponderCapture={() => {
            noteInteraction()
            return false
          }}
        >
          <View style={{ flex: 1, flexDirection: 'row', alignItems: 'stretch' }}>
            <View style={{ flexGrow: 7, flexShrink: 1, flexBasis: 0, minWidth: 0 }}>
              <View nativeID={NAV_SHEAR_NATIVE_IDS.playDetail_header} style={{ flexDirection: 'row', alignItems: 'center', paddingLeft: headerPad(), paddingRight: headerPad(), paddingTop: px(16), paddingBottom: px(8), gap: headerGap() }}>
                <TouchableOpacity
                  accessibilityRole="button"
                  accessibilityLabel={t('back')}
                  activeOpacity={0.6}
                  onPress={back}
                  style={{ width: BACK_SIZE, height: BACK_SIZE, alignItems: 'center', justifyContent: 'center' }}
                >
                  <Icon name="lucide-chevron-left" rawSize={28} color={theme['text-1']} />
                </TouchableOpacity>
                <PinnedTitle />
              </View>
              <View collapsable={false} style={{ flex: 1, minHeight: 0, paddingLeft: headerPad() + BACK_SIZE + headerGap(), paddingRight: headerPad() }}>
                <Lyric onInteraction={noteInteraction} />
              </View>
            </View>
            <Cover componentId={componentId} />
          </View>
          {visible
            ? (
                <View nativeID={NAV_SHEAR_NATIVE_IDS.playDetail_player} collapsable={false}>
                  <Controls onInteraction={noteInteraction} />
                </View>
              )
            : null}
        </View>
        {/* 系统 Dock 占位：空白，不是应用自己画的底栏。挡住溢出的点击，避免控件落到这 56dp 里。 */}
        <View
          pointerEvents="box-only"
          collapsable={false}
          style={{ height: CAR_DOCK_RESERVE_DP }}
        />
      </View>
    </PageContent>
  )
})
