package com.wordbattle.com.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedProfileEntity::class,
        CachedGameEntity::class,
        CachedRoomEntity::class,
        CachedCampaignProgressEntity::class,
        CachedRequestEntity::class,
        CachedMessageEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun gameDao(): GameDao
    abstract fun roomCacheDao(): RoomCacheDao
    abstract fun campaignProgressDao(): CampaignProgressDao
    abstract fun requestDao(): RequestDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "word_battle.db"
            ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
        }
    }
}
