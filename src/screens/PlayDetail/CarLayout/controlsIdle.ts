export interface ControlsIdleCallbacks {
  onVisibleChange: (visible: boolean) => void
  setTimeoutFn?: (cb: () => void, ms: number) => ReturnType<typeof setTimeout>
  clearTimeoutFn?: (id: ReturnType<typeof setTimeout>) => void
}

/**
 * 车机播放页控件显隐状态机：
 * - 无操作 idleMs 后与底部播放栏一起收起；
 * - 任意触控/点击后一并恢复并重新计时；
 * - 设置弹窗打开期间暂停自动收起，关闭后恢复并重新计时。
 */
export const createControlsIdleController = (idleMs: number, callbacks: ControlsIdleCallbacks) => {
  const schedule = callbacks.setTimeoutFn ?? setTimeout
  const cancel = callbacks.clearTimeoutFn ?? clearTimeout
  let visible = true
  let paused = false
  let disposed = false
  let timer: ReturnType<typeof setTimeout> | null = null

  const clearTimer = () => {
    if (timer != null) {
      cancel(timer)
      timer = null
    }
  }

  const armTimer = () => {
    clearTimer()
    if (paused || disposed) return
    timer = schedule(() => {
      timer = null
      if (paused || disposed) return
      if (visible) {
        visible = false
        callbacks.onVisibleChange(false)
      }
    }, idleMs)
  }

  const noteInteraction = () => {
    if (disposed) return
    if (!visible) {
      visible = true
      callbacks.onVisibleChange(true)
    }
    armTimer()
  }

  const pause = () => {
    if (disposed) return
    paused = true
    clearTimer()
    if (!visible) {
      visible = true
      callbacks.onVisibleChange(true)
    }
  }

  const resume = () => {
    if (disposed) return
    paused = false
    noteInteraction()
  }

  const dispose = () => {
    disposed = true
    clearTimer()
  }

  return {
    isVisible: () => visible,
    isPaused: () => paused,
    noteInteraction,
    pause,
    resume,
    dispose,
  }
}
