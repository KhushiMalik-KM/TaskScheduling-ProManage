package com.promanage.dao;

import com.promanage.db.DBConnection;
import com.promanage.model.Project;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ProjectDAO.java — Data Access Object (DAO)
 *
 * "DAO" sounds fancy but it just means: this class talks to the database.
 * All database operations (add, view, fetch) live here.
 * Other classes never write SQL directly — they just call methods from this class.
 */
public class ProjectDAO {

    // ── 1. Generate a new unique Project ID ───────────────────────────────
    /**
     * Looks at the database to find the highest existing ID number,
     * then creates the next one. e.g. if "PRJ003" exists, returns "PRJ004".
     * If no projects exist yet, starts at "PRJ001".
     */
    public String generateProjectId() {
        String sql = "SELECT project_id FROM projects ORDER BY project_id DESC LIMIT 1";
        try (Statement stmt = DBConnection.getConnection().createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {

            if (rs.next()) {
                String lastId = rs.getString("project_id"); // e.g. "PRJ007"
                int number    = Integer.parseInt(lastId.substring(3)); // → 7
                return String.format("PRJ%03d", number + 1);          // → "PRJ008"
            }
        } catch (SQLException e) {
            System.out.println("❌ Error generating ID: " + e.getMessage());
        }
        return "PRJ001"; // First project ever
    }

    // ── 2. Add a new project to the database ──────────────────────────────
    /**
     * Takes a Project object and saves it into the 'projects' table.
     * Returns true if saved successfully, false if something went wrong.
     */
    public boolean addProject(Project project) {
        String sql = "INSERT INTO projects (project_id, title, deadline, revenue) VALUES (?, ?, ?, ?)";

        // PreparedStatement: safe way to run SQL with variables
        // The ? marks are placeholders — we fill them in below
        try (PreparedStatement pstmt = DBConnection.getConnection().prepareStatement(sql)) {

            pstmt.setString(1, project.getProjectId()); // 1st ? = project ID
            pstmt.setString(2, project.getTitle());     // 2nd ? = title
            pstmt.setInt(3, project.getDeadline());     // 3rd ? = deadline
            pstmt.setDouble(4, project.getRevenue());   // 4th ? = revenue

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0; // true means it worked!

        } catch (SQLException e) {
            System.out.println("❌ Error adding project: " + e.getMessage());
            return false;
        }
    }

    // ── 3. Fetch ALL projects from the database ────────────────────────────
    /**
     * Reads every row in the 'projects' table and returns them as a List.
     * A List is like an array but can grow/shrink dynamically.
     */
    public List<Project> getAllProjects() {
        List<Project> projects = new ArrayList<>();
        String sql = "SELECT * FROM projects ORDER BY project_id";

        try (Statement stmt = DBConnection.getConnection().createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {

            // rs is like a cursor — rs.next() moves to the next row
            while (rs.next()) {
                Project p = new Project(
                        rs.getString("project_id"),
                        rs.getString("title"),
                        rs.getInt("deadline"),
                        rs.getDouble("revenue")
                );
                projects.add(p);
            }

        } catch (SQLException e) {
            System.out.println("❌ Error fetching projects: " + e.getMessage());
        }

        return projects;
    }

    // ── 4. Check if any projects exist ────────────────────────────────────
    /**
     * Returns true if there is at least one project in the database.
     * Used to show a friendly message if the list is empty.
     */
    public boolean hasProjects() {
        String sql = "SELECT COUNT(*) FROM projects";
        try (Statement stmt = DBConnection.getConnection().createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.out.println("❌ Error checking projects: " + e.getMessage());
        }
        return false;
    }
}
