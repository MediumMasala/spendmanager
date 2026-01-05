package com.spendmanager.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.spendmanager.app.data.model.AppSource
import com.spendmanager.app.data.model.DeviceInfo
import com.spendmanager.app.data.model.EventQueueItem
import com.spendmanager.app.data.model.Transaction
import com.spendmanager.app.data.model.WeeklySummary

@Database(
    entities = [
        EventQueueItem::class,
        Transaction::class,
        WeeklySummary::class,
        DeviceInfo::class,
        AppSource::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventQueueDao(): EventQueueDao
    abstract fun transactionDao(): TransactionDao
    abstract fun weeklySummaryDao(): WeeklySummaryDao
    abstract fun deviceInfoDao(): DeviceInfoDao
    abstract fun appSourceDao(): AppSourceDao

    companion object {
        private const val DATABASE_NAME = "spendmanager.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
