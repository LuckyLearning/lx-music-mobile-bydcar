// SPDX-License-Identifier: Apache-2.0
package lx.compat

import java.net.URLDecoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Utility input above the preload per-argument cap. Not turned into an empty crypto result. */
class InputQuotaError(message: String) : Error(message)

/**
 * Legacy host utility ABI from QuickJS.java / AES.java / RSA.java.
 * Failures return "" the way those Java methods do. This is protocol compatibility, not an
 * application crypto boundary: MD5 and raw RSA stay out of any new security design.
 */
object Binary {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val a = bytes[i++].toInt() and 255
            val b = if (i < bytes.size) bytes[i++].toInt() and 255 else -1
            val c = if (i < bytes.size) bytes[i++].toInt() and 255 else -1
            out.append(ALPHABET[a shr 2])
            out.append(ALPHABET[((a and 3) shl 4) or if (b < 0) 0 else b shr 4])
            out.append(if (b < 0) '=' else ALPHABET[((b and 15) shl 2) or if (c < 0) 0 else c shr 6])
            out.append(if (c < 0) '=' else ALPHABET[c and 63])
        }
        return out.toString()
    }

    fun decode(input: String): ByteArray {
        val raw = input.filterNot { it == ' ' || it == '\t' || it == '\r' || it == '\n' }
        require(raw.length % 4 != 1) { "bad base64" }
        val s = raw.padEnd((raw.length + 3) / 4 * 4, '=')
        val out = ArrayList<Byte>(s.length / 4 * 3)
        var i = 0
        while (i < s.length) {
            val last = i + 4 == s.length
            val q = s.substring(i, i + 4)
            require(q[0] != '=' && q[1] != '=')
            require(q[2] != '=' || q[3] == '=')
            require(last || '=' !in q)
            val a = IntArray(4) { n ->
                if (q[n] == '=') 0 else ALPHABET.indexOf(q[n]).also { require(it >= 0) }
            }
            out.add(((a[0] shl 2) or (a[1] shr 4)).toByte())
            if (q[2] != '=') out.add((((a[1] and 15) shl 4) or (a[2] shr 2)).toByte())
            if (q[3] != '=') out.add((((a[2] and 3) shl 6) or a[3]).toByte())
            i += 4
        }
        return out.toByteArray()
    }
}

object LegacyCrypto {
    private const val MAX_ARG = 1_048_576

    fun invoke(op: String, args: List<Any?>): Any? {
        return try {
            when (op) {
                "str2b64" -> Binary.encode(arg(args, 0).toByteArray(Charsets.UTF_8))
                "b642buf" -> signedJson(Binary.decode(arg(args, 0)))
                "md5" -> md5(arg(args, 0))
                "aes" -> aes(args)
                "rsa" -> rsa(args)
                else -> error("Unknown utility")
            }
        } catch (quota: InputQuotaError) {
            throw quota
        } catch (_: Exception) {
            ""
        }
    }

    /** Same character layout as QuickJS.java utils_b642buf: a JSON array of Java signed bytes. */
    fun signedJson(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 4 + 2)
        out.append('[')
        for (i in bytes.indices) {
            if (i > 0) out.append(',')
            out.append(bytes[i].toInt())
        }
        out.append(']')
        return out.toString()
    }

    private fun arg(args: List<Any?>, index: Int): String {
        val value = args.getOrNull(index).str()
        if (value.length > MAX_ARG) throw InputQuotaError("utility input")
        return value
    }

    private fun md5(encoded: String): String {
        val text = URLDecoder.decode(encoded, "UTF-8")
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun aesCipher(mode: String): Cipher {
        val names = when (mode) {
            // Android Cipher accepts PKCS7Padding. JDK names the same AES padding PKCS5Padding.
            "AES/CBC/PKCS7Padding" -> listOf("AES/CBC/PKCS7Padding", "AES/CBC/PKCS5Padding")
            "AES" -> listOf("AES")
            else -> error("AES mode")
        }
        var last: Exception? = null
        for (name in names) {
            try {
                return Cipher.getInstance(name)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("AES")
    }

    private fun aes(args: List<Any?>): String {
        // AES.encrypt(String,String,String,String): empty iv selects the no-IV overload.
        // A non-empty iv is copied into a 16-byte buffer (truncated or zero-padded) even when
        // the mode name is the preload ECB alias "AES".
        val mode = arg(args, 3)
        val ivText = arg(args, 2)
        val cipher = aesCipher(mode)
        val data = Binary.decode(arg(args, 0))
        val key = SecretKeySpec(Binary.decode(arg(args, 1)), "AES")
        if (ivText.isEmpty()) {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } else {
            val raw = Binary.decode(ivText)
            val iv = ByteArray(16)
            System.arraycopy(raw, 0, iv, 0, minOf(raw.size, 16))
            cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        }
        return Binary.encode(cipher.doFinal(data))
    }

    private fun rsa(args: List<Any?>): String {
        val data = Binary.decode(arg(args, 0))
        val mode = arg(args, 2)
        if (mode != "RSA/ECB/NoPadding" && mode != "RSA/ECB/OAEPWithSHA1AndMGF1Padding") error("RSA mode")
        val keyBytes = Binary.decode(arg(args, 1).trim())
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        val cipher = Cipher.getInstance(mode)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Binary.encode(cipher.doFinal(data))
    }
}
