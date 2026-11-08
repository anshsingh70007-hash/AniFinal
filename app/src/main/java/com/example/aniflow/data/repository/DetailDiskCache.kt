package com.example.aniflow.data.repository

import android.content.Context
import com.example.aniflow.data.NetworkModule
import com.example.aniflow.data.model.Anime
import java.io.File

/**
 * Last good AniList detail per anime, on disk. Lets a title the user has opened before still open
 * — with its synopsis, art and recommendations — while AniList is down or rate-limiting.
 * Bounded to [MAX_FILES]; oldest files are evicted. Room replaces this in P3.
 */
class DetailDiskCache(context: Context) {
    private val dir = File(context.filesDir, "detail_cache").apply { mkdirs() }

    fun load(id: Int): Anime? = runCatching {
        val f = File(dir, "$id.json")
        if (!f.exists()) null else NetworkModule.json.decodeFromString(Anime.serializer(), f.readText())
    }.getOrNull()

    fun save(anime: Anime) {
        runCatching {
            val tmp = File(dir, "${anime.id}.json.tmp")
            tmp.writeText(NetworkModule.json.encodeToString(Anime.serializer(), anime))
            tmp.renameTo(File(dir, "${anime.id}.json"))
            val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return
            if (files.size > MAX_FILES) {
                files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
            }
        }
    }

    private companion object { const val MAX_FILES = 400 }
}
