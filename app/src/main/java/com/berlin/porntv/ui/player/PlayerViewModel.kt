package com.berlin.porntv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berlin.porntv.data.model.VideoDetail
import com.berlin.porntv.data.model.VideoQuality
import com.berlin.porntv.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun loadVideoDetail(videoId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                videoRepository.getVideoDetail(videoId).collectLatest { videoDetail ->
                    if (videoDetail != null) {
                        _uiState.value = _uiState.value.copy(
                            videoDetail = videoDetail,
                            selectedQuality = videoDetail.qualities.firstOrNull(),
                            isLoading = false,
                            error = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "无法加载视频详情"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun selectQuality(quality: VideoQuality) {
        _uiState.value = _uiState.value.copy(selectedQuality = quality)
    }
}

data class PlayerUiState(
    val videoDetail: VideoDetail? = null,
    val selectedQuality: VideoQuality? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showQualitySelector: Boolean = false
) {
    val currentVideoUrl: String
        get() = selectedQuality?.url ?: videoDetail?.videoUrl ?: ""
}