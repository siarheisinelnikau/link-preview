package com.link.preview.sample

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.link.preview.LinkPreview
import com.link.preview.PreviewState
import com.link.preview.okhttp.OkHttpPreviewFetcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PreviewViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    val input = savedStateHandle.getStateFlow("input", "https://kotlinlang.org")
    private val mutableState = MutableStateFlow<PreviewState?>(null)
    val state = mutableState.asStateFlow()
    private val loader = LinkPreview(OkHttpPreviewFetcher(client))
    private var request: Job? = null

    fun updateInput(value: String) {
        savedStateHandle["input"] = value
    }

    fun load() {
        request?.cancel()
        val text = input.value
        request = viewModelScope.launch {
            loader.observe(text).collect { mutableState.value = it }
        }
    }

    fun cancel() {
        request?.cancel()
        mutableState.value = null
    }

    private companion object {
        val client = OkHttpPreviewFetcher.defaultClient()
    }
}
