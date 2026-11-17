// [BYD] 车机横屏外壳：左侧 CarShell/NavRail 车机导航 + 右侧保留原内容区。
// 与上游差异：仅把外层布局换成车机外壳；Header/Main/PlayerBar 及共享 Views 保持原样，
// 竖屏（Vertical）与 Home/Views 完全不受影响。详见 docs/BYD-修改清单.md。
import { View } from 'react-native'
import { CarShell } from '@/components/car'
import PlayerBar from '@/components/player/PlayerBar'
import Header from './Header'
import Main from './Main'
import { createStyle } from '@/utils/tools'

const styles = createStyle({
  content: {
    flex: 1,
    overflow: 'hidden',
  },
})

export default () => {
  return (
    <CarShell>
      <View style={styles.content}>
        <Header />
        <Main />
        <PlayerBar isHome />
      </View>
    </CarShell>
  )
}
