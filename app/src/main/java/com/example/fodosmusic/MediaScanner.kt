package com.example.fodosmusic

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: Uri
)

fun getAllAudioFiles(context: Context): List<Song> {
    val songs = mutableListOf<Song>()
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

    context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(collection, id)
            songs.add(
                Song(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Unknown",
                    artist = cursor.getString(artistCol) ?: "Unknown Artist",
                    duration = cursor.getLong(durationCol),
                    uri = uri
                )
            )
        }
    }
    return songs
}

// Cache tingkat 1: memori (paling cepat, tapi hilang kalau app di-kill)
private val albumArtCache = LruCache<Long, Bitmap>(300)
private val noArtCache = mutableSetOf<Long>()

private fun diskCacheDir(context: Context): File {
    val dir = File(context.cacheDir, "album_art")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun diskCacheFile(context: Context, songId: Long): File =
    File(diskCacheDir(context), "$songId.jpg")

private fun noArtMarkerFile(context: Context, songId: Long): File =
    File(diskCacheDir(context), "$songId.noart")

/**
 * Ambil album art. Urutan pencarian: memori -> disk -> decode dari file audio asli.
 * Hasil decode disimpan ke memori DAN disk, jadi restart app berikutnya tinggal baca dari disk
 * (jauh lebih cepat daripada decode ulang dari file audio).
 */
fun getAlbumArt(context: Context, songId: Long, uri: Uri, targetSizePx: Int = 200): Bitmap? {
    albumArtCache.get(songId)?.let { return it }
    if (noArtCache.contains(songId)) return null

    val diskFile = diskCacheFile(context, songId)
    if (diskFile.exists()) {
        val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
        if (bitmap != null) {
            albumArtCache.put(songId, bitmap)
            return bitmap
        }
    }

    if (noArtMarkerFile(context, songId).exists()) {
        noArtCache.add(songId)
        return null
    }

    val bitmap = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val artBytes = retriever.embeddedPicture
        retriever.release()

        if (artBytes != null) {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, boundsOptions)

            var sampleSize = 1
            while (boundsOptions.outWidth / sampleSize > targetSizePx * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, decodeOptions)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    if (bitmap != null) {
        albumArtCache.put(songId, bitmap)
        try {
            FileOutputStream(diskFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (e: Exception) {
            // gagal nulis cache disk bukan masalah fatal, lanjut aja
        }
    } else {
        noArtCache.add(songId)
        try {
            noArtMarkerFile(context, songId).createNewFile()
        } catch (e: Exception) {
        }
    }

    return bitmap
}

/**
 * Preload semua album art di background (IO thread), lapor progress lewat callback.
 * Dipanggil sekali pas app pertama kali buka, biar scroll list nggak stutter lagi
 * (karena semua art udah ke-cache duluan sebelum list ditampilin).
 */
suspend fun preloadAlbumArt(
    context: Context,
    songs: List<Song>,
    onProgress: (done: Int, total: Int) -> Unit
) {
    withContext(Dispatchers.IO) {
        songs.forEachIndexed { index, song ->
            getAlbumArt(context, song.id, song.uri)
            withContext(Dispatchers.Main) {
                onProgress(index + 1, songs.size)
            }
        }
    }
}

// ---- Archive status persistence ----
// Disimpen di SharedPreferences biar status archive-nya nggak reset tiap buka app.

private const val PREFS_NAME = "fodos_music_prefs"
private const val KEY_ARCHIVED_IDS = "archived_song_ids"

fun getArchivedSongIds(context: Context): Set<Long> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val stored = prefs.getStringSet(KEY_ARCHIVED_IDS, emptySet()) ?: emptySet()
    return stored.mapNotNull { it.toLongOrNull() }.toSet()
}

fun saveArchivedSongIds(context: Context, ids: Set<Long>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putStringSet(KEY_ARCHIVED_IDS, ids.map { it.toString() }.toSet()).apply()
}