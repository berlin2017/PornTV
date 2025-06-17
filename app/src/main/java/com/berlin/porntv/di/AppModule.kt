package com.berlin.porntv.di


import com.berlin.porntv.data.network.VideoApiService
import com.berlin.porntv.data.network.VideoScraper
import com.berlin.porntv.data.repository.VideoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory // Import GsonConverterFactory
import java.util.concurrent.TimeUnit
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
    fun provideVideoRepository(videoScraper: VideoScraper, videoApiService: VideoApiService): VideoRepository {
        return VideoRepository(videoScraper, videoApiService)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // 2. 创建 HttpLoggingInterceptor 实例
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // 设置日志级别，BODY 会打印请求和响应的头部和主体
            // 其他级别包括: NONE, BASIC, HEADERS
            // 在生产环境中，你可能希望只在 Debug 构建时启用详细日志，或使用更低的级别
            // if (BuildConfig.DEBUG) {
            level = HttpLoggingInterceptor.Level.BODY
            // } else {
            //     level = HttpLoggingInterceptor.Level.NONE
            // }
        }

        // 配置 OkHttpClient (例如添加拦截器、超时等)
        return OkHttpClient.Builder()
             .addInterceptor(loggingInterceptor)
            .connectTimeout(100, TimeUnit.SECONDS) // 设置连接超时
            .writeTimeout(100, TimeUnit.SECONDS) // 设置连接超时
            .readTimeout(100, TimeUnit.SECONDS) // 设置连接超时
            .callTimeout(100, TimeUnit.SECONDS) // 设置连接超时
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://192.168.1.199:8080") // 替换为你的 API Base URL
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create()) // 正确的实例化方式
            .build()
    }

    @Provides
    @Singleton
    fun provideVideoApiService(retrofit: Retrofit): VideoApiService {
        return retrofit.create(VideoApiService::class.java)
    }
}