package com.berlin.porntv.data.network


import com.berlin.porntv.data.model.Video
import com.berlin.porntv.data.model.VideoDetail
import com.berlin.porntv.data.model.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoScraper @Inject constructor() {

    private val baseUrl = "https://www.pornhub.com"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

    suspend fun getVideos(page: Int = 1): List<Video> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/video?page=$page"
            val doc = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(30000)
                .get()

            parseVideoList(doc)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseVideoList(doc: Document): List<Video> {
        val videos = mutableListOf<Video>()

        val videoElements = doc.select("div.videoBox")
        for (element in videoElements) {
            try {
                val linkElement = element.selectFirst("a")
                val href = linkElement?.attr("href") ?: continue
                val id = href.substringAfterLast("/").substringBefore("?")

                val title = linkElement.attr("title")
                val thumbnailUrl = element.selectFirst("img")?.attr("data-thumb_url") ?: ""
                val duration = element.selectFirst("div.duration")?.text() ?: ""
                val views = element.selectFirst("span.views")?.text() ?: ""
                val rating = element.selectFirst("div.rating-container")?.text() ?: ""

                videos.add(Video(id, title, thumbnailUrl, duration, views, rating))
            } catch (e: Exception) {
                e.printStackTrace()
                continue
            }
        }

        return videos
    }

    suspend fun getVideoDetail(videoId: String): VideoDetail? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/view_video.php?viewkey=$videoId"
            val doc = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(30000)
                .get()

            val title = doc.selectFirst("h1.title")?.text() ?: ""

            // 提取视频URL和质量选项
            val scriptElements = doc.select("script")
            var videoUrl = ""
            val qualities = mutableListOf<VideoQuality>()

            for (script in scriptElements) {
                val scriptContent = script.html()
                if (scriptContent.contains("flashvars")) {
                    // 提取视频URL
                    val mediaDefinitionsPattern = Pattern.compile("mediaDefinitions\\s*:\\s*(\\[.*?\\])")
                    val matcher = mediaDefinitionsPattern.matcher(scriptContent)

                    if (matcher.find()) {
                        val mediaDefinitionsJson = matcher.group(1)
                        // 这里简化处理，实际应用中应该使用JSON解析库
                        val qualityPattern = Pattern.compile(""""quality"\s*:\s*"(.*?)"""")
                        val qualityMatcher = qualityPattern.matcher(mediaDefinitionsJson)

                        val urlPattern = Pattern.compile(""""videoUrl"\s*:\s*"(.*?)"""")
                        val urlMatcher = urlPattern.matcher(mediaDefinitionsJson)

                        while (qualityMatcher.find() && urlMatcher.find()) {
                            val quality = qualityMatcher.group(1)
                            val url = urlMatcher.group(1).replace("\\\\", "")

                            if (videoUrl.isEmpty()) {
                                videoUrl = url
                            }

                            qualities.add(VideoQuality(quality, url))
                        }
                    }
                    break
                }
            }

            VideoDetail(videoId, title, videoUrl, qualities)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}