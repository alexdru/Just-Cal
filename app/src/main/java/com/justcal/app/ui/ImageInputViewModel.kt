package com.justcal.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.camera.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ImageInputState(val busy: Boolean = false, val image: PreparedImage? = null, val error: Boolean = false)

@HiltViewModel
class ImageInputViewModel @Inject constructor(val images: ImagePreprocessor) : ViewModel() {
    private val mutableState = MutableStateFlow(ImageInputState())
    val state = mutableState.asStateFlow()
    private var transferred = false

    fun prepare(uri: Uri, source: ImageSource, request: ScanRequest) {
        if (mutableState.value.busy) return
        mutableState.value = ImageInputState(busy = true)
        viewModelScope.launch {
            try {
                val image = images.prepare(uri, source, request)
                mutableState.value = ImageInputState(image = image)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = ImageInputState(error = true)
            }
        }
    }

    fun retry() { if (!mutableState.value.busy) mutableState.value = ImageInputState() }

    fun transfer() { transferred = true }

    override fun onCleared() {
        if (!transferred) mutableState.value.image?.let { images.discard(it.uri) }
    }
}
