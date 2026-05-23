package com.hiraeth.flame.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiraeth.flame.data.db.AlbumWithMedia
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.repository.AlbumRepository
import com.hiraeth.flame.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MediaDetailViewModel(
    private val repository: MediaRepository,
    private val albumRepository: AlbumRepository,
    private val mediaId: Long,
) : ViewModel() {

    val media: StateFlow<MediaEntity?> = repository.observeById(mediaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val albums: StateFlow<List<AlbumWithMedia>> = albumRepository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Channel to pipe operations state back safely to the UI thread
    private val _saveResult = MutableSharedFlow<Result<Unit>>()
    val saveResult: SharedFlow<Result<Unit>> = _saveResult.asSharedFlow()

    fun saveMetadata(title: String, description: String) {
        viewModelScope.launch {
            try {
                val current = media.value ?: throw IllegalStateException("Media item no longer exists in database")

                repository.update(
                    current.copy(
                        displayName = title.trim(),
                        description = description.trim(),
                    )
                )
                // Emit authentic success
                _saveResult.emit(Result.success(Unit))
            } catch (e: Exception) {
                // Catch actual SQLite, NullPointer, or IO exceptions safely
                _saveResult.emit(Result.failure(e))
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.getById(mediaId)?.let { repository.delete(it) }
                onDone()
            } catch (e: Exception) {
                // Safe crash containment
            }
        }
    }

    fun addToAlbum(albumId: Long) {
        viewModelScope.launch {
            try {
                albumRepository.addToAlbum(albumId, mediaId)
            } catch (e: Exception) {
                // Safe crash containment
            }
        }
    }

    companion object {
        fun factory(
            repository: MediaRepository,
            albumRepository: AlbumRepository,
            mediaId: Long,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(MediaDetailViewModel::class.java))
                    return MediaDetailViewModel(repository, albumRepository, mediaId) as T
                }
            }
    }
}
