package com.hiraeth.flame.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportHelper {

    enum class ImageFormat { JPG, PNG }

    suspend fun exportSingleImage(
        context: Context,
        sourceFile: File,
        destUri: Uri,
        format: ImageFormat
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return@withContext false
            context.contentResolver.openOutputStream(destUri)?.use { os ->
                when (format) {
                    ImageFormat.JPG -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
                    ImageFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun exportSingleVideo(
        context: Context,
        sourceFile: File,
        destUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(destUri)?.use { os ->
                sourceFile.inputStream().use { input -> input.copyTo(os) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun zipFiles(
        context: Context,
        files: List<Pair<String, File>>,
        destUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(destUri)?.use { os ->
                ZipOutputStream(BufferedOutputStream(os)).use { zos ->
                    files.forEach { (entryName, file) ->
                        if (file.exists()) {
                            zos.putNextEntry(ZipEntry(entryName))
                            file.inputStream().use { input -> input.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getExportMimeType(entity: MediaEntity): String {
        return if (entity.isVideo) "video/mp4" else "image/jpeg"
    }

    fun getExportFileName(entity: MediaEntity, format: ImageFormat? = null): String {
        return if (entity.isVideo) {
            "${entity.displayName}.mp4"
        } else {
            val ext = when (format) {
                ImageFormat.PNG -> "png"
                ImageFormat.JPG, null -> "jpg"
            }
            "${entity.displayName}.$ext"
        }
    }
}
