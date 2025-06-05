package com.berlin.porntv.data.network


import android.util.Log
import com.berlin.porntv.data.model.Video
import com.berlin.porntv.data.model.VideoDetail
import com.berlin.porntv.data.model.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
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
        Log.d("VideoScraper", "Attempting to parse video list with NEW (Structure 2) selectors.")

        // 主选择器，li[data-video-id] 或 li[data-video-vkey] 应该仍然有效
        val videoElements = doc.select("li[data-video-vkey]") // 或者 "li.pcVideoListItem" 如果更稳定
        Log.d("VideoScraper", "Found ${videoElements.size} video elements using 'li[data-video-vkey]'.")

        for ((index, element) in videoElements.withIndex()) {
            // 打印每个 element 的 HTML 以供调试，确保选择器选到了正确的元素块
            // Log.d("VideoScraper", "Processing element #$index HTML: ${element.html().take(600)}")
            try {
                val id = element.attr("data-video-vkey")
                if (id.isEmpty()) {
                    Log.w("VideoScraper", "Element #$index: Skipping, missing data-video-vkey.")
                    continue
                }

                // 标题和链接
                val titleLinkElement = element.selectFirst("span.title a")
                val title = titleLinkElement?.attr("title")?.trim() // 优先取 title 属性
                    ?: titleLinkElement?.text()?.trim() // 其次取 text 内容
                    ?: "N/A"
                // val href = titleLinkElement?.attr("href") ?: ""

                // 缩略图
                // img 标签现在在 div.phimage > a > img 路径下
                // 并且有 js-videoThumb 类
                val thumbnailElement = element.selectFirst("div.phimage a img.js-videoThumb")
                var thumbnailUrl = thumbnailElement?.attr("src")
                if (thumbnailUrl.isNullOrEmpty()) {
                    thumbnailUrl = thumbnailElement?.attr("data-image")
                }
                if (thumbnailUrl.isNullOrEmpty()) {
                    thumbnailUrl = thumbnailElement?.attr("data-path") // 最后一个备选项
                }
                thumbnailUrl = thumbnailUrl ?: ""


                // 时长
                val duration = element.selectFirst("var.duration")?.text()?.trim() ?: "N/A"

                // 观看次数
                val viewsText = element.selectFirst("span.views var")?.text()?.trim() ?: "0"
                // 移除 "K", "M" 等，并转换为数字 (如果需要精确数值，这里需要更复杂的处理)
                val views = viewsText.replace(Regex("[^0-9.]"), "")


                // 评分 - 在此 HTML 片段中仍然没有明确的评分元素
                val rating = "N/A"

                if (title != "N/A" && thumbnailUrl.isNotEmpty()) {
                    videos.add(Video(id, title, thumbnailUrl, duration, views, rating))
                    Log.d("VideoScraper", "Element #$index: Parsed: id=$id, title=$title, thumb=$thumbnailUrl")
                } else {
                    Log.w("VideoScraper", "Element #$index: Skipped due to missing title or thumbnail. Title: $title, Thumb: $thumbnailUrl, ID: $id")
                    // 如果跳过，打印当前 element 的 HTML 帮助分析原因
                    // Log.w("VideoScraper", "Skipped element HTML: ${element.html()}")
                }

            } catch (e: Exception) {
                Log.e("VideoScraper", "Error parsing element #$index: ${e.message}", e)
                // 打印出错的element HTML
                // Log.e("VideoScraper", "Error on element HTML: ${element.html()}", e)
                e.printStackTrace()
                continue
            }
        }

        Log.d("VideoScraper", "Successfully parsed ${videos.size} videos from new structure.")
        return videos
    }

    suspend fun getVideoDetail(videoId: String): VideoDetail? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/view_video.php?viewkey=$videoId" // 或者视频详情页的实际URL结构

            findMediaApiEndpoints(url)
            val doc = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(30000)
                .get()

            val title = doc.selectFirst("h1.title")?.text()?.trim() ?: // 尝试从常见的标题元素获取
            doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim() ?: // 备选：从og:title元标签获取
            doc.title().split("|").firstOrNull()?.trim() ?: "N/A" // 最后的备选：从页面标题获取

            // 尝试提取 <video> 标签内的 <source> 标签的 src 属性
            // 1. 尝试通过 video 标签的特定 class (如果知道)
            var videoElement = doc.selectFirst("video.mgp_videoElement") // 使用你提供的 class
            var sourceElement = videoElement?.selectFirst("source[type='video/mp4']")
            var actualVideoUrl = sourceElement?.attr("src")

            // 2. 如果上面的选择器失败，尝试更通用的选择器
            if (actualVideoUrl.isNullOrEmpty()) {
                sourceElement = doc.selectFirst("video > source[type='video/mp4']") // 直接子元素
                actualVideoUrl = sourceElement?.attr("src")
            }

            if (actualVideoUrl.isNullOrEmpty()) {
                sourceElement = doc.selectFirst("video source") // 更通用的 video 下的 source
                actualVideoUrl = sourceElement?.attr("src")
            }

            // 如果仍然没有找到，可以记录下来或者返回 null/空字符串
            if (actualVideoUrl.isNullOrEmpty()) {
                Log.w("VideoScraper", "Could not find direct video source URL for videoId: $videoId on page: $url")
                // 在这种情况下，你可能需要回到之前基于 <script> 标签的解析逻辑作为备选方案，
                // 或者接受无法直接获取视频源的事实。
                // 为了简单起见，这里我们直接返回 null 或者一个空的 VideoDetail
                // return@withContext null
            }

            // 清理URL中的 HTML 实体，例如 &amp; -> &
            actualVideoUrl = actualVideoUrl?.replace("&amp;", "&")

            // 假设我们只关心一个主要的视频URL和它的质量（如果能从文件名或URL中推断）
            // 对于你提供的HTML，质量信息似乎在URL路径中 "480P_2000K"
            // 我们可以尝试从URL中提取它，但这会非常依赖于URL格式
            val qualities = mutableListOf<VideoQuality>()
            if (!actualVideoUrl.isNullOrEmpty()) {
                // 尝试从URL中提取质量信息 (这是一个非常粗略的示例)
                val qualityRegex = Regex("/(\\d+P(?:_\\d+K)?)/") // 匹配如 /480P_2000K/ 或 /720P/
                val matchResult = qualityRegex.find(actualVideoUrl)
                val qualityString = matchResult?.groups?.get(1)?.value ?: "Unknown"
                qualities.add(VideoQuality(qualityString, actualVideoUrl))
            } else {
                // 如果没有直接的 URL，你可能需要在这里处理之前从 flashvars 提取的 qualities
                // 但根据请求，我们优先使用 <source> 标签
                Log.d("VideoScraper", "No direct video URL found, qualities list will be empty or rely on other methods.")
            }


            if (title != "N/A" && !actualVideoUrl.isNullOrEmpty()) {
                VideoDetail(videoId, title, actualVideoUrl, qualities)
            } else {
                Log.w("VideoScraper", "Failed to get essential video details for $videoId. Title: $title, URL: $actualVideoUrl")
                null
            }

        } catch (e: Exception) {
            Log.e("VideoScraper", "Error fetching video detail for $videoId: ${e.message}", e)
            null
        }
    }

    fun findMediaApiEndpoints(pageUrl: String): List<String> {
        val foundUrls = mutableListOf<String>()

        try {
            val document: Document = Jsoup.connect(pageUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.102 Safari/537.36") // 设置一个常见的User-Agent
                .timeout(10000) // 设置超时时间
                .get()

            // 1. 搜索 <script> 标签中的内容
            val scripts: List<Element> = document.select("script")
            for (script in scripts) {
                val scriptContent: String = script.html() // 或者 script.data() 获取脚本内容

                // 简单的文本包含检查
                if (scriptContent.contains("get_media", ignoreCase = true)) {
                    // 如果找到 "get_media"，你可能需要更复杂的逻辑来提取完整的 URL
                    // 例如，使用正则表达式查找包含 "get_media" 的 URL 字符串
                    // 这个正则表达式是一个非常基础的例子，你可能需要根据实际情况调整
                    val urlPattern = Regex("""['"]([^'"]*get_media[^'"]*)['"]""")
                    urlPattern.findAll(scriptContent).forEach { matchResult ->
                        val potentialUrl = matchResult.groupValues[1]
                        if (potentialUrl.startsWith("http://") || potentialUrl.startsWith("https://") || potentialUrl.startsWith("/")) {
                            foundUrls.add(potentialUrl)
                        }
                    }

                    // 如果 "get_media" 是某个变量的一部分，或者通过字符串拼接而成，提取会更复杂
                    // println("Found 'get_media' in script: ${scriptContent.substring(0, Math.min(scriptContent.length, 200))}...") // 打印部分内容以供分析
                }
            }

            // 2. (可选) 搜索 HTML 元素属性
            // 例如，查找所有包含 "get_media" 的 href, src, data-*, action 属性
            val elementsWithAttributes = document.select("*[href*='get_media'], *[src*='get_media'], *[data-*='get_media'], *[action*='get_media']")
            for (element in elementsWithAttributes) {
                element.attributes().forEach { attr ->
                    if (attr.value.contains("get_media", ignoreCase = true)) {
                        if (attr.value.startsWith("http://") || attr.value.startsWith("https://") || attr.value.startsWith("/")) {
                            foundUrls.add(attr.value)
                        }
                    }
                }
            }

        } catch (e: IOException) {
            println("抓取或解析URL时出错: ${e.message}")
        }

        return foundUrls.distinct() // 返回去重后的结果
    }

}