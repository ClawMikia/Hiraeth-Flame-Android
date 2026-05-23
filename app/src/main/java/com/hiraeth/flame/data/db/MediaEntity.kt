package com.hiraeth.flame.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local media row: file bytes live under [com.hiraeth.flame.data.local.MediaStorage.root];
 * [relativePath] is stable for Room and survives process death.
 */
@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val relativePath: String,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val description: String = "",
    val modifiedAtEpochMs: Long = System.currentTimeMillis(),
    val sizeBytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
)
