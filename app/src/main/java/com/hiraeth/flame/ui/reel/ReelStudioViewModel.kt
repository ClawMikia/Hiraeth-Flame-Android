package com.hiraeth.flame.ui.reel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Picks multiple local videos and stages them under [com.hiraeth.flame.data.local.MediaStorage.reelsDir].
 * True frame-accurate merge is delegated to Media3 Transformer in production; this sample copies sources in order.
 */
class ReelStudioViewModel(
    private val repository: MediaRepository,
    private val reelsRoot: File,
) : ViewModel() {

    private val _selected = MutableStateFlow<List<Long>>(emptyList())
    val selected: StateFlow<List<Long>> = _selected

    val videos: StateFlow<List<MediaEntity>> = repository.observeAll()
        .map { list -> list.filter { it.isVideo } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    fun toggle(id: Long) {
        val current = _selected.value.toMutableList()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selected.value = current
    }

    fun clearSelection() {
        _selected.value = emptyList()
    }

    fun stageReelProject() {
        viewModelScope.launch {
            val ids = _selected.value
            if (ids.size < 2) {
                _status.value = "Select at least two videos."
                return@launch
            }
            _status.value = "Staging ${ids.size} clips..."
            runCatching {
                withContext(Dispatchers.IO) {
                    val folderName = "reel_${System.currentTimeMillis()}"
                    val folder = File(reelsRoot, folderName).apply { mkdirs() }
                    if (!folder.exists()) throw Exception("Failed to create folder $folderName")

                    ids.forEachIndexed { index, mediaId ->
                        val entity = repository.getById(mediaId) ?: return@forEachIndexed
                        val src = repository.resolveFile(entity)
                        if (!src.exists()) return@forEachIndexed
                        val dest = File(folder, "${index.toString().padStart(2, '0')}_clip.mp4")
                        src.copyTo(dest, overwrite = true)
                    }
                    folderName
                }
            }.onSuccess { folderName ->
                _status.value = "Staged in $folderName. Files ready for processing."
            }.onFailure {
                _status.value = "Error: ${it.message}"
            }
        }
    }

    companion object {
        fun factory(repository: MediaRepository, reelsRoot: File): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ReelStudioViewModel::class.java))
                    return ReelStudioViewModel(repository, reelsRoot) as T
                }
            }
    }
}
