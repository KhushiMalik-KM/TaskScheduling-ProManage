package com.scheduler.model;

import java.time.LocalDate;

/**
 * Represents one row in the projects table.
 *
 * DESIGN NOTES:
 * - deadlineDays is stored RELATIVE to arrivalDate (not as an absolute date).
 *   Absolute deadline is always computed as: arrivalDate + deadlineDays.
 *   This matches what the user inputs ("project must be done in 5 days from now").
 *
 * - slackDays and priorityScore are COMPUTED fields — they are never stored
 *   in the database. They are calculated fresh each scheduling run by
 *   SchedulerService and temporarily stored here for sorting.
 *
 * - slackDays is long (not int) because ChronoUnit.DAYS.between() returns long.
 *   Using int would require a cast and risk overflow on very large date ranges.
 */
public class Project {

    // ── Persisted fields (map directly to DB columns) ─────────────────────────
    private int       projectId;
    private String    title;
    private double    revenue;
    private int       deadlineDays;   // days from arrivalDate by which work must complete
    private LocalDate arrivalDate;
    private String    status;         // ACTIVE | SCHEDULED | EXPIRED | COMPLETED

    // ── Computed fields (set by SchedulerService, never stored in DB) ─────────
    private long   slackDays;         // days between next Monday and absolute deadline
    private double priorityScore;     // revenue / (slackDays + 1)

    // ── Constructors ──────────────────────────────────────────────────────────

    public Project() {}

    public Project(int projectId, String title, double revenue,
                   int deadlineDays, LocalDate arrivalDate, String status) {
        this.projectId    = projectId;
        this.title        = title;
        this.revenue      = revenue;
        this.deadlineDays = deadlineDays;
        this.arrivalDate  = arrivalDate;
        this.status       = status;
    }

    // ── Key derived property ──────────────────────────────────────────────────

    /**
     * Computes the absolute deadline from stored relative fields.
     * Example: arrived 2026-03-01, deadlineDays=5 → absolute deadline = 2026-03-06
     *
     * This is a pure derivation — no logic, no state change.
     * Called frequently (display, viability check, slot assignment).
     */
    public LocalDate getAbsoluteDeadline() {
        return arrivalDate.plusDays(deadlineDays);
    }

    // ── Getters and Setters ───────────────────────────────────────────────────

    public int getProjectId()                    { return projectId; }
    public void setProjectId(int projectId)      { this.projectId = projectId; }

    public String getTitle()                     { return title; }
    public void setTitle(String title)           { this.title = title; }

    public double getRevenue()                   { return revenue; }
    public void setRevenue(double revenue)       { this.revenue = revenue; }

    public int getDeadlineDays()                 { return deadlineDays; }
    public void setDeadlineDays(int deadlineDays){ this.deadlineDays = deadlineDays; }

    public LocalDate getArrivalDate()                    { return arrivalDate; }
    public void setArrivalDate(LocalDate arrivalDate)    { this.arrivalDate = arrivalDate; }

    public String getStatus()                    { return status; }
    public void setStatus(String status)         { this.status = status; }

    public long getSlackDays()                   { return slackDays; }
    public void setSlackDays(long slackDays)     { this.slackDays = slackDays; }

    public double getPriorityScore()                     { return priorityScore; }
    public void setPriorityScore(double priorityScore)   { this.priorityScore = priorityScore; }

    // ── toString ──────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format(
                "Project[id=%d, title='%s', revenue=%.2f, deadline=%s, slack=%d, score=%.2f, status=%s]",
                projectId, title, revenue, getAbsoluteDeadline(), slackDays, priorityScore, status
        );
    }
}