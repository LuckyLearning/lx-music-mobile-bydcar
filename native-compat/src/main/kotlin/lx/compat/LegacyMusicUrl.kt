// SPDX-License-Identifier: Apache-2.0
package lx.compat

/**
 * Same request shape as src/core/init/userApi/index.ts musicUrl:
 * data.source, data.action, data.info.type, data.info.musicInfo.
 * The native player must not replace this with another URL service.
 */
object LegacyMusicUrl {
    fun requestJson(source: String, quality: String, musicInfo: Map<String, Any?>, requestKey: String = "native"): String {
        return Json.stringify(
            mapOf(
                "requestKey" to requestKey,
                "data" to mapOf(
                    "source" to source,
                    "action" to "musicUrl",
                    "info" to mapOf(
                        "type" to quality,
                        "musicInfo" to musicInfo,
                    ),
                ),
            ),
        )
    }
}
