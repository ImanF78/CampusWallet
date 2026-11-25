package com.example.campuswallet

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import java.util.concurrent.Executors

/**
 * Main entry point of the Campus Wallet app.
 * Handles authentication (login/register), dashboard, budget tracking,
 * and adding income/expense transactions.
 */
class MainActivity : AppCompatActivity() {

    // Room database instance
    private lateinit var database: CampusWalletDatabase

    // Background executor for database operations (keeps UI thread free)
    private val databaseExecutor = Executors.newSingleThreadExecutor()

    // Currently logged-in user info
    private var currentUserId = -1
    private var currentUserName = ""

    // Selected transaction type when adding a new entry ("Income" or "Expense")
    private var selectedType = "Expense"

    // SharedPreferences for keeping the user logged in across app restarts
    private val sessionPrefs by lazy {
        getSharedPreferences("campus_wallet_session", MODE_PRIVATE)
    }

    // SharedPreferences for storing each user's monthly budget
    private val budgetPrefs by lazy {
        getSharedPreferences("campus_wallet_budgets", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Room database with migration support
        database = Room.databaseBuilder(
            applicationContext,
            CampusWalletDatabase::class.java,
            "campus_wallet_database"
        )
            .addMigrations(CampusWalletDatabase.MIGRATION_1_2)
            .build()

        // Restore previous session if the user was already logged in
        currentUserId = sessionPrefs.getInt("user_id", -1)
        currentUserName = sessionPrefs.getString("user_name", "") ?: ""

        // Navigate to the correct starting screen
        if (currentUserId != -1) {
            showDashboard()
        } else {
            showWelcome()
        }
    }

    /** Saves the logged-in user details to SharedPreferences */
    private fun saveSession(user: User) {
        currentUserId = user.id
        currentUserName = user.name

        sessionPrefs.edit()
            .putInt("user_id", user.id)
            .putString("user_name", user.name)
            .apply()
    }

    /** Shows the welcome / onboarding screen */
    private fun showWelcome() {
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.getStartedButton).setOnClickListener {
            showRegister()
        }

        findViewById<TextView>(R.id.loginText).setOnClickListener {
            showLogin()
        }
    }

    /** Shows the registration screen and handles new account creation */
    private fun showRegister() {
        setContentView(R.layout.activity_register)

        findViewById<Button>(R.id.registerButton).setOnClickListener {

            val name = findViewById<EditText>(R.id.studentName)
                .text.toString().trim()

            val email = findViewById<EditText>(R.id.studentEmail)
                .text.toString().trim().lowercase()

            val password = findViewById<EditText>(R.id.studentPassword)
                .text.toString()

            // Basic validation
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

            // Hash password with a unique salt
            val salt = PasswordHelper.createSalt()
            val hashedPassword = PasswordHelper.hashPassword(password, salt)

            val user = User(
                name = name,
                email = email,
                passwordHash = hashedPassword,
                passwordSalt = salt
            )

            // Insert user on a background thread
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

    /** Shows the login screen and authenticates the user */
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

            // Verify credentials on a background thread
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

    /** Shows the main dashboard with balance, budget, and transaction history */
    private fun showDashboard() {
        setContentView(R.layout.activity_dashboard)

        // Personalized greeting
        findViewById<TextView>(R.id.welcomeStudent).text =
            "Hello, $currentUserName!"

        val budgetInput = findViewById<EditText>(R.id.budgetInput)
        val savedBudget = budgetPrefs.getFloat("budget_$currentUserId", 0f).toDouble()

        // Pre-fill previously saved budget if it exists
        if (savedBudget > 0) {
            budgetInput.setText(savedBudget.toString())
        }

        // Save monthly budget
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

        // Navigate to add transaction screen
        findViewById<Button>(R.id.addTransactionButton).setOnClickListener {
            showTransaction()
        }

        // Log out and clear session
        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            sessionPrefs.edit().clear().apply()
            currentUserId = -1
            currentUserName = ""
            showWelcome()
            Toast.makeText(this, "You have logged out", Toast.LENGTH_SHORT).show()
        }

        // Load latest financial data
        loadTransactions()
    }

    /**
     * Loads all transactions for the current user,
     * calculates totals, updates the UI, and shows budget progress.
     */
    private fun loadTransactions() {
        val userId = currentUserId
        val monthlyBudget = budgetPrefs.getFloat("budget_$userId", 0f).toDouble()

        databaseExecutor.execute {
            val transactions = database.transactionDao()
                .getTransactionsForUser(userId)

            val totalIncome = database.transactionDao().getTotalIncome(userId)
            val totalExpenses = database.transactionDao().getTotalExpenses(userId)

            val balance = totalIncome - totalExpenses

            // Build transaction history text
            val history = if (transactions.isEmpty()) {
                "No transactions yet. Add your first income or expense to get started."
            } else {
                transactions.joinToString("\n\n") {
                    "${it.type}: KSh ${"%.2f".format(it.amount)}\nCategory: ${it.category}"
                }
            }

            runOnUiThread {
                // Safety check in case the user logged out while data was loading
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

                // Update budget progress bar and warning message
                if (monthlyBudget > 0) {
                    val percentage = ((totalExpenses / monthlyBudget) * 100).toInt()
                    budgetProgress.progress = percentage.coerceIn(0, 100)

                    val remaining = monthlyBudget - totalExpenses
                    if (remaining < 0) {
                        budgetInfo.text = "⚠️ Warning: You have exceeded your monthly budget by KSh ${"%.2f".format(-remaining)}!"
                        budgetInfo.setTextColor(android.graphics.Color.RED)
                    } else {
                        budgetInfo.text = "You've used ${percentage}% of your monthly budget.\nKSh ${"%.2f".format(remaining)} remaining in your budget."
                        budgetInfo.setTextColor(android.graphics.Color.parseColor("#526174"))
                    }
                } else {
                    budgetProgress.progress = 0
                    budgetInfo.text = "Set a monthly budget to track your spending progress."
                    budgetInfo.setTextColor(android.graphics.Color.parseColor("#526174"))
                }
            }
        }
    }

    /** Shows the screen for adding a new income or expense transaction */
    private fun showTransaction() {
        setContentView(R.layout.activity_transaction)

        selectedType = "Expense"

        val incomeButton = findViewById<Button>(R.id.incomeOption)
        val expenseButton = findViewById<Button>(R.id.expenseOption)

        // Default selection is Expense
        incomeButton.alpha = 0.6f
        expenseButton.alpha = 1f

        // Toggle between Income and Expense
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

        // Save the new transaction
        findViewById<Button>(R.id.submitTransaction).setOnClickListener {

            val amount = findViewById<EditText>(R.id.transactionAmount)
                .text.toString().toDoubleOrNull()

            val category = findViewById<EditText>(R.id.transactionCategory)
                .text.toString().trim()

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

    /** Clean up the background executor when the activity is destroyed */
    override fun onDestroy() {
        super.onDestroy()
        databaseExecutor.shutdown()
    }
}