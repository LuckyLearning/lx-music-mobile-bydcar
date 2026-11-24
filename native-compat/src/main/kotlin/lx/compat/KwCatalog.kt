// SPDX-License-Identifier: Apache-2.0
package lx.compat

/**
 * Focused port of the Kuwo search and playlist *parsers and URL builders* in src/utils/musicSdk/kw.
 * These functions do not open sockets. The search and playlist endpoints are legacy cleartext
 * hosts; a caller must put that host in PlayerTransportPolicy or NetworkPolicy before any fetch.
 * getMusicUrl still belongs to the user script, not this adapter.
 */
object KwCatalog {
    private val quality = Regex("level:(\\w+),bitrate:(\\d+),format:(\\w+),size:([\\w.]+)")

    fun decodeName(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            if (value[i] != '&') {
                out.append(value[i])
                i++
                continue
            }
            val end = value.indexOf(';', i)
            if (end < 0) {
                out.append(value.substring(i))
                break
            }
            val token = value.substring(i + 1, end)
            val decoded = when (token) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> "\u00A0"
                else -> if (token.startsWith("#x") || token.startsWith("#X")) token.drop(2).toIntOrNull(16)?.toChar()
                else if (token.startsWith("#")) token.drop(1).toIntOrNull()?.toChar()
                else null
            }
            if (decoded == null) out.append(value.substring(i, end + 1)) else out.append(decoded)
            i = end + 1
        }
        return out.toString()
    }

    fun formatSinger(raw: String): String = raw.replace("&", "、")

    fun formatPlayTime(seconds: Int): String {
        if (seconds <= 0) return "--/--"
        val minutes = seconds / 60
        val rest = seconds % 60
        return minutes.toString().padStart(2, '0') + ":" + rest.toString().padStart(2, '0')
    }

    fun searchUrl(query: String, page: Int, limit: Int): String {
        require(page >= 1 && limit in 1..100 && query.length <= 200)
        val encoded = LegacyRequest.encodeURIComponent(query)
        return "http://search.kuwo.cn/r.s?client=kt&all=$encoded&pn=${page - 1}&rn=$limit&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
    }

    const val SEARCH_HOST = "search.kuwo.cn"

    fun playlistListUrl(sortId: String, id: String?, type: String?, page: Int): String? {
        require(page >= 1 && sortId.length <= 32)
        if (id.isNullOrEmpty()) {
            return "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList?loginUid=0&loginSid=0&appUid=76039576&&pn=$page&rn=36&order=$sortId"
        }
        return when (type) {
            "10000" -> "http://wapi.kuwo.cn/api/pc/classify/playlist/getTagPlayList?loginUid=0&loginSid=0&appUid=76039576&pn=$page&id=$id&rn=36"
            "43" -> "http://mobileinterfaces.kuwo.cn/er.s?type=get_pc_qz_data&f=web&id=$id&prod=pc"
            else -> null
        }
    }

    fun playlistDetailUrl(id: String, page: Int): String {
        require(id.length in 1..32 && page >= 1 && id.all { it.isDigit() })
        return "http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=$id&pn=${page - 1}&rn=1000&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1"
    }

    data class Track(
        val name: String,
        val singer: String,
        val songmid: String,
        val albumName: String,
        val interval: String,
        val qualities: List<String>,
    )

    fun parseSearchList(rows: List<Map<String, Any?>>): List<Track>? {
        val result = ArrayList<Track>()
        for (info in rows) {
            val minfo = info["N_MINFO"] as? String ?: return null
            val qualities = ArrayList<String>()
            for (part in minfo.split(';')) {
                val match = quality.matchEntire(part) ?: continue
                when (match.groupValues[2]) {
                    "4000" -> qualities.add("flac24bit")
                    "2000" -> qualities.add("flac")
                    "320" -> qualities.add("320k")
                    "128" -> qualities.add("128k")
                }
            }
            qualities.reverse()
            val rid = (info["MUSICRID"] as? String ?: "").removePrefix("MUSIC_")
            val duration = (info["DURATION"] as? String)?.toIntOrNull() ?: 0
            result.add(
                Track(
                    name = decodeName(info["SONGNAME"] as? String),
                    singer = formatSinger(decodeName(info["ARTIST"] as? String)),
                    songmid = rid,
                    albumName = decodeName(info["ALBUM"] as? String),
                    interval = formatPlayTime(duration),
                    qualities = qualities,
                )
            )
        }
        return result
    }

    data class Tag(val id: String, val name: String, val parentId: String? = null, val parentName: String? = null)

    fun hotTags(rows: List<Map<String, Any?>>): List<Tag> = rows.map { item ->
        Tag(id = "${item["id"]}-${item["digest"]}", name = item["name"].str())
    }

    fun tagGroups(groups: List<Map<String, Any?>>): List<Tag> {
        val out = ArrayList<Tag>()
        for (group in groups) {
            val parentId = group["id"].str()
            val parentName = group["name"].str()
            val data = group["data"].list()
            for (item in data) {
                val row = item.obj()
                out.add(Tag(id = row["id"].str(), name = row["name"].str(), parentId = parentId, parentName = parentName))
            }
        }
        return out
    }
}
