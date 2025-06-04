package com.berlin.porntv.data.repository

import com.berlin.porntv.data.model.Video
import com.berlin.porntv.data.model.VideoDetail
import com.berlin.porntv.data.network.VideoScraper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val videoScraper: VideoScraper
) {
    fun getVideos(page: Int = 1): Flow<List<Video>> = flow {
        val videos = videoScraper.getVideos(page)
        emit(videos)
    }

    fun getVideoDetail(videoId: String): Flow<VideoDetail?> = flow {
        val videoDetail = videoScraper.getVideoDetail(videoId)
        emit(videoDetail)
    }
}