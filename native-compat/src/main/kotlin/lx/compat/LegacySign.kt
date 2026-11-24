// SPDX-License-Identifier: Apache-2.0
package lx.compat

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Signing used by the existing musicSdk clients.
 * kg: src/utils/musicSdk/kg/util.js signatureParams
 * tx: src/utils/musicSdk/tx/utils/crypto.js zzcSign, with SHA-1 from CryptoModule.sha1
 * mg: src/utils/musicSdk/mg/musicSearch.js createSignature
 * wy: src/utils/musicSdk/wy/utils/crypto.js eapi, AES mode "AES" (ECB/PKCS5) from AES.java
 */
object LegacySign {
    private const val KG_ANDROID = "OIlwieks28dk2k092lksi2UIkp"
    private const val KG_WEB = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
    private val TX_PART_1 = intArrayOf(23, 14, 6, 36, 16, 40, 7, 19)
    private val TX_PART_2 = intArrayOf(16, 1, 32, 12, 19, 27, 8, 5)
    private val TX_SCRAMBLE = intArrayOf(89, 39, 179, 150, 218, 82, 58, 252, 177, 52, 186, 123, 120, 64, 242, 133, 143, 161, 121, 179)
    private const val MG_DEVICE = "963B7AA0D21511ED807EE5846EC87D20"
    private const val MG_SIGNATURE = "6cdc72a439cef99a3418d2a78aa28c73"
    private const val WY_KEY = "e82ckenh8dichen8"

    fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun kg(params: String, platform: String = "android", body: String = ""): String {
        val key = if (platform == "web") KG_WEB else KG_ANDROID
        val joined = params.split("&").sorted().joinToString("")
        return md5(key + joined + body + key)
    }

    fun tx(json: String): String {
        val hash = sha1(json)
        val part1 = TX_PART_1.joinToString("") { if (it in hash.indices) hash[it].toString() else "" }
        val part2 = TX_PART_2.joinToString("") { if (it in hash.indices) hash[it].toString() else "" }
        val mixed = ByteArray(TX_SCRAMBLE.size) { index ->
            (TX_SCRAMBLE[index] xor hash.substring(index * 2, index * 2 + 2).toInt(16)).toByte()
        }
        val b64 = Binary.encode(mixed).replace(Regex("[\\\\/+=]"), "")
        return "zzc$part1$b64$part2".lowercase()
    }

    fun mg(time: String, text: String): Pair<String, String> {
        val sign = md5("$text$MG_SIGNATURE" + "yyapp2d16148780a1dcc7408e06336b98cfd50" + MG_DEVICE + time)
        return sign to MG_DEVICE
    }

    fun wyParams(url: String, text: String): String {
        val digest = md5("nobody${url}use${text}md5forencrypt")
        val data = "$url-36cd479b6b5-$text-36cd479b6b5-$digest"
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(WY_KEY.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it.toInt() and 255) }
    }

    private fun sha1(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
