package com.aman.gigi.repository

import com.aman.gigi.db.RecentlySentGif
import com.aman.gigi.db.RecentlySentGifDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GifRepository @Inject constructor(
    private val recentlySentGifDao: RecentlySentGifDao
) {

    fun getRecentGifs(limit: Int = 20): Flow<List<String>> {
        return recentlySentGifDao.getRecentGifs(limit)
    }

    suspend fun addRecentGif(url: String) {
        val gif = RecentlySentGif(url = url)
        recentlySentGifDao.insert(gif)
        recentlySentGifDao.deleteOldGifs(20)
    }
}
