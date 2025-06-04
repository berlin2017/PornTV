package com.berlin.porntv.data.model


data class VideoDetail(
    val id: String,
    val title: String,
    val videoUrl: String,
    val qualities: List<VideoQuality>
)

data class VideoQuality(
    val label: String,
    val url: String
)