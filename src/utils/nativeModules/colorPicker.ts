import { NativeModules } from 'react-native'

import { colorSupportsAlpha, parseColorValue } from '@/utils/colorValue'

const { ColorPickerModule } = NativeModules

/**
 * 打开原生 ColorPickerView 对话框。
 * 取消、失败或返回的色值不合法时得到 null，调用方不要改已保存的颜色。
 * 成功时得到和 ColorValueField 以前一样的 #RRGGBBAA。
 */
export const pickLyricColor = async(
  color: string,
  title: string,
  confirmLabel: string,
  cancelLabel: string,
): Promise<string | null> => {
  if (!ColorPickerModule || typeof ColorPickerModule.pick != 'function') return null
  const picked: unknown = await ColorPickerModule.pick(color, colorSupportsAlpha(color), title, confirmLabel, cancelLabel)
  if (typeof picked != 'string' || !picked) return null
  const parsed = parseColorValue(picked)
  return parsed ? parsed.hex : null
}
