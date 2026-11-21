import { AppRegistry } from 'react-native'

import { setBootBackground } from '@/core/boot/mode'

// 必须在 app.ts 拉导航/界面之前注册。任务体仍延后加载。
AppRegistry.registerHeadlessTask('BootAutostart', () => (data: { delays?: number[], confirmMs?: number }) => {
  setBootBackground(true)
  return import('@/core/boot/background').then(({ runBackgroundAutostart }) => runBackgroundAutostart(data))
})
