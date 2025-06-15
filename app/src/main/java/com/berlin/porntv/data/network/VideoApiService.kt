package com.berlin.porntv.data.network

import com.berlin.porntv.data.model.VideoItemModel
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface VideoApiService {
    @POST("api/video-info") // 假设 videoId 是路径参数
    suspend fun getVideoInfo(@Body urlRequest: VideoUrlRequest): Response<VideoItemModel>
}

data class VideoUrlRequest(val url: String)