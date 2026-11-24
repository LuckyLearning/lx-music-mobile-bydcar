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
    val extra: Map<String, String> = emptyMap(),
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
        fetch(policy, searchUrl(query, 1, 10), ::parse)

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
            val exchange = PinnedHttps(policy, ScopedConnector).execute(policy.admit(url, "GET", emptyList(), null, 8_000, false))
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
    const val SEARCH_HOST = "u.y.qq.com"
    const val USER_AGENT = "QQMusic 14090508(android 12)"

    /** Same object and key order as musicSdk/tx/musicSearch.js musicSearch. */
    fun desktopBody(query: String, page: Int, limit: Int, searchId: String): String {
        return Json.stringify(linkedMapOf(
            "comm" to linkedMapOf(
                "_channelid" to "0",
                "_os_version" to "6.2.9200-2",
                "ct" to "19",
                "cv" to "2151",
                "guid" to "1F70E520B2EAA7D25E11760783C53CA9",
                "patch" to "118",
                "psrf_access_token_expiresAt" to 0,
                "psrf_qqaccess_token" to "",
                "psrf_qqopenid" to "",
                "psrf_qqunionid" to "",
                "tmeAppID" to "qqmusic",
                "tmeLoginType" to 0,
                "uin" to "0",
                "wid" to "7223299733393904640",
            ),
            "music.search.SearchCgiService" to linkedMapOf(
                "module" to "music.search.SearchCgiService",
                "method" to "DoSearchForQQMusicDesktop",
                "param" to linkedMapOf(
                    "grp" to 1,
                    "num_per_page" to limit,
                    "page_num" to page,
                    "query" to query,
                    "remoteplace" to "txt.newclient.top",
                    "search_type" to 0,
                    "searchid" to searchId,
                ),
            ),
        ))
    }

    fun parseDesktop(body: Any?): CatalogPage? {
        val root = body as? Map<*, *> ?: return null
        if ((root["code"] as? Number)?.toInt() != 0) return null
        val service = (root["music.search.SearchCgiService"] as? Map<*, *>) ?: (root["req"] as? Map<*, *>) ?: return null
        if ((service["code"] as? Number)?.toInt() != 0) return null
        val data = service["data"] as? Map<*, *> ?: return null
        val song = ((data["body"] as? Map<*, *>)?.get("song") as? Map<*, *>)?.get("list") as? List<*> ?: return null
        val total = ((data["meta"] as? Map<*, *>)?.get("sum") as? Number)?.toInt() ?: song.size
        return CatalogPage(parseSongs(song), total, "tx")
    }

    fun live(policy: NetworkPolicy, query: String): LiveSearch {
        val body = desktopBody(query, 1, 10, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA00000").toByteArray(Charsets.UTF_8)
        val url = "https://$SEARCH_HOST/cgi-bin/musics.fcg?sign=${LegacySign.tx(body.toString(Charsets.UTF_8))}"
        return fetch(policy, url, ::parseDesktop, listOf(
            "user-agent" to USER_AGENT,
            "content-type" to "application/json",
        ), "POST", body)
    }

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
    const val SEARCH_HOST = "interface.music.163.com"
    const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36"
    private const val PATH = "/api/search/song/list/page"

    fun queryText(query: String, page: Int, limit: Int): String {
        return Json.stringify(linkedMapOf(
            "keyword" to query,
            "needCorrect" to "1",
            "channel" to "typing",
            "offset" to limit * (page - 1),
            "scene" to "normal",
            "total" to (page == 1),
            "limit" to limit,
        ))
    }

    fun formBody(query: String, page: Int, limit: Int): ByteArray {
        val params = LegacySign.wyParams(PATH, queryText(query, page, limit))
        return "params=$params".toByteArray(Charsets.US_ASCII)
    }

    fun parseBody(body: Any?): CatalogPage? {
        val root = body as? Map<*, *> ?: return null
        if ((root["code"] as? Number)?.toInt() != 200) return null
        val data = root["data"] as? Map<*, *> ?: return null
        val rows = data["resources"] as? List<*> ?: return null
        val tracks = parseResources(rows) ?: return null
        val total = (data["totalCount"] as? Number)?.toInt() ?: tracks.size
        return CatalogPage(tracks, total, "wy")
    }

    fun live(query: String): LiveSearch {
        val policy = NetworkPolicy(
            cleartextHosts = setOf(SEARCH_HOST),
            resolve = { host -> InetAddress.getAllByName(host).mapNotNull { it.hostAddress } },
        )
        val body = formBody(query, 1, 10)
        return fetch(policy, "http://$SEARCH_HOST/eapi/batch", ::parseBody, listOf(
            "user-agent" to USER_AGENT,
            "origin" to "https://music.163.com",
            "content-type" to "application/x-www-form-urlencoded",
        ), "POST", body, ScopedConnector)
    }

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
    const val SEARCH_HOST = "jadeite.migu.cn"
    const val USER_AGENT = "Mozilla/5.0 (Linux; U; Android 11.0.0; zh-cn; MI 11 Build/OPR1.170623.032) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30"

    fun searchUrl(query: String, page: Int, limit: Int): String {
        val text = LegacyRequest.encodeURIComponent(query)
        return "https://$SEARCH_HOST/music_search/v3/search/searchAll?isCorrect=0&isCopyright=1&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C%22bestShow%22%3A1%2C%22songlist%22%3A0%2C%22lyricSong%22%3A0%7D&pageSize=$limit&text=$text&pageNo=$page&sort=0&sid=USS"
    }

    fun headers(query: String, time: String): List<Pair<String, String>> {
        val (sign, device) = LegacySign.mg(time, query)
        return listOf(
            "uiversion" to "A_music_3.6.1",
            "deviceid" to device,
            "timestamp" to time,
            "sign" to sign,
            "channel" to "0146921",
            "user-agent" to USER_AGENT,
        )
    }

    fun parseBody(body: Any?): CatalogPage? {
        val root = body as? Map<*, *> ?: return null
        if (root["code"]?.toString() != "000000") return null
        val data = root["songResultData"] as? Map<*, *> ?: return null
        val rows = data["resultList"] as? List<*> ?: return null
        val total = data["totalCount"]?.toString()?.toIntOrNull() ?: rows.size
        return CatalogPage(parseResultList(rows), total, "mg")
    }

    fun live(policy: NetworkPolicy, query: String, time: String = System.currentTimeMillis().toString()): LiveSearch {
        return fetch(policy, searchUrl(query, 1, 10), ::parseBody, headers(query, time))
    }

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
                        extra = mapOf("copyrightId" to copyright),
                    )
                )
            }
        }
        return tracks
    }
}

