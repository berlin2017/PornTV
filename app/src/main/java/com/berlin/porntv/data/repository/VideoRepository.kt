package com.berlin.porntv.data.repository

import com.berlin.porntv.data.model.Video
import com.berlin.porntv.data.model.VideoDetail
import com.berlin.porntv.data.model.VideoItemModel
import com.berlin.porntv.data.network.VideoApiService
import com.berlin.porntv.data.network.VideoScraper
import com.berlin.porntv.data.network.VideoUrlRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okio.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val videoScraper: VideoScraper,
    private val videoApiService: VideoApiService
) {
    fun getVideos(page: Int = 1): Flow<List<Video>> = flow {
        val videos = videoScraper.getVideos(page)
        emit(videos)
    }

    fun getVideoDetail(videoId: String): Flow<VideoDetail?> = flow {
        val videoDetail = videoScraper.getVideoDetail(videoId)
        emit(videoDetail)
    }


    /**
     * 从网络API获取视频的详细信息。
     * @param videoId 要获取详情的视频ID。
     * @return Result<VideoItemModel> 包含成功获取的 VideoItemModel 或错误信息。
     *         或者直接返回 VideoItemModel?，如果为 null 则表示获取失败。
     */
    suspend fun getVideoInfoFromApi(videoId: String): Result<VideoItemModel> {
        return withContext(Dispatchers.IO) { // 将网络请求切换到 IO 线程
            try {
                val response = videoApiService.getVideoInfo(VideoUrlRequest(videoId))
                if (response.isSuccessful) {
                    val videoItem = response.body()
                    if (videoItem != null) {
                        Result.Success(videoItem)
                    } else {
                        Result.Error(Exception("Response body is null"), "未能获取视频数据")
                    }
                } else {
                    // 处理HTTP错误 (例如 404, 500 等)
                    // response.errorBody()?.string() 可以获取错误信息
                    Result.Error(
                        Exception("API Error: ${response.code()} - ${response.message()}"),
                        "获取视频信息失败 (错误码: ${response.code()})"
                    )
                }
            } catch (e: IOException) {
                // 处理网络连接问题
                Result.Error(e, "网络连接错误，请稍后重试")
            } catch (e: Exception) {
                // 处理其他未知异常，例如JSON解析错误
                Result.Error(e, "获取视频信息时发生未知错误")
            }
        }
    }

}

// 假设你有一个表示成功或失败的通用 Result 类，这是一个很好的实践
// 如果没有，你可以直接返回 VideoItemModel? 并通过 null 判断失败
open class Result<out T : Any> {
    data class Success<out T : Any>(val data: T) : Result<T>()
    data class Error(val exception: Exception, val message: String? = null) : Result<Nothing>()
}

