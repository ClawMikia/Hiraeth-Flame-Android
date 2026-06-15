package com.hiraeth.flame.ui.mediaimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiraeth.flame.data.repository.AlbumRepository
import com.hiraeth.flame.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ImportPreviewViewModel(
    private val repository: MediaRepository,
    private val albumRepository: AlbumRepository,
) : ViewModel() {

    private val _busy = MutableStateFlow(value = false)
    val busy: StateFlow<Boolean> = _busy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _progressCount = MutableStateFlow(0)
    val progressCount: StateFlow<Int> = _progressCount

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount

    fun import(
        uri: Uri,
        displayName: String,
        description: String,
        isVideo: Boolean,
        albumName: String? = null,
        onImported: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                val mediaId = repository.importFromUri(uri, displayName, description, isVideo)
                if (!albumName.isNullOrBlank()) {
                    val albumId = albumRepository.createAlbum(albumName, "Imported from device")
                    albumRepository.addToAlbum(albumId, mediaId)
                }
                mediaId
            }.onSuccess { id ->
                onImported(id)
            }.onFailure {
                _error.value = it.message ?: "Import failed"
            }
            _busy.value = false
        }
    }

    fun importMultiple(
        items: List<Pair<Uri, Boolean>>,
        commonDescription: String,
        albumName: String? = null,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            _totalCount.value = items.size
            _progressCount.value = 0
            
            var hasError = false
            val albumId = if (!albumName.isNullOrBlank()) {
                albumRepository.createAlbum(albumName, "Imported collection")
            } else null

            items.forEach { (uri, isVideo) ->
                runCatching {
                    val name = uri.lastPathSegment ?: "Imported Media"
                    val mediaId = repository.importFromUri(uri, name, commonDescription, isVideo)
                    if (albumId != null) {
                        albumRepository.addToAlbum(albumId, mediaId)
                    }
                }.onFailure {
                    hasError = true
                }
                _progressCount.value++
            }
            
            if (hasError) {
                _error.value = "Some files failed to import"
            }
            _busy.value = false
            onDone()
        }
    }

    companion object {
        fun factory(repository: MediaRepository, albumRepository: AlbumRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ImportPreviewViewModel::class.java))
                    return ImportPreviewViewModel(repository, albumRepository) as T
                }
            }
    }
}
