package com.hiraeth.flame.data.repository

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.hiraeth.flame.data.db.MediaDao
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.local.MediaStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(
    private val dao: MediaDao,
    private val storage: MediaStorage,
) {

    fun observeAll(): Flow<List<MediaEntity>> = dao.observeAll()

    fun observeById(id: Long): Flow<MediaEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): MediaEntity? = dao.getById(id)

    suspend fun update(entity: MediaEntity) {
        dao.update(entity.copy(modifiedAtEpochMs = System.currentTimeMillis()))
    }

    suspend fun delete(entity: MediaEntity) = withContext(Dispatchers.IO) {
        storage.resolveRelative(entity.relativePath).delete()
        dao.delete(entity)
    }

    suspend fun importFromUri(uri: Uri, suggestedName: String, isVideo: Boolean): Long =
        withContext(Dispatchers.IO) {
            val (file, mime) = storage.importFromUri(uri, isVideo)
            val dims = if (isVideo) videoDimensionsAndDuration(file) else imageDimensions(file)
            val entity = MediaEntity(
                relativePath = storage.relativeToRoot(file),
                displayName = suggestedName.trim(),
                mimeType = mime,
                isVideo = isVideo,
                sizeBytes = file.length(),
                width = dims.first,
                height = dims.second,
                durationMs = dims.third,
            )
            dao.insert(entity)
        }

    suspend fun registerCapturedPhoto(file: File): Long = insertFileRecord(file, isVideo = false)

    suspend fun registerCapturedVideo(file: File): Long = insertFileRecord(file, isVideo = true)

    private suspend fun insertFileRecord(file: File, isVideo: Boolean): Long =
        withContext(Dispatchers.IO) {
            val mime = if (isVideo) "video/mp4" else "image/jpeg"
            val dims = if (isVideo) videoDimensionsAndDuration(file) else imageDimensions(file)
            val entity = MediaEntity(
                relativePath = storage.relativeToRoot(file),
                displayName = "",
                mimeType = mime,
                isVideo = isVideo,
                sizeBytes = file.length(),
                width = dims.first,
                height = dims.second,
                durationMs = dims.third,
            )
            dao.insert(entity)
        }

    fun resolveFile(entity: MediaEntity): File = storage.resolveRelative(entity.relativePath)

    private fun imageDimensions(file: File): Triple<Int, Int, Long> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return Triple(opts.outWidth, opts.outHeight, 0L)
    }

    private fun videoDimensionsAndDuration(file: File): Triple<Int, Int, Long> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            Triple(w, h, dur)
        } finally {
            r.release()
        }
    }
}
