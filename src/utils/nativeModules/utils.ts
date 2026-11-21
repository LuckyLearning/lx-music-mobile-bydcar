import { AppState, NativeEventEmitter, NativeModules, Platform } from 'react-native'

const { UtilsModule } = NativeModules

export const exitApp = UtilsModule.exitApp

export const getSupportedAbis = UtilsModule.getSupportedAbis

export const installApk = (filePath: string, fileProviderAuthority: string) => UtilsModule.installApk(filePath, fileProviderAuthority)


export const screenkeepAwake = () => {
  if (global.lx.isScreenKeepAwake) return
  global.lx.isScreenKeepAwake = true
  UtilsModule.screenkeepAwake()
}
export const screenUnkeepAwake = () => {
  // console.log('screenUnkeepAwake')
  if (!global.lx.isScreenKeepAwake) return
  global.lx.isScreenKeepAwake = false
  UtilsModule.screenUnkeepAwake()
}

export const getWIFIIPV4Address = UtilsModule.getWIFIIPV4Address as () => Promise<string>

export const getDeviceName = async(): Promise<string> => {
  return UtilsModule.getDeviceName().then((deviceName: string) => deviceName || 'Unknown')
}

export const isNotificationsEnabled = UtilsModule.isNotificationsEnabled as () => Promise<boolean>

export const requestNotificationPermission = async() => new Promise<boolean>((resolve) => {
  let subscription = AppState.addEventListener('change', (state) => {
    if (state != 'active') return
    subscription.remove()
    setTimeout(() => {
      void isNotificationsEnabled().then(resolve)
    }, 1000)
  })
  UtilsModule.openNotificationPermissionActivity().then((result: boolean) => {
    if (result) return
    subscription.remove()
    resolve(false)
  })
})

export const shareText = async(shareTitle: string, title: string, text: string): Promise<void> => {
  UtilsModule.shareText(shareTitle, title, text)
}

export const getSystemLocales = async(): Promise<string> => {
  return UtilsModule.getSystemLocales()
}

export const onScreenStateChange = (handler: (state: 'ON' | 'OFF') => void): () => void => {
  // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
  const eventEmitter = new NativeEventEmitter(UtilsModule)
  const eventListener = eventEmitter.addListener('screen-state', event => {
    handler(event.state as 'ON' | 'OFF')
  })

  return () => {
    eventListener.remove()
  }
}

export const getWindowSize = async(): Promise<{ width: number, height: number }> => {
  return UtilsModule.getWindowSize()
}

export const onWindowSizeChange = (handler: (size: { width: number, height: number }) => void): () => void => {
  UtilsModule.listenWindowSizeChanged()
  // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
  const eventEmitter = new NativeEventEmitter(UtilsModule)
  const eventListener = eventEmitter.addListener('screen-size-changed', event => {
    handler(event as { width: number, height: number })
  })

  return () => {
    eventListener.remove()
  }
}

/** 开机后台服务是否已在跑。用于在 bundle 刚加载、任务体还没执行时拦住主界面。 */
export const isBootPlaybackRunning = (): boolean => {
  if (Platform.OS !== 'android' || typeof UtilsModule?.isBootPlaybackRunning !== 'function') return false
  try {
    return UtilsModule.isBootPlaybackRunning() === true
  } catch {
    return false
  }
}

/** 把「开机自启」写进原生偏好。开机广播到达时 JS 还没起来，接收器只读这份值。iOS 无此能力。 */
export const setBootAutostart = (enabled: boolean) => {
  if (Platform.OS !== 'android' || typeof UtilsModule?.setBootAutostart !== 'function') return
  UtilsModule.setBootAutostart(enabled)
}

export interface BootStatusSnapshot {
  at: number
  action: string
  /** `true` / `false` / `pending` */
  ok: string
  reason: string
}

/** 开机后台启动的结论。服务据此撤掉启动通知。 */
export const finishBootAutostart = (ok: boolean, reason: string) => {
  if (Platform.OS !== 'android' || typeof UtilsModule?.finishBootAutostart !== 'function') return
  try {
    UtilsModule.finishBootAutostart(ok, reason)
  } catch (err) {
    console.log('LXBoot finishBootAutostart failed', err)
  }
}

export const getBootStatus = async(): Promise<BootStatusSnapshot | null> => {
  if (Platform.OS !== 'android' || typeof UtilsModule?.getBootStatus !== 'function') return null
  return UtilsModule.getBootStatus()
}

export const isIgnoringBatteryOptimization = async(): Promise<boolean> => {
  return UtilsModule.isIgnoringBatteryOptimization()
}

export const requestIgnoreBatteryOptimization = async() => new Promise<boolean>((resolve) => {
  let subscription = AppState.addEventListener('change', (state) => {
    if (state != 'active') return
    subscription.remove()
    setTimeout(() => {
      void isIgnoringBatteryOptimization().then(resolve)
    }, 1000)
  })
  UtilsModule.requestIgnoreBatteryOptimization().then((result: boolean) => {
    if (result) return
    subscription.remove()
    resolve(false)
  })
})
