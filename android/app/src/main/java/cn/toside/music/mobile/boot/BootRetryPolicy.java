package cn.toside.music.mobile.boot;

/**
 * 开机后台起播的重试时机。从听风 {@code BootRetryPolicy} 对齐：0s / 4s / 10s。
 *
 * 开机要跟三件事赛跑：播放服务还没连上、上次的列表还没从存储读出来、
 * 车机音频设备还没就绪。一次不成就放弃，用户看到的就是「开关开了，开机没声音」。
 * 三次的等待仍落在 20 秒内，不会变成「开机很久之后突然出声」。
 *
 * 确认窗口比听风的 1.2 秒长：听风播的是本地文件，本应用要先取在线地址。
 * JS 侧的默认值必须与这里一致。
 */
public final class BootRetryPolicy {

  public static final long[] DELAYS_MS = {0L, 4_000L, 10_000L};

  /** 每次尝试里等待真正进入播放/缓冲的最长时间。 */
  public static final long CONFIRM_MS = 8_000L;

  private BootRetryPolicy() {}

  public static int attempts() {
    return DELAYS_MS.length;
  }

  /** 越界时取最后一次，调用方用 {@link #isExhausted} 收尾。 */
  public static long waitMs(int attempt) {
    if (attempt < 0) return DELAYS_MS[0];
    if (attempt >= DELAYS_MS.length) return DELAYS_MS[DELAYS_MS.length - 1];
    return DELAYS_MS[attempt];
  }

  public static boolean hasMore(int attempt) {
    return attempt + 1 < DELAYS_MS.length;
  }

  public static boolean isExhausted(int attempt) {
    return attempt >= DELAYS_MS.length - 1;
  }
}
