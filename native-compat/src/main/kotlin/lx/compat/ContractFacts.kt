// SPDX-License-Identifier: Apache-2.0
package lx.compat

/**
 * Boundaries taken from the mobile client at ad77216f, not from a desktop source host.
 * User scripts negotiate musicUrl (and local lyric/pic). Search and playlist metadata live in
 * src/utils/musicSdk and are not provided by a URL-resolver script.
 */
object ContractFacts {
    const val baselineSha = "ad77216fdb3d195072cdddbe687006715971ecad"
    const val lxVersion = "2.0.0"
    const val lxEnv = "mobile"
    val userApiSources = listOf("kw", "kg", "tx", "wy", "mg", "local")
    val onlineScriptActions = listOf("musicUrl")
    val localScriptActions = listOf("musicUrl", "lyric", "pic")
    val onlineQualities = listOf("128k", "320k", "flac", "flac24bit")
    val builtInPlatforms = listOf("kw", "kg", "tx", "wy", "mg")
    const val outerTimeoutMs = 20_000L
    const val httpDefaultTimeoutMs = 13_000L
    const val httpMaxTimeoutMs = 60_000L
    const val musicUrlMaxChars = 2048
    const val lyricMaxChars = 51200
    const val sideLyricMaxExclusive = 5120
    const val lxLyricMaxExclusive = 8192
}
