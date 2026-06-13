package com.hiraeth.flame.ui.mediaimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiraeth.flame.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ImportPreviewViewModel(
    private val repository: MediaRepository,
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
        onImported: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                repository.importFromUri(uri, displayName, description, isVideo)
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
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            _totalCount.value = items.size
            _progressCount.value = 0
            
            var hasError = false
            items.forEach { (uri, isVideo) ->
                runCatching {
                    val name = uri.lastPathSegment ?: "Imported Media"
                    repository.importFromUri(uri, name, commonDescription, isVideo)
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
        fun factory(repository: MediaRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ImportPreviewViewModel::class.java))
                    return ImportPreviewViewModel(repository) as T
                }
            }
    }
}
