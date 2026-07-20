package com.hiraeth.flame.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.db.AlbumWithMedia
import com.hiraeth.flame.data.repository.MediaRepository
import com.hiraeth.flame.data.repository.AlbumRepository
import com.hiraeth.flame.domain.LibrarySort
import com.hiraeth.flame.domain.LibraryViewMode
import com.hiraeth.flame.domain.MediaTypeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class LibraryListItem {
    data class Header(val title: String, val albumId: Long = -1L, val isExpanded: Boolean = true) : LibraryListItem()
    data class Media(val entity: MediaEntity) : LibraryListItem()
}

/** Holds the first four inputs for [LibraryViewModel.items] (nested [combine] avoids 7-way overload issues on K2). */
private data class LibraryMainInputs(
    val list: List<MediaEntity>,
    val query: String,
    val typeFilter: MediaTypeFilter,
    val sort: LibrarySort,
    val albums: List<AlbumWithMedia>
)

class LibraryViewModel(
    private val repository: MediaRepository,
    private val albumRepository: AlbumRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val typeFilter = MutableStateFlow(MediaTypeFilter.All)
    private val sort = MutableStateFlow(LibrarySort.DateNewest)
    private val viewMode = MutableStateFlow(LibraryViewMode.Grid)
    private val tagFilter = MutableStateFlow("")
    
    // Tracks expanded state for album IDs (-1 for "Not part of any album")
    private val expandedAlbums = MutableStateFlow<Set<Long>>(setOf(-1L) + (0..100L).toSet()) // Initially expand many

    val viewModeState: StateFlow<LibraryViewMode> = viewMode

    val albums: StateFlow<List<AlbumWithMedia>> = albumRepository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<LibraryListItem>> = combine(
        combine(
            repository.observeAll(),
            _query,
            typeFilter,
            sort,
            albumRepository.observeAlbums()
        ) { list, q, tf, s, albums ->
            LibraryMainInputs(list, q, tf, s, albums)
        },
        tagFilter,
        expandedAlbums
    ) { main, tag, expanded ->
        val list = main.list
        val q = main.query
        val tf = main.typeFilter
        val s = main.sort
        val albums = main.albums

        val filtered = list.asSequence()
            .filter { entity ->
                if (q.isBlank()) true else entity.displayName.contains(q, ignoreCase = true)
            }
            .filter { entity ->
                if (tag.isBlank()) true else entity.description.contains(tag, ignoreCase = true)
            }
            .filter { entity ->
                when (tf) {
                    MediaTypeFilter.All -> true
                    MediaTypeFilter.ImagesOnly -> !entity.isVideo
                    MediaTypeFilter.VideosOnly -> entity.isVideo
                }
            }
            .sortedWith(comparatorFor(s))
            .toList()

        // Grouping logic
        val result = mutableListOf<LibraryListItem>()
        val mediaInAlbums = mutableSetOf<Long>()

        albums.forEach { awm ->
            val albumMedia = awm.media.filter { m -> filtered.any { it.id == m.id } }
            if (albumMedia.isNotEmpty()) {
                val isExpanded = expanded.contains(awm.album.id)
                result.add(LibraryListItem.Header(awm.album.name, awm.album.id, isExpanded))
                if (isExpanded) {
                    albumMedia.forEach { 
                        result.add(LibraryListItem.Media(it))
                        mediaInAlbums.add(it.id)
                    }
                } else {
                    // Even if not expanded, mark media as "in album" so it doesn't show up in "Not in album"
                    albumMedia.forEach { mediaInAlbums.add(it.id) }
                }
            }
        }

        val notInAlbum = filtered.filter { it.id !in mediaInAlbums }
        if (notInAlbum.isNotEmpty()) {
            val isExpanded = expanded.contains(-1L)
            result.add(LibraryListItem.Header("Not part of any album", -1L, isExpanded))
            if (isExpanded) {
                notInAlbum.forEach { result.add(LibraryListItem.Media(it)) }
            }
        }

        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleAlbumExpanded(albumId: Long) {
        val current = expandedAlbums.value
        if (current.contains(albumId)) {
            expandedAlbums.value = current - albumId
        } else {
            expandedAlbums.value = current + albumId
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setTypeFilter(value: MediaTypeFilter) {
        typeFilter.value = value
    }

    fun setSort(value: LibrarySort) {
        sort.value = value
    }

    fun toggleViewMode() {
        viewMode.value = if (viewMode.value == LibraryViewMode.Grid) LibraryViewMode.List else LibraryViewMode.Grid
    }

    fun setTagFilter(value: String) {
        tagFilter.value = value
    }

    fun delete(entity: MediaEntity) {
        viewModelScope.launch { repository.delete(entity) }
    }

    fun deleteAll(entities: List<MediaEntity>) {
        viewModelScope.launch {
            entities.forEach { repository.delete(it) }
        }
    }

    fun addToAlbum(albumId: Long, mediaIds: List<Long>) {
        viewModelScope.launch {
            mediaIds.forEach { albumRepository.addToAlbum(albumId, it) }
        }
    }

    fun createAlbum(name: String, description: String = "", onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = albumRepository.createAlbum(name, description)
            onCreated(id)
        }
    }

    private fun comparatorFor(sort: LibrarySort): Comparator<MediaEntity> =
        when (sort) {
            LibrarySort.DateNewest -> compareByDescending { it.modifiedAtEpochMs }
            LibrarySort.DateOldest -> compareBy { it.modifiedAtEpochMs }
            LibrarySort.NameAZ -> compareBy { it.displayName.lowercase() }
            LibrarySort.NameZA -> compareByDescending { it.displayName.lowercase() }
            LibrarySort.SizeLargest -> compareByDescending { it.sizeBytes }
            LibrarySort.SizeSmallest -> compareBy { it.sizeBytes }
        }

    companion object {
        fun factory(repository: MediaRepository, albumRepository: AlbumRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LibraryViewModel(repository, albumRepository) as T
                }
            }
    }
}
