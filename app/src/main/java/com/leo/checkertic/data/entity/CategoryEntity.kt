package com.leo.checkertic.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int,

    @ColumnInfo(name = "recurrence_type")
    val recurrenceType: String = "once",

    @ColumnInfo(name = "recurrence_custom_days")
    val recurrenceCustomDays: Int = 0,

    @ColumnInfo(name = "last_period_key")
    val lastPeriodKey: String = ""
)
