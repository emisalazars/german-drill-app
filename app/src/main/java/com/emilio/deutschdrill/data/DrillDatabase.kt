package com.emilio.deutschdrill.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CardEntity::class, ReviewEntity::class, SessionEntity::class, OwnSentenceEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DrillDatabase : RoomDatabase() {
    abstract fun dao(): DrillDao

    companion object {
        fun create(context: Context): DrillDatabase =
            Room.databaseBuilder(context, DrillDatabase::class.java, "deutschdrill.db").build()
    }
}
