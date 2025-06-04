package com.berlin.porntv.di


import com.berlin.porntv.data.network.VideoScraper
import com.berlin.porntv.data.repository.VideoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideVideoScraper(): VideoScraper {
        return VideoScraper()
    }

    @Provides
    @Singleton
    fun provideVideoRepository(videoScraper: VideoScraper): VideoRepository {
        return VideoRepository(videoScraper)
    }
}