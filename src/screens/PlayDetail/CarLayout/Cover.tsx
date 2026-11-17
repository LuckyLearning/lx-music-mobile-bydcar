import { memo, useEffect, useState } from 'react'
import { View } from 'react-native'
import Image from '@/components/common/Image'
import { usePlayerMusicInfo } from '@/store/player/hook'
import { useNavigationComponentDidAppear } from '@/navigation'
import { NAV_SHEAR_NATIVE_IDS } from '@/config/constant'
import commonState from '@/store/common/state'
import { useTheme } from '@/store/theme/hook'
import { px } from '@/utils/pixelRatio'
import useWindowSize from '@/utils/hooks/useWindowSize'
import { squareCoverSide } from '../playerLayout'

/** 右栏只放正方形封面，按列的短边取边长，居中，多出来的高度留给背景。 */
export default memo(({ componentId }: { componentId: string }) => {
  const theme = useTheme()
  useWindowSize()
  const musicInfo = usePlayerMusicInfo()
  const [slot, setSlot] = useState({ width: 0, height: 0 })
  const side = squareCoverSide(slot.width, slot.height)

  const [animated, setAnimated] = useState(!!commonState.componentIds.playDetail)
  const [pic, setPic] = useState(musicInfo.pic)
  useEffect(() => {
    if (animated) setPic(musicInfo.pic)
  }, [musicInfo.pic, animated])

  useNavigationComponentDidAppear(componentId, () => {
    setAnimated(true)
  })

  return (
    <View style={{ flexGrow: 3, flexShrink: 1, flexBasis: 0, minWidth: 0, padding: px(24) }}>
      <View
        style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}
        onLayout={(event) => {
          const { width, height } = event.nativeEvent.layout
          setSlot((prev) => (prev.width === width && prev.height === height ? prev : { width, height }))
        }}
      >
        {side > 0
          ? (
              <View style={{
                width: side,
                height: side,
                borderRadius: px(24),
                overflow: 'hidden',
                borderWidth: 1,
                borderColor: theme['art-stroke'],
              }}>
                <Image
                  url={pic}
                  resizeMode="contain"
                  nativeID={NAV_SHEAR_NATIVE_IDS.playDetail_pic}
                  style={{ width: '100%', height: '100%' }}
                />
              </View>
            )
          : null}
      </View>
    </View>
  )
})
