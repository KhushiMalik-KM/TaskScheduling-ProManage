package com.scheduler.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Single source of truth for database connectivity.
 * Every layer that needs the DB goes through here — no one
 * hardcodes connection strings anywhere else.
 *
 * In production, replace with HikariCP connection pool.
 */
public class DBConnection {

    private static final String URL      = "jdbc:postgresql://localhost:5432/scheduler";
    private static final String USER     = "postgres";
    private static final String PASSWORD = "khushi";

    // Utility class — never instantiated
    private DBConnection() {}

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}