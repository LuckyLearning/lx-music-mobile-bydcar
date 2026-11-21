/** Boot mode suppresses startup autoplay and permission dialogs, never explicit foreground UI. */
let background = false
let session = 0
let cancelled = false
let cancelHandler: (() => void) | null = null

export const setBootBackground = (enabled: boolean) => {
  background = enabled
  if (enabled) cancelled = false
}
export const isBootBackground = () => background
export const isBootCancelled = () => background && cancelled
export const onBootCancel = (handler: (() => void) | null) => { cancelHandler = handler }
export const cancelBootBackground = () => {
  if (!background || cancelled) return
  cancelled = true
  cancelHandler?.()
}

/** Old timed-out JS may settle after a newer native generation has begun. */
export const beginBootBackground = (token: number) => {
  if (token < session) return
  session = token
  setBootBackground(true)
}
export const isBootSession = (token: number) => background && token === session
