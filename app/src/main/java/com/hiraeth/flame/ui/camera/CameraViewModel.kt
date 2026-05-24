package com.hiraeth.flame.ui.camera

import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hiraeth.flame.data.local.MediaStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class CaptureOrientation {
    PORTRAIT, LANDSCAPE
}

class CameraViewModel(
    private val storage: MediaStorage,
) : ViewModel() {

    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = _lensFacing

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording

    private val _captureOrientation = MutableStateFlow(CaptureOrientation.PORTRAIT)
    val captureOrientation: StateFlow<CaptureOrientation> = _captureOrientation

    private val _lastMessage = MutableStateFlow<String?>(null)
    val lastMessage: StateFlow<String?> = _lastMessage

    fun toggleLens() {
        _lensFacing.value =
            if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
    }

    fun setRecording(value: Boolean) {
        _recording.value = value
    }

    fun clearMessage() {
        _lastMessage.value = null
    }

    fun createPhotoOutputFile() = storage.createCameraPhotoFile()
    fun createVideoOutputFile() = storage.createCameraVideoFile()

    fun getTargetRotation(): Int {
        return when (captureOrientation.value) {
            CaptureOrientation.PORTRAIT -> Surface.ROTATION_0
            CaptureOrientation.LANDSCAPE -> Surface.ROTATION_90
        }
    }

    companion object {
        fun factory(storage: MediaStorage): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(CameraViewModel::class.java))
                    return CameraViewModel(storage) as T
                }
            }
    }
}
