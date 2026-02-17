package com.promanage.model;

/**
 * Project.java — The "blueprint" for a project.
 * Think of this like a template that defines what every project looks like.
 * Every project in our system will have these 4 fields.
 */
public class Project {

    // ── Fields (the data stored inside each project) ──────────────────────
    private String projectId;   // e.g. "PRJ001" — auto-generated, unique
    private String title;       // e.g. "UI Design for Client X"
    private int deadline;       // e.g. 3 → must be done within first 3 working days
    private double revenue;     // e.g. 50000.0 → money earned if completed on time

    // ── Constructor (used to CREATE a new Project object) ─────────────────
    public Project(String projectId, String title, int deadline, double revenue) {
        this.projectId = projectId;
        this.title = title;
        this.deadline = deadline;
        this.revenue = revenue;
    }

    // ── Getters (used to READ each field) ─────────────────────────────────
    public String getProjectId() { return projectId; }
    public String getTitle()     { return title; }
    public int    getDeadline()  { return deadline; }
    public double getRevenue()   { return revenue; }

    // ── Setters (used to UPDATE each field) ───────────────────────────────
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public void setTitle(String title)         { this.title = title; }
    public void setDeadline(int deadline)      { this.deadline = deadline; }
    public void setRevenue(double revenue)     { this.revenue = revenue; }

    // ── toString (used to DISPLAY a project nicely) ───────────────────────
    @Override
    public String toString() {
        return String.format("| %-8s | %-35s | Day %-3d | Rs. %,.2f |",
                projectId, title, deadline, revenue);
    }
}