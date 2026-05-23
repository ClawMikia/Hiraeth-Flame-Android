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

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun import(
        uri: Uri,
        displayName: String,
        description: String,
        isVideo: Boolean,
        onImported: (Long) -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                // Passes title, description, and metadata down cleanly into repository layer function
                repository.importFromUri(uri, displayName, description, isVideo)
            }.onSuccess { id ->
                onImported(id)
            }.onFailure {
                _error.value = it.message ?: "Import failed"
            }
            _busy.value = false
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
