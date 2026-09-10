package com.example.fodosmusic

import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Info teknis audio yang diambil langsung dari track format file-nya (bukan tebakan/hardcode),
 * jadi akurat sesuai file aslinya.
 */
data class AudioInfo(
    val format: String,
    val sampleRateHz: Int,
    val bitDepth: Int?,      // null kalau formatnya lossy (mp3/aac/ogg/opus) karena memang nggak punya bit depth tetap
    val bitrateKbps: Int?,
    val channelCount: Int
) {
    val sampleRateLabel: String
        get() = if (sampleRateHz > 0) String.format("%.1f kHz", sampleRateHz / 1000f) else "-"

    val bitDepthLabel: String
        get() = bitDepth?.let { "$it-bit" } ?: "-"

    val bitrateLabel: String
        get() = bitrateKbps?.let { "$it kbps" } ?: "-"

    val channelLabel: String
        get() = when (channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            0 -> "-"
            else -> "$channelCount ch"
        }
}

/**
 * Baca metadata teknis dari track audio pakai MediaExtractor (bukan MediaMetadataRetriever,
 * karena retriever nggak expose sample rate / bit depth secara langsung).
 * Dijalankan di IO dispatcher karena baca file.
 */
suspend fun getAudioInfo(context: Context, uri: Uri): AudioInfo? = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, uri, null)

        var trackFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackFormat = format
                break
            }
        }

        val format = trackFormat ?: return@withContext null
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0

        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 0

        val bitrateKbps = if (format.containsKey(MediaFormat.KEY_BIT_RATE))
            format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000 else null

        // Bit depth cuma valid buat format PCM/lossless. Kalau ada KEY_PCM_ENCODING eksplisit, pakai itu.
        // Kalau nggak ada tapi mime-nya lossless (FLAC/WAV), default ke 16-bit (standar paling umum).
        // Kalau formatnya lossy (mp3/aac/ogg/opus), sengaja dibiarkan null karena memang nggak punya bit depth tetap.
        val bitDepth: Int? = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            when (format.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                AudioFormat.ENCODING_PCM_8BIT -> 8
                AudioFormat.ENCODING_PCM_16BIT -> 16
                AudioFormat.ENCODING_PCM_FLOAT -> 32
                else -> null
            }
        } else {
            when {
                mime.contains("flac", ignoreCase = true) -> 16
                mime.contains("wav", ignoreCase = true) -> 16
                mime.contains("x-wav", ignoreCase = true) -> 16
                mime.contains("raw", ignoreCase = true) -> 16
                else -> null
            }
        }

        val formatLabel = when {
            mime.contains("flac", ignoreCase = true) -> "FLAC"
            mime.contains("mpeg", ignoreCase = true) -> "MP3"
            mime.contains("mp4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> "AAC"
            mime.contains("wav", ignoreCase = true) -> "WAV"
            mime.contains("vorbis", ignoreCase = true) -> "OGG"
            mime.contains("opus", ignoreCase = true) -> "OPUS"
            mime.isNotEmpty() -> mime.substringAfterLast("/").uppercase()
            else -> "Unknown"
        }

        AudioInfo(
            format = formatLabel,
            sampleRateHz = sampleRate,
            bitDepth = bitDepth,
            bitrateKbps = bitrateKbps,
            channelCount = channelCount
        )
    } catch (e: Exception) {
        null
    } finally {
        extractor.release()
    }
}