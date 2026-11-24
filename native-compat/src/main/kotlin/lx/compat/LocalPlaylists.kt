// SPDX-License-Identifier: Apache-2.0
package lx.compat

/** Playlists saved by this test app. They are not the official app's lists and they are not fetched from a music service. */
data class SavedTrack(val title: String, val uri: String)

data class SavedList(val name: String, val tracks: List<SavedTrack>)

object LocalPlaylists {
    fun encode(lists: List<SavedList>): String {
        val payload = lists.take(20).map { list ->
            mapOf(
                "name" to list.name.take(40),
                "tracks" to list.tracks.take(100).map { track ->
                    mapOf("title" to track.title.take(80), "uri" to track.uri.take(2048))
                },
            )
        }
        return Json.stringify(payload)
    }

    fun decode(text: String?): List<SavedList> {
        if (text.isNullOrEmpty()) return emptyList()
        val rows = try {
            Json.parse(text).list()
        } catch (_: RuntimeException) {
            return emptyList()
        }
        return rows.mapNotNull { row ->
            val item = row as? Map<*, *> ?: return@mapNotNull null
            val name = item["name"] as? String ?: return@mapNotNull null
            val tracks = (item["tracks"] as? List<*>)?.mapNotNull { entry ->
                val track = entry as? Map<*, *> ?: return@mapNotNull null
                val title = track["title"] as? String ?: return@mapNotNull null
                val uri = track["uri"] as? String ?: return@mapNotNull null
                if (uri.isEmpty()) null else SavedTrack(title, uri)
            } ?: emptyList()
            SavedList(name, tracks)
        }
    }
}
