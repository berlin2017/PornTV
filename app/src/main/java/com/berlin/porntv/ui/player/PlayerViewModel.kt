package com.berlin.porntv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berlin.porntv.data.model.VideoDetail
import com.berlin.porntv.data.model.VideoItemModel
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
                            isLoading = false, error = "无法加载视频详情"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, error = e.message
                )
            }
        }
    }

    fun selectQuality(quality: VideoQuality) {
        _uiState.value = _uiState.value.copy(selectedQuality = quality)
    }

    fun switchQuality() {
        val currentQuality = _uiState.value.selectedQuality
        val videoDetail = _uiState.value.videoDetail

        if (videoDetail != null && videoDetail.qualities.isNotEmpty()) {
            val qualities = videoDetail.qualities
            val currentIndex = qualities.indexOf(currentQuality)
            val nextIndex = (currentIndex + 1) % qualities.size
            _uiState.value = _uiState.value.copy(selectedQuality = qualities[nextIndex])
        }
    }

    fun fetchVideoDetails(videoId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            when (val result = videoRepository.getVideoInfoFromApi(videoId)) {
                is com.berlin.porntv.data.repository.Result.Success -> {
                    result.data.toVideoDetail()
                        ?.let { videoDetail ->
                            _uiState.value = _uiState.value.copy(
                                videoDetail = videoDetail,
                                selectedQuality = videoDetail.qualities.lastOrNull(),
                                isLoading = false,
                                error = null
                            )
                        } ?: run {
                        _uiState.value = _uiState.value.copy(
                            videoDetail = null, isLoading = false, error = "无法加载视频详情"
                        )
                    }
                }

                is com.berlin.porntv.data.repository.Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, error = "无法加载视频详情"
                    )
                    // Log result.exception for more details
                }
            }
        }
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

fun VideoItemModel.toVideoDetail(): VideoDetail? {
    return if (this.formats?.isNotEmpty() == true) {
        VideoDetail(
            id = this.title ?: "Unknown",
            title = this.title ?: "Unknown",
            videoUrl = this.formats.lastOrNull()?.url ?: "",
            qualities = this.formats.map { format ->
                VideoQuality(
                    label = format.formatId ?: "Unknown", url = format.url ?: "",
                    formatId = format.formatId,
                    protocol = format.protocol
                )
            })
    } else {
        null
    }
}