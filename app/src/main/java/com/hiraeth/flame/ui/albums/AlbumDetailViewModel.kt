package com.hiraeth.flame.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiraeth.flame.data.db.AlbumEntity
import com.hiraeth.flame.data.db.AlbumWithMedia
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.repository.AlbumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlbumDetailViewModel(
    private val albumRepository: AlbumRepository,
    private val albumId: Long,
) : ViewModel() {

    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter

    val albumWithMedia: StateFlow<AlbumWithMedia?> = albumRepository.observeAlbums()
        .map { list -> list.find { it.album.id == albumId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val filteredMedia: StateFlow<List<MediaEntity>> = combine(albumWithMedia, _filter) { awm, query ->
        val media = awm?.media ?: emptyList()
        if (query.isBlank()) media
        else media.filter { it.displayName.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(query: String) {
        _filter.value = query
    }

    fun updateAlbum(name: String, description: String) {
        viewModelScope.launch {
            albumRepository.updateAlbum(albumId, name, description)
        }
    }

    companion object {
        fun factory(albumRepository: AlbumRepository, albumId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AlbumDetailViewModel(albumRepository, albumId) as T
                }
            }
    }
}
