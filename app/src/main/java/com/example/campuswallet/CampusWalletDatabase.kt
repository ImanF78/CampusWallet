
package com.example.campuswallet

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Main Room Database configuration for CampusWallet application.
 */
@Database(
    entities = [Transaction::class, User::class],
    version = 3,
    exportSchema = false
)
abstract class CampusWalletDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun userDao(): UserDao

    companion object {
        const val DATABASE_NAME = "campus_wallet_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        email TEXT NOT NULL,
                        passwordHash TEXT NOT NULL,
                        passwordSalt TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_users_email ON users(email)"
                )

                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN userId INTEGER NOT NULL DEFAULT 0"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_userId ON transactions(userId)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN timestamp INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
