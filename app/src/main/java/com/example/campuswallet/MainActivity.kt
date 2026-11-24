
package com.example.campuswallet

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var database: CampusWalletDatabase
    private val databaseExecutor = Executors.newSingleThreadExecutor()

    private var currentUserId = -1
    private var currentUserName = ""
    private var selectedType = "Expense"

    private val sessionPrefs by lazy {
        getSharedPreferences("campus_wallet_session", MODE_PRIVATE)
    }

    private val budgetPrefs by lazy {
        getSharedPreferences("campus_wallet_budgets", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = Room.databaseBuilder(
            applicationContext,
            CampusWalletDatabase::class.java,
            CampusWalletDatabase.DATABASE_NAME
        )
            .addMigrations(
                CampusWalletDatabase.MIGRATION_1_2,
                CampusWalletDatabase.MIGRATION_2_3
            )
            .fallbackToDestructiveMigration(true)
            .build()

        currentUserId = sessionPrefs.getInt("user_id", -1)
        currentUserName = sessionPrefs.getString("user_name", "") ?: ""

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentUserId != -1) {
                    showDashboard()
                } else {
                    finish()
                }
            }
        })

        if (currentUserId != -1) {
            showDashboard()
        } else {
            showWelcome()
        }
    }

    private fun saveSession(user: User) {
        currentUserId = user.id
        currentUserName = user.name

        sessionPrefs.edit()
            .putInt("user_id", user.id)
            .putString("user_name", user.name)
            .apply()
    }

    private fun showWelcome() {
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.getStartedButton).setOnClickListener {
            showRegister()
        }

        findViewById<TextView>(R.id.loginText).setOnClickListener {
            showLogin()
        }
    }

    private fun showRegister() {
        setContentView(R.layout.activity_register)

        findViewById<Button>(R.id.registerButton).setOnClickListener {

            val name = findViewById<EditText>(R.id.studentName)
                .text.toString().trim()

            val email = findViewById<EditText>(R.id.studentEmail)
                .text.toString().trim().lowercase()

            val password = findViewById<EditText>(R.id.studentPassword)
                .text.toString()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please complete all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val salt = PasswordHelper.createSalt()
            val hashedPassword = PasswordHelper.hashPassword(password, salt)

            val user = User(
                name = name,
                email = email,
                passwordHash = hashedPassword,
                passwordSalt = salt
            )

            databaseExecutor.execute {
                val existingUser = database.userDao().getUserByEmail(email)

                if (existingUser != null) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "An account with this email already exists",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    val newId = database.userDao().insertUser(user).toInt()
                    val savedUser = user.copy(id = newId)

                    runOnUiThread {
                        saveSession(savedUser)
                        Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                        showDashboard()
                    }
                }
            }
        }

        findViewById<TextView>(R.id.goToLogin).setOnClickListener {
            showLogin()
        }
    }

    private fun showLogin() {
        setContentView(R.layout.activity_login)

        findViewById<Button>(R.id.loginButton).setOnClickListener {

            val email = findViewById<EditText>(R.id.loginEmail)
                .text.toString().trim().lowercase()

            val password = findViewById<EditText>(R.id.loginPassword)
                .text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter your email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            databaseExecutor.execute {
                val user = database.userDao().getUserByEmail(email)

                val valid = user != null && PasswordHelper.verifyPassword(
                    password,
                    user.passwordSalt,
                    user.passwordHash
                )

                runOnUiThread {
                    if (valid && user != null) {
                        saveSession(user)
                        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
                        showDashboard()
                    } else {
                        Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        findViewById<TextView>(R.id.backToRegister).setOnClickListener {
            showRegister()
        }
    }

    private fun showDashboard() {
        setContentView(R.layout.activity_dashboard)

        findViewById<TextView>(R.id.welcomeStudent).text =
            "Hello, $currentUserName!"

        val budgetInput = findViewById<EditText>(R.id.budgetInput)
        val savedBudget = budgetPrefs.getFloat("budget_$currentUserId", 0f).toDouble()

        if (savedBudget > 0) {
            budgetInput.setText(savedBudget.toString())
        }

        findViewById<Button>(R.id.saveBudgetButton).setOnClickListener {
            val value = budgetInput.text.toString().toDoubleOrNull()

            if (value == null || value <= 0) {
                Toast.makeText(this, "Enter a valid monthly budget", Toast.LENGTH_SHORT).show()
            } else {
                budgetPrefs.edit()
                    .putFloat("budget_$currentUserId", value.toFloat())
                    .apply()

                Toast.makeText(this, "Monthly budget saved!", Toast.LENGTH_SHORT).show()
                loadTransactions()
            }
        }

        findViewById<Button>(R.id.addTransactionButton).setOnClickListener {
            showTransaction()
        }

        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            sessionPrefs.edit().clear().apply()
            currentUserId = -1
            currentUserName = ""
            showWelcome()
            Toast.makeText(this, "You have logged out", Toast.LENGTH_SHORT).show()
        }

        loadTransactions()
    }

    private fun loadTransactions() {
        val userId = currentUserId
        val monthlyBudget = budgetPrefs.getFloat("budget_$userId", 0f).toDouble()

        databaseExecutor.execute {
            val transactions = database.transactionDao()
                .getTransactionsForUser(userId)

            val totalIncome = database.transactionDao().getTotalIncome(userId)
            val totalExpenses = database.transactionDao().getTotalExpenses(userId)

            val balance = totalIncome - totalExpenses

            val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())

            val history = if (transactions.isEmpty()) {
                "No transactions yet. Add your first income or expense to get started."
            } else {
                transactions.joinToString("\n\n") {
                    val dateStr = dateFormat.format(Date(it.timestamp))
                    "${it.type}: KSh ${"%.2f".format(it.amount)}\nCategory: ${it.category}\nDate: $dateStr"
                }
            }

            runOnUiThread {
                if (currentUserId != userId) return@runOnUiThread

                findViewById<TextView>(R.id.incomeText).text =
                    "Total Income\nKSh ${"%.2f".format(totalIncome)}"

                findViewById<TextView>(R.id.expenseText).text =
                    "Total Expenses\nKSh ${"%.2f".format(totalExpenses)}"

                findViewById<TextView>(R.id.balanceText).text =
                    "Available Balance\nKSh ${"%.2f".format(balance)}"

                findViewById<TextView>(R.id.historyText).text = history

                val budgetProgress = findViewById<ProgressBar>(R.id.budgetProgress)
                val budgetInfo = findViewById<TextView>(R.id.budgetInfo)

                if (monthlyBudget > 0) {
                    val percentage = ((totalExpenses / monthlyBudget) * 100).toInt()
                    budgetProgress.progress = percentage.coerceIn(0, 100)

                    val remaining = monthlyBudget - totalExpenses
                    if (remaining < 0) {
                        budgetInfo.text = "⚠️ Over budget by KSh ${"%.2f".format(-remaining)}!\n" +
                                "Total Spent: KSh ${"%.2f".format(totalExpenses)} / Budget: KSh ${"%.2f".format(monthlyBudget)}"
                    } else {
                        budgetInfo.text = "You've used ${percentage}% of your monthly budget.\n" +
                                "KSh ${"%.2f".format(remaining)} remaining in your budget."
                    }
                } else {
                    budgetProgress.progress = 0
                    budgetInfo.text = "Set a monthly budget to track your spending progress."
                }
            }
        }
    }

    private fun showTransaction() {
        setContentView(R.layout.activity_transaction)

        selectedType = "Expense"

        val incomeButton = findViewById<Button>(R.id.incomeOption)
        val expenseButton = findViewById<Button>(R.id.expenseOption)

        incomeButton.alpha = 0.6f
        expenseButton.alpha = 1f

        incomeButton.setOnClickListener {
            selectedType = "Income"
            incomeButton.alpha = 1f
            expenseButton.alpha = 0.6f
        }

        expenseButton.setOnClickListener {
            selectedType = "Expense"
            expenseButton.alpha = 1f
            incomeButton.alpha = 0.6f
        }

        findViewById<Button>(R.id.submitTransaction).setOnClickListener {

            val amount = findViewById<EditText>(R.id.transactionAmount)
                .text.toString().toDoubleOrNull()

            val rawCategory = findViewById<EditText>(R.id.transactionCategory)
                .text.toString().trim()

            val category = rawCategory.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }

            if (amount == null || amount <= 0 || category.isEmpty()) {
                Toast.makeText(this, "Enter a valid amount and category", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val transaction = Transaction(
                type = selectedType,
                amount = amount,
                category = category,
                userId = currentUserId
            )

            databaseExecutor.execute {
                database.transactionDao().insertTransaction(transaction)

                runOnUiThread {
                    Toast.makeText(this, "Transaction saved successfully!", Toast.LENGTH_SHORT).show()
                    showDashboard()
                }
            }
        }

        findViewById<TextView>(R.id.backToDashboard).setOnClickListener {
            showDashboard()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        databaseExecutor.shutdown()
    }
}
