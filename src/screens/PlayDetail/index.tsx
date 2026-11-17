import { useEffect } from 'react'
import { useWindowSize } from '@/utils/hooks'
import { useSettingValue } from '@/store/setting/hook'

import Vertical from './Vertical'
import Horizontal from './Horizontal'
import CarLayout from './CarLayout'
import PageContent from '@/components/PageContent'
import StatusBar from '@/components/common/StatusBar'
import { setComponentId } from '@/core/common'
import { COMPONENT_IDS } from '@/config/constant'
import { selectPlayerLayout } from './playerLayout'

export default ({ componentId }: { componentId: string }) => {
  const windowSize = useWindowSize()
  const carLayout = useSettingValue('player.carLayout')
  const layout = selectPlayerLayout(windowSize.width, windowSize.height, carLayout)

  useEffect(() => {
    setComponentId(COMPONENT_IDS.playDetail, componentId)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <PageContent>
      <StatusBar />
      {
        layout == 'car'
          ? <CarLayout componentId={componentId} />
          : layout == 'horizontal'
            ? <Horizontal componentId={componentId} />
            : <Vertical componentId={componentId} />
      }
    </PageContent>
  )
}
