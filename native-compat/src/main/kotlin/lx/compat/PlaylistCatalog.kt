// SPDX-License-Identifier: Apache-2.0
package lx.compat

/**
 * Playlist list and detail from the existing musicSdk songList modules.
 * Playback addresses stay with the imported script. These requests are the original HTTPS URLs.
 */
data class PlaylistCard(val id: String, val name: String, val source: String)

object MgPlaylist {
    const val HOST = "app.c.nf.migu.cn"
    const val INFO_HOST = "c.musicapp.migu.cn"
    val headers = listOf(
        "user-agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1",
        "referer" to "https://m.music.migu.cn/",
    )

    fun recommendUrl(page: Int): String {
        require(page >= 1)
        return "https://$HOST/pc/bmw/page-data/playlist-square-recommend/v1.0?templateVersion=2&pageNo=$page"
    }

    fun songsUrl(id: String, page: Int): String {
        require(id.length in 1..24 && id.all { it.isDigit() } && page >= 1)
        return "https://$HOST/MIGUM3.0/resource/playlist/song/v2.0?pageNo=$page&pageSize=30&playlistId=$id"
    }

    fun infoUrl(id: String): String {
        require(id.length in 1..24 && id.all { it.isDigit() })
        return "https://$INFO_HOST/MIGUM3.0/resource/playlist/v2.0?playlistId=$id"
    }

    /** musicSdk/mg/songList.js getList: contents use filterList2, otherwise contentItemList[1]. */
    fun parseRecommend(body: Any?): List<PlaylistCard>? {
        val root = body as? Map<*, *> ?: return null
        if (root["code"]?.toString() != "000000") return null
        val data = root["data"] as? Map<*, *> ?: return null
        val contents = data["contents"] as? List<*>
        if (contents != null) return filterList2(contents)
        val items = (data["contentItemList"] as? List<*>)?.getOrNull(1).let { (it as? Map<*, *>)?.get("itemList") as? List<*> }
            ?: return null
        return items.mapNotNull { row ->
            val item = row as? Map<*, *> ?: return@mapNotNull null
            val id = (((item["logEvent"] as? Map<*, *>)?.get("contentId")) ?: return@mapNotNull null).toString()
            val name = item["title"]?.toString() ?: return@mapNotNull null
            PlaylistCard(id, name, "mg")
        }
    }

    fun parseSongs(body: Any?): CatalogPage? {
        val root = body as? Map<*, *> ?: return null
        if (root["code"]?.toString() != "000000") return null
        val data = root["data"] as? Map<*, *> ?: return null
        val rows = data["songList"] as? List<*> ?: return null
        val tracks = ArrayList<CatalogTrack>()
        val seen = HashSet<String>()
        for (row in rows) {
            val item = row as? Map<*, *> ?: continue
            val songId = item["songId"]?.toString() ?: continue
            if (songId.isEmpty() || !seen.add(songId)) continue
            val qualities = ArrayList<String>()
            val formats = item["audioFormats"] as? List<*> ?: emptyList<Any?>()
            for (format in formats) {
                when ((format as? Map<*, *>)?.get("formatType")?.toString()) {
                    "PQ" -> qualities.add("128k")
                    "HQ" -> qualities.add("320k")
                    "SQ" -> qualities.add("flac")
                    "ZQ" -> qualities.add("flac24bit")
                }
            }
            tracks.add(
                CatalogTrack(
                    source = "mg",
                    name = item["songName"]?.toString() ?: "",
                    singer = CatalogText.singerList(item["singerList"]),
                    songmid = songId,
                    albumName = item["album"]?.toString() ?: "",
                    interval = KwCatalog.formatPlayTime(long(item["duration"]).toInt()),
                    qualities = qualities,
                    extra = mapOf("copyrightId" to (item["copyrightId"]?.toString() ?: "")),
                ),
            )
        }
        val total = (data["totalCount"] as? Number)?.toInt() ?: tracks.size
        return CatalogPage(tracks, total, "mg")
    }

    fun parseInfo(body: Any?): String? {
        val root = body as? Map<*, *> ?: return null
        if (root["code"]?.toString() != "000000") return null
        return ((root["data"] as? Map<*, *>)?.get("title"))?.toString()
    }

    private fun filterList2(rows: List<*>, out: ArrayList<PlaylistCard> = ArrayList(), seen: HashSet<String> = HashSet()): List<PlaylistCard> {
        for (row in rows) {
            val item = row as? Map<*, *> ?: continue
            val nested = item["contents"] as? List<*>
            if (nested != null) filterList2(nested, out, seen)
            else if (item["resType"]?.toString() == "2021") {
                val id = item["resId"]?.toString() ?: continue
                if (seen.add(id)) out.add(PlaylistCard(id, item["txt"]?.toString() ?: "", "mg"))
            }
        }
        return out
    }
}

object TxPlaylist {
    fun detailUrl(id: String): String {
        require(id.length in 1..24 && id.all { it.isDigit() })
        return "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=$id&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
    }

    /** musicSdk/tx/songList.js getListDetail. code and subcode must be 0 and cdlist must exist. */
    fun parseDetail(body: Any?): CatalogPage? {
        val root = body as? Map<*, *> ?: return null
        if ((root["code"] as? Number)?.toInt() != 0) return null
        if ((root["subcode"] as? Number)?.toInt() != 0) return null
        val cd = (root["cdlist"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return null
        val rows = cd["songlist"] as? List<*> ?: return null
        return CatalogPage(TxCatalog.parseSongs(rows), rows.size, "tx")
    }
}