internal fun long(value: Any?): Long = (value as? Number)?.toLong() ?: 0L

object KgPlaylist {
    const val INFO_HOST = "mobiles.kugou.com"

    data class Info(val name: String, val author: String, val songCount: Int)

    fun infoUrl(globalId: String): String {
        require(globalId.length in 1..80 && globalId.all { it.isLetterOrDigit() || it == '_' })
        val params = "appid=1058&specialid=0&global_specialid=$globalId&format=jsonp&srcappid=2919&clientver=20000&clienttime=1586163242519&mid=1586163242519&uuid=1586163242519&dfid=-"
        return "https://$INFO_HOST/api/v5/special/info_v2?$params&signature=${LegacySign.kg(params, "web")}"
    }

    /** createHttp returns body.data. specialname/nickname/songcount are the fields getUserListDetail2 reads. */
    fun parseInfo(data: Any?): Info? {
        val info = data as? Map<*, *> ?: return null
        val name = info["specialname"]?.toString() ?: return null
        if (name.isEmpty()) return null
        return Info(
            KwCatalog.decodeName(name),
            KwCatalog.decodeName(info["nickname"]?.toString()),
            (info["songcount"] as? Number)?.toInt() ?: 0,
        )
    }

    fun parseTracks(rows: List<*>): List<CatalogTrack> {
        val tracks = ArrayList<CatalogTrack>()
        for (row in rows) {
            val item = row as? Map<*, *> ?: continue
            val qualities = ArrayList<String>()
            val hashes = linkedMapOf<String, String>()
            fun keep(quality: String, sizeKey: String, hashKey: String) {
                if (long(item[sizeKey]) == 0L) return
                qualities.add(quality)
                val hash = item[hashKey]?.toString()?.trim().orEmpty()
                if (hash.matches(Regex("[A-Fa-f0-9]{32}"))) hashes[quality] = hash.lowercase()
            }
            keep("128k", "filesize", "hash")
            keep("320k", "filesize_320", "hash_320")
            keep("ape", "filesize_ape", "hash_ape")
            keep("flac", "filesize_flac", "hash_flac")
            tracks.add(CatalogTrack(
                source = "kg",
                name = KwCatalog.decodeName(item["songname"]?.toString()),
                singer = KwCatalog.decodeName(item["singername"]?.toString()),
                songmid = CatalogText.id(item["audio_id"]),
                albumName = KwCatalog.decodeName(item["album_name"]?.toString()),
                interval = KwCatalog.formatPlayTime((long(item["duration"]) / 1000L).toInt()),
                qualities = qualities,
                hashes = hashes,
            ))
        }
        return tracks
    }
}

data class Captured(val status: Int, val body: String, val clientError: String?)

private fun fetch(
    policy: NetworkPolicy,
    url: String,
    parse: (Any?) -> CatalogPage?,
    headers: List<Pair<String, String>> = emptyList(),
    method: String = "GET",
    body: ByteArray? = null,
    connector: Connector = TlsConnector,
): LiveSearch {
    val captured = capture(policy, url, headers, method, body, connector)
    if (captured.clientError != null) return LiveSearch(captured.status, null, false, captured.clientError)
    return classify(captured.status, captured.body.toByteArray(Charsets.UTF_8), parse)
}

internal fun capture(
    policy: NetworkPolicy,
    url: String,
    headers: List<Pair<String, String>> = emptyList(),
    method: String = "GET",
    body: ByteArray? = null,
    connector: Connector = TlsConnector,
): Captured {
    return try {
        val exchange = PinnedHttps(policy, connector).execute(policy.admit(url, method, headers, body, 8_000, false))
        Captured(exchange.statusCode, exchange.body.toString(Charsets.UTF_8), null)
    } catch (e: Exception) {
        Captured(0, "", e.javaClass.simpleName + " " + (e.message ?: ""))
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
