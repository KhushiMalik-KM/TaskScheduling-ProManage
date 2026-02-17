package com.promanage.db;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
public class DBConnection {
    private static final String URL      = "jdbc:postgresql://localhost:5432/promanage_db";
    private static final String USERNAME = "postgres";   // ← change if yours is different
    private static final String PASSWORD = "khushi"; // ← change to your actual password

    // ── Singleton: one shared connection instance ──────────────────────────
    private static Connection connection = null;

    /**
     * Returns the database connection.
     * If no connection exists yet, it creates one.
     * If one already exists and is open, it reuses it.
     */
    public static Connection getConnection() {
        try {
            // If no connection exists OR it was closed, create a new one
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
                System.out.println("✅ Database connected successfully!");
            }
        } catch (SQLException e) {
            System.out.println("❌ Database connection failed!");
            System.out.println("   Reason: " + e.getMessage());
            System.out.println("   👉 Check: Is PostgreSQL running? Is your password correct?");
        }
        return connection;
    }

    /**
     * Closes the database connection.
     * Call this when the app is shutting down.
     */
    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("🔌 Database connection closed.");
            }
        } catch (SQLException e) {
            System.out.println("⚠️ Error closing connection: " + e.getMessage());
        }
    }
}

