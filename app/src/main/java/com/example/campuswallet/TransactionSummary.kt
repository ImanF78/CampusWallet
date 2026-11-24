package com.example.campuswallet

data class TransactionSummary(
    val totalIncome: Double,
    val totalExpenses: Double,
    val balance: Double,
    val transactionCount: Int
)

object TransactionSummaryHelper {

    fun calculateSummary(transactions: List<Transaction>): TransactionSummary {

        val income = transactions
            .filter { it.type.equals("income", ignoreCase = true) }
            .sumOf { it.amount }

        val expenses = transactions
            .filter { it.type.equals("expense", ignoreCase = true) }
            .sumOf { it.amount }

        val balance = income - expenses

        return TransactionSummary(
            totalIncome = income,
            totalExpenses = expenses,
            balance = balance,
            transactionCount = transactions.size
        )
    }

    fun formatAmount(amount: Double): String {
        return "KSh %.2f".format(amount)
    }

    fun getTransactionCountMessage(count: Int): String {
        return when (count) {
            0 -> "No transactions recorded yet"
            1 -> "1 transaction recorded"
            else -> "$count transactions recorded"
        }
    }
}