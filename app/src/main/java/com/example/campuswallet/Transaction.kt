
package com.example.campuswallet

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["userId"])]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String,
    val amount: Double,
    val category: String,
    val userId: Int,
    val timestamp: Long = System.currentTimeMillis()
)
