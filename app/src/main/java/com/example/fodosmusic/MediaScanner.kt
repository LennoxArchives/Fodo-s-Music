package com.example.fodosmusic

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val year: Int,
    val duration: Long,
    val uri: Uri
)

/**
 * Info teknis audio yang dibaca langsung dari container file-nya (bukan tebakan).
 * Nilai 0 / "Unknown" berarti info itu tidak tersedia di file tersebut.
 */
data class AudioTechInfo(
    val formatLabel: String,
    val sampleRateHz: Int,
    val bitDepth: Int,
    val bitrateKbps: Int
)

fun getAllAudioFiles(context: Context): List<Song> {
    val songs = mutableListOf<Song>()
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.YEAR,
        MediaStore.Audio.Media.DURATION
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

    context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(collection, id)
            songs.add(
                Song(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Unknown",
                    artist = cursor.getString(artistCol) ?: "Unknown Artist",
                    album = cursor.getString(albumCol) ?: "Unknown Album",
                    year = cursor.getInt(yearCol),
                    duration = cursor.getLong(durationCol),
                    uri = uri
                )
            )
        }
    }
    return songs
}

// ---- Cache album art (logika sama seperti sebelumnya) ----
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

/**
 * Baca info teknis audio (format, sample rate, bit depth perkiraan, bitrate) langsung
 * dari container file-nya pakai MediaExtractor. Wajib dipanggil dari coroutine (suspend),
 * karena baca file bisa agak lambat kalau storage-nya lelet.
 */
suspend fun getAudioTechInfo(context: Context, uri: Uri): AudioTechInfo = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, uri, null)

        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                format = f
                break
            }
        }

        if (format == null) return@withContext AudioTechInfo("Unknown", 0, 0, 0)

        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        val formatLabel = mimeToLabel(mime)

        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else 0

        // "bits-per-sample" cuma keisi platform buat format lossless (FLAC/WAV) di device yg cukup baru.
        // Kalau nggak ada, kita asumsikan 16-bit standar CD buat lossless, dan 0 (n/a) buat format lossy.
        val bitDepth = when {
            format.containsKey("bits-per-sample") -> format.getInteger("bits-per-sample")
            mime.contains("flac") || mime.contains("raw") -> 16
            else -> 0
        }

        val bitrate = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
            format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000
        } else 0

        AudioTechInfo(formatLabel, sampleRate, bitDepth, bitrate)
    } catch (e: Exception) {
        AudioTechInfo("Unknown", 0, 0, 0)
    } finally {
        extractor.release()
    }
}

private fun mimeToLabel(mime: String): String = when {
    mime.contains("flac") -> "FLAC"
    mime.contains("mpeg") -> "MP3"
    mime.contains("aac") -> "AAC"
    mime.contains("opus") -> "Opus"
    mime.contains("vorbis") -> "OGG Vorbis"
    mime.contains("wav") || mime.contains("raw") -> "WAV"
    mime.contains("alac") -> "ALAC"
    else -> mime.substringAfter("/").uppercase(Locale.ROOT).ifBlank { "Unknown" }
}

// ---- Favorit lagu (persist simpel pakai SharedPreferences) ----
private const val FAVORITES_PREFS = "fodos_music_favorites"
private const val FAVORITES_KEY = "favorite_ids"

fun loadFavoriteIds(context: Context): Set<Long> {
    val prefs = context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
    val raw = prefs.getStringSet(FAVORITES_KEY, emptySet()) ?: emptySet()
    return raw.mapNotNull { it.toLongOrNull() }.toSet()
}

fun saveFavoriteIds(context: Context, ids: Set<Long>) {
    val prefs = context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
    prefs.edit().putStringSet(FAVORITES_KEY, ids.map { it.toString() }.toSet()).apply()
}