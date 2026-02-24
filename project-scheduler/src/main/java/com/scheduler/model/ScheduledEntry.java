package com.scheduler.model;

import java.time.LocalDate;

/**
 * Represents one row in the scheduled_weeks table.
 * Pairs a project with its assigned calendar day for a given week.
 *
 * weekStart = the Monday of the execution week (used to query "this week's schedule").
 * scheduledDay = the specific day (Mon–Fri) the project is assigned to.
 */
public class ScheduledEntry {

    private int       projectId;
    private String    projectTitle;
    private LocalDate scheduledDay;  // specific assigned working day
    private LocalDate weekStart;     // Monday of the execution week (for grouping/querying)
    private double    revenue;

    public ScheduledEntry(int projectId, String projectTitle,
                          LocalDate scheduledDay, LocalDate weekStart, double revenue) {
        this.projectId    = projectId;
        this.projectTitle = projectTitle;
        this.scheduledDay = scheduledDay;
        this.weekStart    = weekStart;
        this.revenue      = revenue;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int       getProjectId()          { return projectId; }
    public String    getProjectTitle()       { return projectTitle; }
    public LocalDate getScheduledDay()       { return scheduledDay; }
    public LocalDate getWeekStart()          { return weekStart; }
    public double    getRevenue()            { return revenue; }

    // ── toString ──────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format(
                "ScheduledEntry[project='%s'(id=%d), day=%s (%s), revenue=%.2f]",
                projectTitle, projectId, scheduledDay, scheduledDay.getDayOfWeek(), revenue
        );
    }
}