package com.hiraeth.flame.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiraeth.flame.data.db.AlbumWithMedia
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.repository.AlbumRepository
import com.hiraeth.flame.data.repository.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun saveMetadata(
        title: String,
        description: String,
    ) {
        viewModelScope.launch {
            val current = repository.getById(mediaId) ?: return@launch
            repository.update(
                current.copy(
                    displayName = title.trim(),
                    description = description.trim(),
                ),
            )
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.getById(mediaId)?.let { repository.delete(it) }
            onDone()
        }
    }

    fun addToAlbum(albumId: Long) {
        viewModelScope.launch {
            albumRepository.addToAlbum(albumId, mediaId)
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
