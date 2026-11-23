
package com.example.campuswallet

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TransactionDao {

    @Insert
    fun insertTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY id DESC")
    fun getTransactionsForUser(userId: Int): List<Transaction>
}
