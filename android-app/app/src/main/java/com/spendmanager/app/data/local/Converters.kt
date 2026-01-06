package com.spendmanager.app.data.local

import androidx.room.TypeConverter
import com.spendmanager.app.data.model.AppCategory
import com.spendmanager.app.data.model.TransactionDirection
import com.spendmanager.app.data.model.UploadStatus

class Converters {
    // UploadStatus
    @TypeConverter
    fun fromUploadStatus(status: UploadStatus): String = status.name

    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus = UploadStatus.valueOf(value)

    // TransactionDirection
    @TypeConverter
    fun fromTransactionDirection(direction: TransactionDirection): String = direction.name

    @TypeConverter
    fun toTransactionDirection(value: String): TransactionDirection = TransactionDirection.valueOf(value)

    // AppCategory
    @TypeConverter
    fun fromAppCategory(category: AppCategory): String = category.name

    @TypeConverter
    fun toAppCategory(value: String): AppCategory = AppCategory.valueOf(value)
}
