
package com.example.campuswallet

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TransactionDao {

    @Insert
    fun insertTransaction(transaction: Transaction): Long

    @Delete
    fun deleteTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY id DESC")
    fun getTransactionsForUser(userId: Int): List<Transaction>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE userId = :userId AND type = 'Income'")
    fun getTotalIncome(userId: Int): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE userId = :userId AND type = 'Expense'")
    fun getTotalExpenses(userId: Int): Double

    @Query("DELETE FROM transactions WHERE userId = :userId")
    fun clearTransactionsForUser(userId: Int)
}
