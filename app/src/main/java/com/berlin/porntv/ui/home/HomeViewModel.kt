package com.berlin.porntv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berlin.porntv.data.model.Video
import com.berlin.porntv.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadVideos()
    }

    fun loadVideos(page: Int = 1) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                videoRepository.getVideos(page).collectLatest { videos ->
                    _uiState.value = _uiState.value.copy(
                        videos = videos,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun loadMoreVideos() {
        val currentPage = _uiState.value.currentPage
        val nextPage = currentPage + 1

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)

            try {
                videoRepository.getVideos(nextPage).collectLatest { newVideos ->
                    val updatedVideos = _uiState.value.videos + newVideos
                    _uiState.value = _uiState.value.copy(
                        videos = updatedVideos,
                        currentPage = nextPage,
                        isLoadingMore = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = e.message
                )
            }
        }
    }
}

data class HomeUiState(
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val error: String? = null
)