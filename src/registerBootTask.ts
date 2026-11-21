import { AppRegistry } from 'react-native'

import { beginBootBackground } from '@/core/boot/mode'

// 必须在 app.ts 拉导航/界面之前注册。任务体仍延后加载。
AppRegistry.registerHeadlessTask('BootAutostart', () => (data: { token: number, delays?: number[], confirmMs?: number }) => {
  beginBootBackground(data.token)
  return import('@/core/boot/background').then(({ runBackgroundAutostart }) => runBackgroundAutostart(data))
})
