package com.hiraeth.flame.ui.albums

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiraeth.flame.data.db.AlbumWithMedia
import com.hiraeth.flame.data.repository.AlbumRepository
import com.hiraeth.flame.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumsViewModel(
    private val albumRepository: AlbumRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    val albums: StateFlow<List<AlbumWithMedia>> = albumRepository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus

    fun createAlbum(name: String, category: String) {
        viewModelScope.launch {
            albumRepository.createAlbum(name, category)
        }
    }

    fun importFolder(context: Context, treeUri: Uri) {
        viewModelScope.launch {
            _importStatus.value = "Importing folder..."
            try {
                val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@launch
                val folderName = root.name ?: "Imported Album"
                
                val files = root.listFiles().filter { 
                    val type = it.type ?: ""
                    type.startsWith("image/") || type.startsWith("video/")
                }

                if (files.isEmpty()) {
                    _importStatus.value = "No media files found in folder."
                    return@launch
                }

                val albumId = albumRepository.createAlbum(folderName, "Imported from folder")
                
                withContext(Dispatchers.IO) {
                    files.forEachIndexed { index, file ->
                        val isVideo = file.type?.startsWith("video/") == true
                        val mediaId = mediaRepository.importFromUri(
                            uri = file.uri,
                            suggestedName = file.name ?: "Imported ${index + 1}",
                            description = "Imported from folder $folderName",
                            isVideo = isVideo
                        )
                        albumRepository.addToAlbum(albumId, mediaId)
                    }
                }
                _importStatus.value = "Successfully imported ${files.size} items into '$folderName'"
            } catch (e: Exception) {
                _importStatus.value = "Import failed: ${e.message}"
            }
        }
    }

    fun clearImportStatus() {
        _importStatus.value = null
    }

    companion object {
        fun factory(albumRepository: AlbumRepository, mediaRepository: MediaRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(AlbumsViewModel::class.java))
                    return AlbumsViewModel(albumRepository, mediaRepository) as T
                }
            }
    }
}
