/** 开机后台起播进行中。这段时间不要弹权限框，也不要把静音占位当成「这首播完了」。 */
let background = false

export const setBootBackground = (enabled: boolean) => {
  background = enabled
}

export const isBootBackground = () => background
