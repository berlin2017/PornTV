package com.berlin.porntv.data.model


data class VideoDetail(
    val id: String,
    val title: String,
    val videoUrl: String,
    val qualities: List<VideoQuality>
)

data class VideoQuality(
    val label: String,
    val url: String,
    val formatId: String? = null, // 可选：格式ID
    val protocol: String? = null // 可选：协议类型（如 "mp4", "hls" 等）
)

data class VideoItemModel(
    val title: String?,
    val formats: List<YtDlpFormat>?,
    val error: String? = null,
    val rawOutput: String? = null // 可选：包含原始输出以供调试
)

data class YtDlpFormat(
    val formatId: String?,
    val extension: String?,
    val resolution: String?,
    val fps: Float?,
    val filesize: Long?,
    val filesizeApprox: Long?, // yt-dlp 2023.03.04+ uses filesize_approx
    val tbr: Double?, // average bitrate (kbps)
    val vcodec: String?,
    val acodec: String?,
    val url: String?, // Direct download URL (if available via -g or in format info)
    val note: String?,
    val height: Int?,
    val width: Int?,
    val protocol: String?
)
