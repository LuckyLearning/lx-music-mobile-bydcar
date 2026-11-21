/**
 * 开机后台任务出不出声，只看「启动后自动播放」。
 * 「开机自启」只负责把播放服务在后台拉起来，不打开主界面，也不应单独强制起播。
 */
export type BootPlaybackPlan = 'play' | 'prepare'

export const planBootPlayback = (startupAutoPlay: boolean): BootPlaybackPlan => {
  return startupAutoPlay ? 'play' : 'prepare'
}
