package com.scheduler.dao;

import com.scheduler.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Handles writing computed revenue statistics to the revenue_statistics table.
 *
 * WHY A SEPARATE DAO FROM ProjectDAO?
 * Statistics have their own lifecycle — they are computed periodically
 * (once per Saturday run) and stored as historical snapshots.
 * Keeping them separate makes ProjectDAO focused on project CRUD only,
 * and makes StatisticsDAO easy to extend (e.g. add per-week breakdowns later).
 */
public class StatisticsDAO {

    /**
     * Persists a snapshot of revenue statistics for the current scheduling run.
     * Each Saturday run adds a new row — you get a full history of how
     * average and max revenue changed over time.
     */
    public void saveStatistics(double average, double max, int count) throws SQLException {
        String sql = "INSERT INTO revenue_statistics " +
                "(average_revenue, max_revenue, total_projects_analyzed) " +
                "VALUES (?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, average);
            stmt.setDouble(2, max);
            stmt.setInt(3, count);
            stmt.executeUpdate();
        }
    }
}