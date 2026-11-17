import { isHorizontalMode } from '@/utils/tools'

/** 比亚迪系统 Dock 高度。这台车机 WindowInsets 经常报 0，必须自己留出。 */
export const CAR_DOCK_RESERVE_DP = 56

/** 底部播放条无操作后收起的等待时间。 */
export const CAR_CONTROLS_IDLE_MS = 5000

export type PlayerLayoutMode = 'car' | 'horizontal' | 'vertical'

/**
 * 车机布局只在「设置开启且宽大于高」时使用。
 * 关闭时横屏仍走原来的播放页（宽高比 > 1.2 用横屏页，否则竖屏页）。
 * 竖屏（高大于等于宽）始终用原来的竖屏播放页。
 */
export const selectPlayerLayout = (width: number, height: number, carLayoutEnabled: boolean): PlayerLayoutMode => {
  if (carLayoutEnabled && width > height) return 'car'
  if (isHorizontalMode(width, height)) return 'horizontal'
  return 'vertical'
}

/** 封面保持 1:1，列比正方形高时上下留空，不拉高、不裁成竖图。 */
export const squareCoverSide = (columnWidth: number, columnHeight: number) => {
  if (columnWidth <= 0 || columnHeight <= 0) return 0
  return Math.min(columnWidth, columnHeight)
}
