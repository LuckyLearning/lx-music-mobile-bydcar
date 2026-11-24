// SPDX-License-Identifier: Apache-2.0
package lx.compat

import java.net.InetAddress
import java.util.Locale
import kotlin.math.floor
import kotlin.math.ln

/**
 * Search parsers for the built-in platforms in src/utils/musicSdk.
 * getMusicUrl stays with the reviewed script. These methods do not read old app data.
 * QQ and NetEase signed request clients are not ported; their parsers run on fixtures.
 * Kugou search is plain HTTPS. Kuwo search is cleartext and is fetched only when the caller
 * names that host.
 */
data class CatalogTrack(
    val source: String,
    val name: String,
    val singer: String,
    val songmid: String,
    val albumName: String,
    val interval: String,
    val qualities: List<String>,
    val hashes: Map<String, String> = emptyMap(),
    val sizes: Map<String, Long> = emptyMap(),
)

data class CatalogPage(val tracks: List<CatalogTrack>, val total: Int, val source: String)

data class LiveSearch(
    val status: Int,
    val page: CatalogPage?,
    val upstream: Boolean,
    val clientError: String?,
)

object CatalogText {
    fun sizeLabel(size: Long): String {
        if (size <= 0L) return "0 B"
        val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
        val number = floor(ln(size.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = size.toDouble() / Math.pow(1024.0, number.toDouble())
        return String.format(Locale.US, "%.2f %s", value, units[number])
    }

    fun id(value: Any?): String = when (value) {
        is Number -> {
            val number = value.toDouble()
            if (number % 1.0 == 0.0 && kotlin.math.abs(number) <= 9_007_199_254_740_992.0) number.toLong().toString()
            else number.toString()
        }
        is String -> value
        else -> ""
    }

    fun singerList(value: Any?, nameKey: String = "name"): String {
        val rows = value as? List<*>
        if (rows == null) return KwCatalog.decodeName(value?.toString() ?: "")
        val names = rows.mapNotNull { row ->
            val name = (row as? Map<*, *>)?.get(nameKey)?.toString()
            if (name.isNullOrEmpty()) null else name
        }
        return KwCatalog.decodeName(names.joinToString("、"))
    }
}

object KgCatalog {
    const val SEARCH_HOST = "songsearch.kugou.com"

    fun searchUrl(query: String, page: Int, limit: Int): String {
        require(query.length in 1..80 && page >= 1 && limit in 1..50)
        val encoded = LegacyRequest.encodeURIComponent(query)
        return "https://$SEARCH_HOST/song_search_v2?keyword=$encoded&page=$page&pagesize=$limit&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1&privilege_filter=0&area_code=1"
    }

    /** Matches musicSdk/kg/musicSearch.js, including the group-row key that reuses the parent id. */
    fun parse(body: Any?): CatalogPage? {
        val root = body as? Map<*, *> ?: return null
        if ((root["error_code"] as? Number)?.toInt() != 0) return null
        val data = root["data"] as? Map<*, *> ?: return null
        val rows = data["lists"] as? List<*> ?: return null
        val seen = HashSet<String>()
        val tracks = ArrayList<CatalogTrack>()
        for (row in rows) {
            val item = row as? Map<*, *> ?: return null
            val key = CatalogText.id(item["Audioid"]) + (item["FileHash"]?.toString() ?: "")
            if (seen.add(key)) tracks.add(track(item))
            val group = item["Grp"] as? List<*> ?: emptyList<Any?>()
            for (child in group) {
                val nested = child as? Map<*, *> ?: continue
                val childKey = CatalogText.id(item["Audioid"]) + (item["FileHash"]?.toString() ?: "")
                if (!seen.add(childKey)) continue
                tracks.add(track(nested))
            }
        }
        val total = (data["total"] as? Number)?.toInt() ?: tracks.size
        return CatalogPage(tracks, total, "kg")
    }

    fun live(policy: NetworkPolicy, query: String): LiveSearch =
        fetch(policy, searchUrl(query, 1, 10), ::parse, listOf("user-agent" to "Mozilla/5.0"))

    private fun track(item: Map<*, *>): CatalogTrack {
        val qualities = ArrayList<String>()
        val hashes = linkedMapOf<String, String>()
        val sizes = linkedMapOf<String, Long>()
        fun keep(quality: String, sizeKey: String, hashKey: String) {
            val size = long(item[sizeKey])
            if (size == 0L) return
            qualities.add(quality)
            sizes[quality] = size
            val hash = item[hashKey]?.toString()?.trim().orEmpty()
            if (hash.matches(Regex("[A-Fa-f0-9]{32}"))) hashes[quality] = hash.lowercase()
        }
        keep("128k", "FileSize", "FileHash")
        keep("320k", "HQFileSize", "HQFileHash")
        keep("flac", "SQFileSize", "SQFileHash")
        keep("flac24bit", "ResFileSize", "ResFileHash")
        val suffix = item["Suffix"]?.toString() ?: ""
        val title = (item["OriSongName"]?.toString() ?: "") + if (suffix.isEmpty()) "" else " $suffix"
        return CatalogTrack(
            source = "kg",
            name = KwCatalog.decodeName(title),
            singer = CatalogText.singerList(item["Singers"]),
            songmid = CatalogText.id(item["Audioid"]),
            albumName = KwCatalog.decodeName(item["AlbumName"]?.toString()),
            interval = KwCatalog.formatPlayTime(long(item["Duration"]).toInt()),
            qualities = qualities,
            hashes = hashes,
            sizes = sizes,
        )
    }
}

/** Public Kugou song-info lookup. The hash selects the requested quality; the response bitrate is recorded as returned. */
object KgPlay {
    const val INFO_HOST = "m.kugou.com"

    data class Playable(val url: String, val host: String, val bitrate: Int, val fileSize: Long, val quality: String)

    fun infoUrl(hash: String): String {
        require(hash.matches(Regex("[A-Fa-f0-9]{32}")))
        return "https://$INFO_HOST/app/i/getSongInfo.php?cmd=playInfo&hash=$hash"
    }

    fun parse(body: Any?, quality: String): Playable? {
        val root = body as? Map<*, *> ?: return null
        if ((root["status"] as? Number)?.toInt() != 1) return null
        val url = root["url"]?.toString() ?: return null
        if (!url.startsWith("https://") || url.length > 2048) return null
        val host = try {
            java.net.URI(url).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return null
        if (!host.endsWith(".kugou.com") && host != "kugou.com") return null
        if (Addresses.parse(host) != null && !Addresses.isPublic(host)) return null
        return Playable(url, host, (root["bitRate"] as? Number)?.toInt() ?: 0, long(root["fileSize"]), quality)
    }

    fun live(policy: NetworkPolicy, hash: String, quality: String): LivePlay {
        return try {
            val exchange = PinnedHttps(policy, TlsConnector).execute(
                policy.admit(infoUrl(hash), "GET", listOf("user-agent" to "Mozilla/5.0"), null, 8_000, false),
            )
            if (exchange.statusCode != 200) return LivePlay(exchange.statusCode, null, true, null)
            val text = exchange.body.toString(Charsets.UTF_8).trim()
            if (!text.startsWith("{")) return LivePlay(exchange.statusCode, null, true, null)
            val playable = parse(Json.parse(text), quality)
            if (playable == null) LivePlay(exchange.statusCode, null, true, null) else LivePlay(exchange.statusCode, playable, false, null)
        } catch (e: Exception) {
            LivePlay(0, null, false, e.javaClass.simpleName)
        }
    }
}

data class LivePlay(val status: Int, val playable: KgPlay.Playable?, val upstream: Boolean, val clientError: String?)

object KwSearch {
    fun parseBody(body: Any?): List<KwCatalog.Track>? {
        val root = body as? Map<*, *> ?: return null
        val total = root["TOTAL"]?.toString()
        val show = root["SHOW"]?.toString()
        if (total != "0" && show == "0") return null
        val rows = root["abslist"] as? List<*> ?: return emptyList()
        return KwCatalog.parseSearchList(rows.map { (it as? Map<*, *>)?.entries?.associate { entry -> entry.key.toString() to entry.value } ?: return null })
    }

    fun live(query: String): LiveSearch {
        val policy = NetworkPolicy(
            cleartextHosts = setOf(KwCatalog.SEARCH_HOST),
            resolve = { host -> InetAddress.getAllByName(host).mapNotNull { it.hostAddress } },
        )
        val url = KwCatalog.searchUrl(query, 1, 5)
        return try {
            val exchange = PinnedHttps(policy, TlsConnector).execute(policy.admit(url, "GET", emptyList(), null, 8_000, false))
            classify(exchange.statusCode, exchange.body) { parsed ->
                val tracks = parseBody(parsed) ?: return@classify null
                CatalogPage(tracks.map {
                    CatalogTrack("kw", it.name, it.singer, it.songmid, it.albumName, it.interval, it.qualities)
                }, tracks.size, "kw")
            }
        } catch (e: Exception) {
            LiveSearch(0, null, false, e.javaClass.simpleName)
        }
    }
}

object TxCatalog {
    /** Parser only. The signed desktop search request is not ported. */
    fun parseSongs(rows: List<*>): List<CatalogTrack> {
        val tracks = ArrayList<CatalogTrack>()
        for (row in rows) {
            val item = row as? Map<*, *> ?: continue
            val file = item["file"] as? Map<*, *> ?: continue
            if (file["media_mid"]?.toString().isNullOrEmpty()) continue
            val qualities = ArrayList<String>()
            if (long(file["size_128mp3"]) != 0L) qualities.add("128k")
            if (long(file["size_320mp3"]) != 0L) qualities.add("320k")
            if (long(file["size_flac"]) != 0L) qualities.add("flac")
            if (long(file["size_hires"]) != 0L) qualities.add("flac24bit")
            val album = item["album"] as? Map<*, *>
            tracks.add(
                CatalogTrack(
                    source = "tx",
                    name = item["title"]?.toString() ?: "",
                    singer = CatalogText.singerList(item["singer"]),
                    songmid = CatalogText.id(item["mid"]),
                    albumName = album?.get("name")?.toString() ?: "",
                    interval = item["interval"]?.let { KwCatalog.formatPlayTime(long(it).toInt()) } ?: "",
                    qualities = qualities,
                )
            )
        }
        return tracks
    }
}

object WyCatalog {
    /** Parser only. The eapi request client is not ported. */
    fun parseResources(rows: List<*>): List<CatalogTrack>? {
        val tracks = ArrayList<CatalogTrack>()
        for (row in rows) {
            val wrapped = row as? Map<*, *> ?: return null
            val base = (wrapped["baseInfo"] as? Map<*, *>)?.get("simpleSongData") as? Map<*, *> ?: return null
            val privilege = base["privilege"] as? Map<*, *> ?: return null
            val qualities = ArrayList<String>()
            if (privilege["maxBrLevel"] == "hires") qualities.add("flac24bit")
            when ((privilege["maxbr"] as? Number)?.toInt()) {
                999000 -> qualities.addAll(listOf("flac", "320k", "128k"))
                320000 -> qualities.addAll(listOf("320k", "128k"))
                192000, 128000 -> qualities.add("128k")
            }
            qualities.reverse()
            val album = base["al"] as? Map<*, *>
            tracks.add(
                CatalogTrack(
                    source = "wy",
                    name = base["name"]?.toString() ?: "",
                    singer = CatalogText.singerList(base["ar"]),
                    songmid = CatalogText.id(base["id"]),
                    albumName = album?.get("name")?.toString() ?: "",
                    interval = KwCatalog.formatPlayTime((long(base["dt"]) / 1000L).toInt()),
                    qualities = qualities,
                )
            )
        }
        return tracks
    }
}

object MgCatalog {
    fun parseResultList(groups: List<*>): List<CatalogTrack> {
        val tracks = ArrayList<CatalogTrack>()
        val seen = HashSet<String>()
        for (group in groups) {
            val rows = group as? List<*> ?: continue
            for (row in rows) {
                val data = row as? Map<*, *> ?: continue
                val songId = data["songId"]?.toString() ?: continue
                val copyright = data["copyrightId"]?.toString() ?: continue
                if (songId.isEmpty() || copyright.isEmpty() || !seen.add(copyright)) continue
                val qualities = ArrayList<String>()
                val formats = data["audioFormats"] as? List<*> ?: emptyList<Any?>()
                for (format in formats) {
                    val type = (format as? Map<*, *>)?.get("formatType")?.toString() ?: continue
                    when (type) {
                        "PQ" -> qualities.add("128k")
                        "HQ" -> qualities.add("320k")
                        "SQ" -> qualities.add("flac")
                        "ZQ24" -> qualities.add("flac24bit")
                    }
                }
                tracks.add(
                    CatalogTrack(
                        source = "mg",
                        name = data["name"]?.toString() ?: "",
                        singer = CatalogText.singerList(data["singerList"]),
                        songmid = songId,
                        albumName = data["album"]?.toString() ?: "",
                        interval = KwCatalog.formatPlayTime(long(data["duration"]).toInt()),
                        qualities = qualities,
                    )
                )
            }
        }
        return tracks
    }
}

internal fun long(value: Any?): Long = (value as? Number)?.toLong() ?: 0L

private fun fetch(
    policy: NetworkPolicy,
    url: String,
    parse: (Any?) -> CatalogPage?,
    headers: List<Pair<String, String>> = emptyList(),
): LiveSearch {
    return try {
        val exchange = PinnedHttps(policy, TlsConnector).execute(policy.admit(url, "GET", headers, null, 8_000, false))
        classify(exchange.statusCode, exchange.body, parse)
    } catch (e: Exception) {
        LiveSearch(0, null, false, e.javaClass.simpleName)
    }
}

private fun classify(status: Int, body: ByteArray, parse: (Any?) -> CatalogPage?): LiveSearch {
    if (status != 200) return LiveSearch(status, null, true, null)
    val text = body.toString(Charsets.UTF_8).trim()
    if (!text.startsWith("{") && !text.startsWith("[")) return LiveSearch(status, null, true, null)
    return try {
        val page = parse(Json.parse(text))
        if (page != null && page.tracks.isEmpty()) {
            println("INFO catalog empty status=$status bytes=${body.size} lists=${text.contains("\"lists\"")} audioid=${text.contains("Audioid")}")
        }
        if (page == null) LiveSearch(status, null, true, null) else LiveSearch(status, page, false, null)
    } catch (e: RuntimeException) {
        LiveSearch(status, null, false, e.javaClass.simpleName)
    }
}
