package com.scheduler.dao;

import com.scheduler.model.Project;
import com.scheduler.model.ScheduledEntry;
import com.scheduler.util.DBConnection;
import com.scheduler.util.SchedulingCalendar;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for projects and scheduled entries.
 *
 * RESPONSIBILITIES:
 *   - All SQL for the projects and scheduled_weeks tables
 *   - Viability gate at insertion (reject if deadline < next Monday)
 *   - Status transitions: ACTIVE → SCHEDULED, ACTIVE → EXPIRED
 *
 * WHAT THIS CLASS DOES NOT DO:
 *   - No business logic (scheduling algorithm lives in SchedulerService)
 *   - No user interaction (CLI lives in CLIMenu)
 *   - No date rule decisions (date arithmetic lives in SchedulingCalendar)
 */
public class ProjectDAO {

    // ── INSERT ────────────────────────────────────────────────────────────────

    /**
     * Inserts a new project, but ONLY after passing the viability gate.
     *
     * WHY REJECT AT THE DAO LAYER?
     * "Fail fast" design — catch structurally impossible data at the earliest
     * point, before it pollutes the database. A project whose deadline falls
     * before next Monday can NEVER be scheduled. Storing it as ACTIVE would:
     *   1. Inflate the active project count (misleading)
     *   2. Get immediately expired on the next Saturday run (wasted DB write)
     *   3. Corrupt historical revenue statistics (it was never real revenue)
     *
     * WHY IllegalArgumentException (not SQLException)?
     * This is a business rule violation, not a database error.
     * The caller (SchedulerController) catches these separately and shows
     * a clear rejection message — not a generic "DB error" message.
     *
     * @throws IllegalArgumentException if deadline cannot make next week's schedule
     * @throws SQLException             if the database write fails
     */
    public int insertProject(Project p) throws SQLException {

        LocalDate today            = LocalDate.now();
        LocalDate planningDay      = SchedulingCalendar.getPlanningDay(today);
        LocalDate absoluteDeadline = p.getAbsoluteDeadline();

        // ── Viability gate ────────────────────────────────────────────────────
        if (!SchedulingCalendar.isViable(absoluteDeadline, planningDay)) {
            LocalDate nextMonday = SchedulingCalendar.getNextMonday(planningDay);
            throw new IllegalArgumentException(String.format(
                    "Project '%s' REJECTED: absolute deadline %s is before next Monday %s.%n" +
                            "  → Planning runs Saturday (%s); execution starts Monday (%s).%n" +
                            "  → No slot exists before this project's deadline.",
                    p.getTitle(), absoluteDeadline, nextMonday, planningDay, nextMonday
            ));
        }

        String sql = "INSERT INTO projects (title, revenue, deadline_days, arrival_date) " +
                "VALUES (?, ?, ?, ?) RETURNING project_id";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, p.getTitle());
            stmt.setDouble(2, p.getRevenue());
            stmt.setInt(3, p.getDeadlineDays());
            stmt.setDate(4, Date.valueOf(p.getArrivalDate()));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("project_id");
            }
        }

        throw new SQLException("Insert failed — database returned no ID.");
    }

    // ── FETCH ─────────────────────────────────────────────────────────────────

    /**
     * Fetches all projects with status = 'ACTIVE'.
     *
     * Note: These have already passed the viability gate at insertion time.
     * However, SchedulerService still re-checks viability on each Saturday run
     * because a project viable last Saturday may have aged out by this Saturday
     * (if it wasn't selected due to capacity limits last week).
     */
    public List<Project> fetchActiveProjects() throws SQLException {
        List<Project> projects = new ArrayList<>();

        String sql = "SELECT project_id, title, revenue, deadline_days, arrival_date, status " +
                "FROM projects WHERE status = 'ACTIVE' ORDER BY project_id";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                projects.add(mapRow(rs));
            }
        }
        return projects;
    }

    /**
     * Fetches projects by any status value.
     * Used by the CLI to display EXPIRED, SCHEDULED, or COMPLETED projects.
     *
     * @param status one of: 'ACTIVE', 'SCHEDULED', 'EXPIRED', 'COMPLETED'
     */
    public List<Project> fetchProjectsByStatus(String status) throws SQLException {
        List<Project> projects = new ArrayList<>();

        String sql = "SELECT project_id, title, revenue, deadline_days, arrival_date, status " +
                "FROM projects WHERE status = ? ORDER BY project_id";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    projects.add(mapRow(rs));
                }
            }
        }
        return projects;
    }

    /**
     * Fetches all historical revenues (from SCHEDULED and COMPLETED projects).
     * Used by SchedulerService to compute average and max revenue statistics.
     *
     * WHY EXCLUDE ACTIVE AND EXPIRED?
     * - ACTIVE projects haven't generated revenue yet — speculative
     * - EXPIRED projects never generated revenue — they're losses, not earnings
     * Statistics should reflect actual realized or committed revenue only.
     */
    public List<Double> fetchHistoricalRevenues() throws SQLException {
        List<Double> revenues = new ArrayList<>();

        String sql = "SELECT revenue FROM projects WHERE status IN ('SCHEDULED', 'COMPLETED')";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                revenues.add(rs.getDouble("revenue"));
            }
        }
        return revenues;
    }

    /**
     * Fetches the schedule for the current execution week.
     *
     * WHY FILTER BY week_start?
     * scheduled_weeks accumulates entries across all past Saturdays.
     * We only want THIS week's plan — identified by the Monday of the
     * execution week (which is always planningDay + 2).
     *
     * Results are ordered by scheduled_day so the caller gets Mon→Fri order.
     */
    public List<ScheduledEntry> fetchCurrentWeekSchedule() throws SQLException {
        List<ScheduledEntry> entries = new ArrayList<>();

        // Fetch the most recently created schedule week
        // This avoids any date computation mismatch between scheduling and viewing
        String sql = """
            SELECT sw.project_id, p.title, sw.scheduled_day,
                   sw.week_start, p.revenue
            FROM scheduled_weeks sw
            JOIN projects p ON sw.project_id = p.project_id
            WHERE sw.week_start = (
                SELECT MAX(week_start) FROM scheduled_weeks
            )
            ORDER BY sw.scheduled_day
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                entries.add(new ScheduledEntry(
                        rs.getInt("project_id"),
                        rs.getString("title"),
                        rs.getDate("scheduled_day").toLocalDate(),
                        rs.getDate("week_start").toLocalDate(),
                        rs.getDouble("revenue")
                ));
            }
        }
        return entries;
    }

    // ── STATUS UPDATES ────────────────────────────────────────────────────────

    /**
     * Marks a project as EXPIRED.
     *
     * WHY UPDATE STATUS instead of DELETE?
     * Deleting loses the audit trail. You want to know:
     *   - How many projects expired each week (business health metric)
     *   - Which clients had unrealistic deadlines (feedback loop)
     *   - Revenue lost to expiry (opportunity cost reporting)
     */
    public void markProjectExpired(int projectId) throws SQLException {
        updateStatus(projectId, "EXPIRED");
    }
    // Add this to ProjectDAO.java
    public boolean isWeekAlreadyScheduled(LocalDate weekStart) throws SQLException {
        String sql = "SELECT COUNT(*) FROM scheduled_weeks WHERE week_start = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(weekStart));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        }
        return false;
    }
    /**
     * Marks a project as SCHEDULED after a slot is assigned to it.
     * Prevents it from appearing in the next Saturday's active project list.
     */
    public void markProjectScheduled(int projectId) throws SQLException {
        updateStatus(projectId, "SCHEDULED");
    }

    private void updateStatus(int projectId, String status) throws SQLException {
        String sql = "UPDATE projects SET status = ? WHERE project_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setInt(2, projectId);
            stmt.executeUpdate();
        }
    }

    // ── SCHEDULE INSERT ───────────────────────────────────────────────────────

    /**
     * Inserts one scheduling decision into the scheduled_weeks table.
     * Called once per project after the scheduling algorithm assigns its slot.
     */
    public void insertScheduledEntry(ScheduledEntry entry) throws SQLException {
        String sql = "INSERT INTO scheduled_weeks (project_id, scheduled_day, week_start) " +
                "VALUES (?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, entry.getProjectId());
            stmt.setDate(2, Date.valueOf(entry.getScheduledDay()));
            stmt.setDate(3, Date.valueOf(entry.getWeekStart()));
            stmt.executeUpdate();
        }
    }

    // ── STATISTICS ────────────────────────────────────────────────────────────

    /**
     * Queries a full statistics breakdown and prints it to the terminal.
     * Called by CLIMenu option 6 (View Revenue Statistics).
     *
     * Uses a single SQL query with conditional aggregation (FILTER clause)
     * so the entire stats picture is painted in one round-trip to the DB.
     *
     * COALESCE(..., 0) prevents null results when no rows match a condition
     * (e.g. no scheduled projects yet → scheduled_revenue returns 0, not null).
     */
    public void printRevenueStatistics() throws SQLException {
        String sql = """
                SELECT
                    COUNT(*)                                          AS total,
                    COUNT(*) FILTER (WHERE status = 'ACTIVE')        AS active,
                    COUNT(*) FILTER (WHERE status = 'SCHEDULED')     AS scheduled,
                    COUNT(*) FILTER (WHERE status = 'EXPIRED')       AS expired,
                    COUNT(*) FILTER (WHERE status = 'COMPLETED')     AS completed,
                    COALESCE(AVG(revenue), 0)                        AS avg_revenue,
                    COALESCE(MAX(revenue), 0)                        AS max_revenue,
                    COALESCE(MIN(revenue), 0)                        AS min_revenue,
                    COALESCE(SUM(revenue)
                        FILTER (WHERE status = 'SCHEDULED'), 0)      AS scheduled_revenue,
                    COALESCE(SUM(revenue)
                        FILTER (WHERE status = 'EXPIRED'), 0)        AS lost_revenue
                FROM projects
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                System.out.println("  Project Counts:");
                System.out.printf("    Total      : %d%n", rs.getInt("total"));
                System.out.printf("    Active     : %d%n", rs.getInt("active"));
                System.out.printf("    Scheduled  : %d%n", rs.getInt("scheduled"));
                System.out.printf("    Completed  : %d%n", rs.getInt("completed"));
                System.out.printf("    Expired    : %d%n", rs.getInt("expired"));
                System.out.println();
                System.out.println("  Revenue Breakdown:");
                System.out.printf("    Average (all)       : $%,.2f%n", rs.getDouble("avg_revenue"));
                System.out.printf("    Maximum (all)       : $%,.2f%n", rs.getDouble("max_revenue"));
                System.out.printf("    Minimum (all)       : $%,.2f%n", rs.getDouble("min_revenue"));
                System.out.printf("    Committed (scheduled): $%,.2f%n", rs.getDouble("scheduled_revenue"));
                System.out.printf("    Lost to expiry      : $%,.2f%n", rs.getDouble("lost_revenue"));
            }
        }
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    /**
     * Maps one ResultSet row to a Project object.
     * Extracted to avoid duplicating this mapping in every fetch method.
     */
    private Project mapRow(ResultSet rs) throws SQLException {
        Project p = new Project();
        p.setProjectId(rs.getInt("project_id"));
        p.setTitle(rs.getString("title"));
        p.setRevenue(rs.getDouble("revenue"));
        p.setDeadlineDays(rs.getInt("deadline_days"));
        p.setArrivalDate(rs.getDate("arrival_date").toLocalDate());
        p.setStatus(rs.getString("status"));
        return p;
    }
}