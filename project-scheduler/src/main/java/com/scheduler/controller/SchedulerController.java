package com.scheduler.controller;

import com.scheduler.dao.ProjectDAO;
import com.scheduler.model.Project;
import com.scheduler.model.ScheduledEntry;
import com.scheduler.service.SchedulerService;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * The controller is the "front door" of the system.
 *
 * WHAT IT DOES:
 *   - Receives requests from the CLI layer (CLIMenu)
 *   - Delegates work to the Service layer or DAO layer
 *   - Catches and handles exceptions so the CLI never sees raw stack traces
 *   - Displays the final output (schedule summary)
 *
 * WHAT IT DOES NOT DO:
 *   - No business logic (that's SchedulerService)
 *   - No SQL (that's ProjectDAO)
 *   - No user input reading (that's CLIMenu)
 *
 * EXCEPTION HANDLING STRATEGY:
 *   Two distinct exception types flow up from the DAO:
 *
 *   1. IllegalArgumentException — a business rule violation at insertion
 *      (e.g. deadline before next Monday). This is expected and normal.
 *      Message is shown to the user as a clear rejection reason.
 *
 *   2. SQLException — a database infrastructure error.
 *      Unexpected. Shown as a technical error with the raw message.
 *
 *   Separating these gives the user a useful message in case 1
 *   ("your project was rejected because...") rather than a confusing
 *   database error message.
 */
public class SchedulerController {

    private final SchedulerService service;
    private final ProjectDAO       projectDAO;

    public SchedulerController() {
        this.service    = new SchedulerService();
        this.projectDAO = new ProjectDAO();
    }

    // ── ADD PROJECT ───────────────────────────────────────────────────────────

    /**
     * Builds a Project object and asks the DAO to insert it.
     *
     * The DAO performs the viability gate — if the deadline cannot make
     * next Saturday's schedule, it throws IllegalArgumentException.
     * We catch that here and display a human-readable rejection message.
     */
    public void addProject(String title, double revenue,
                           int deadlineDays, LocalDate arrivalDate) {

        Project p = new Project();
        p.setTitle(title);
        p.setRevenue(revenue);
        p.setDeadlineDays(deadlineDays);
        p.setArrivalDate(arrivalDate);

        try {
            int id = projectDAO.insertProject(p);
            System.out.printf("%n  [ACCEPTED] '%s' saved successfully (ID = %d)%n",
                    title, id);
            System.out.printf("             Absolute deadline: %s%n",
                    p.getAbsoluteDeadline());

        } catch (IllegalArgumentException e) {
            // Business rule violation — show the rejection reason clearly
            System.out.println();
            System.out.println("  [REJECTED] " + e.getMessage());

        } catch (SQLException e) {
            // Database infrastructure error — show technical details
            System.err.printf("%n  [DB ERROR] Failed to save project '%s': %s%n",
                    title, e.getMessage());
        }
    }

    // ── RUN SCHEDULER ─────────────────────────────────────────────────────────

    /**
     * Triggers the full scheduling pipeline and displays the resulting plan.
     *
     * @return the scheduled entries (useful for testing); empty list on failure
     */
    public List<ScheduledEntry> scheduleWeek() {
        try {
            List<ScheduledEntry> result = service.runSchedulingPipeline();
            displaySchedule(result);
            return result;

        } catch (SQLException e) {
            System.err.println("\n  [DB ERROR] Scheduling failed: " + e.getMessage());
            return List.of();
        }
    }

    // ── DISPLAY SCHEDULE ──────────────────────────────────────────────────────

    /**
     * Renders the final schedule as a formatted table in the terminal.
     * Shows each project's assigned day and revenue, plus total revenue.
     */
    private void displaySchedule(List<ScheduledEntry> schedule) {
        System.out.println();
        System.out.println("  ╔═══════════════════════════════════════════════════════════╗");
        System.out.println("  ║              NEXT WEEK'S EXECUTION SCHEDULE               ║");
        System.out.println("  ╠═══════════════════════════════════════════════════════════╣");

        if (schedule.isEmpty()) {
            System.out.println("  ║  No projects could be scheduled for next week.           ║");
            System.out.println("  ║  Check that active projects have viable deadlines.        ║");
        } else {
            System.out.printf("  ║  %-12s  %-10s  %-24s  %10s  ║%n",
                    "Date", "Day", "Project", "Revenue");
            System.out.println("  ╠═══════════════════════════════════════════════════════════╣");

            double total = 0;
            for (ScheduledEntry e : schedule) {
                System.out.printf("  ║  %-12s  %-10s  %-24s  %10.2f  ║%n",
                        e.getScheduledDay(),
                        e.getScheduledDay().getDayOfWeek(),
                        truncate(e.getProjectTitle(), 24),
                        e.getRevenue()
                );
                total += e.getRevenue();
            }

            System.out.println("  ╠═══════════════════════════════════════════════════════════╣");
            System.out.printf( "  ║  %-49s  %10.2f  ║%n", "TOTAL REVENUE", total);
        }

        System.out.println("  ╚═══════════════════════════════════════════════════════════╝");
    }

    // ── HELPER ────────────────────────────────────────────────────────────────

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
