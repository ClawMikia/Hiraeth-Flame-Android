package com.hiraeth.flame.ui.util

import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Collections

class DriveServiceHelper(private val driveService: Drive) {

    suspend fun createFolder(folderName: String, parentId: String? = null): String? = withContext(Dispatchers.IO) {
        val metadata = File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
            if (parentId != null) {
                parents = listOf(parentId)
            }
        }
        try {
            val googleFile = driveService.files().create(metadata).setFields("id").execute()
            googleFile.id
        } catch (e: IOException) {
            null
        }
    }

    suspend fun uploadFile(localFile: java.io.File, mimeType: String, parentId: String?): String? = withContext(Dispatchers.IO) {
        val metadata = File().apply {
            name = localFile.name
            if (parentId != null) {
                parents = listOf(parentId)
            }
        }
        val mediaContent = FileContent(mimeType, localFile)
        try {
            val googleFile = driveService.files().create(metadata, mediaContent).setFields("id").execute()
            googleFile.id
        } catch (e: IOException) {
            null
        }
    }

    suspend fun findFolder(folderName: String, parentId: String? = null): String? = withContext(Dispatchers.IO) {
        var query = "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false"
        if (parentId != null) {
            query += " and '$parentId' in parents"
        }
        try {
            val result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
            result.files.firstOrNull()?.id
        } catch (e: IOException) {
            null
        }
    }
}
