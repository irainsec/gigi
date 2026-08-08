package com.aman.gigi.hilt

import com.aman.gigi.data.sync.ScribbleSyncManager
import com.aman.gigi.network.WebSocketClient
import com.aman.gigi.data.client.ClientIdentityStore
import com.aman.gigi.repository.ConnectionRepository
import com.aman.gigi.repository.LoveCardRepository
import com.aman.gigi.repository.OutboundActionRepository
import com.aman.gigi.repository.ScribbleRepository
import com.aman.gigi.repository.SharedAlarmRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing network and sync instances
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideWebSocketClient(): WebSocketClient {
        return WebSocketClient()
    }

    @Provides
    @Singleton
    fun provideGson(): com.google.gson.Gson {
        return com.google.gson.Gson()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    fun provideHttpUploader(): com.aman.gigi.network.HttpUploader {
        return com.aman.gigi.network.HttpUploader()
    }

    @Provides
    @Singleton
    fun provideScribbleSyncManager(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        scribbleRepository: ScribbleRepository,
        outboundActionRepository: OutboundActionRepository,
        connectionRepository: ConnectionRepository,
        bootstrapManager: com.aman.gigi.data.client.ConnectionBootstrapManager,
        identityStore: ClientIdentityStore,
        sharedAlarmRepository: SharedAlarmRepository,
        loveCardRepository: LoveCardRepository,
        webSocketClient: WebSocketClient,
        chatRepository: com.aman.gigi.repository.ChatRepository,
        httpUploader: com.aman.gigi.network.HttpUploader,
        networkMonitor: com.aman.gigi.utils.NetworkMonitor,
        fileScanner: com.aman.gigi.utils.FileScanner,
        sharedAlbumStore: com.aman.gigi.data.music.SharedAlbumStore,
        breakCardDao: com.aman.gigi.data.dao.BreakCardDao,
        nowPlayingTracker: com.aman.gigi.data.nowplaying.NowPlayingTracker
    ): ScribbleSyncManager {
        return ScribbleSyncManager(
            context,
            scribbleRepository,
            outboundActionRepository,
            connectionRepository,
            bootstrapManager,
            identityStore,
            sharedAlarmRepository,
            loveCardRepository,
            webSocketClient,
            chatRepository,
            httpUploader,
            networkMonitor,
            fileScanner,
            sharedAlbumStore,
            breakCardDao,
            nowPlayingTracker
        )
    }
}
