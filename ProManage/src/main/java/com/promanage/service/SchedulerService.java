package com.promanage.service;

import com.promanage.dao.ProjectDAO;
import com.promanage.model.Project;

import java.util.Arrays;
import java.util.List;

/**
 * STRATEGY:
 *   1. Sort all projects by revenue (highest first — we want the most money)
 *   2. For each project (starting from most profitable):
 *      - Try to place it in the LATEST available slot before its deadline
 *        (placing late keeps early slots open for tight-deadline projects)
 *      - If a slot is available → schedule it there
 *      - If all slots before deadline are taken → skip it
 *   3. Whatever fits = your optimal weekly schedule
 */
public class SchedulerService {

    private static final int MAX_DAYS = 5; // Monday to Friday

    private final ProjectDAO projectDAO;

    public SchedulerService() {
        this.projectDAO = new ProjectDAO();
    }

    /**
     * Main method: fetches all projects from DB and generates the optimal schedule.
     */
    public void generateSchedule() {
        List<Project> allProjects = projectDAO.getAllProjects();

        if (allProjects.isEmpty()) {
            System.out.println("\n  ⚠ No projects found in the database.");
            System.out.println("      Please add some projects first (Option 1).\n");
            return;
        }

        // ── STEP 1: Convert list to array for sorting ──────────────────────
        Project[] projects = allProjects.toArray(new Project[0]);

        // ── STEP 2: Sort by revenue — HIGHEST first ────────────────────────
        // (b.getRevenue() - a.getRevenue()) → descending order
        Arrays.sort(projects, (a, b) -> Double.compare(b.getRevenue(), a.getRevenue()));

        // ── STEP 3: Create schedule slots ─────────────────────────────────
        // slots[0] = Monday, slots[1] = Tuesday, ..., slots[4] = Friday
        // null means the slot is still empty
        Project[] slots = new Project[MAX_DAYS];

        // Track which projects were skipped (missed)
        java.util.List<Project> missedProjects = new java.util.ArrayList<>();

        // ── STEP 4: Try to fit each project ───────────────────────────────
        for (Project project : projects) {

            // A project with deadline=3 can only go in slot 0, 1, or 2 (days 1,2,3)
            // We cap it at MAX_DAYS in case someone enters deadline > 5
            int latestSlot = Math.min(project.getDeadline(), MAX_DAYS) - 1;

            // Try from the latest slot backwards to the earliest
            boolean scheduled = false;
            for (int day = latestSlot; day >= 0; day--) {
                if (slots[day] == null) {       // This slot is free!
                    slots[day] = project;       // Place the project here
                    scheduled = true;
                    break;                      // Stop looking, move to next project
                }
            }

            if (!scheduled) {
                missedProjects.add(project);   // Couldn't fit this project
            }
        }

        // ── STEP 5: Display the results ────────────────────────────────────
        printSchedule(slots, missedProjects);
    }

    /**
     * Prints the final weekly schedule in a readable table format.
     */
    private void printSchedule(Project[] slots, java.util.List<Project> missedProjects) {
        String[] dayNames = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};

        System.out.println("\n");
        System.out.println("  ╔════════════════════════════════════════════════════════════════════--══╗");
        System.out.println("  ║                   OPTIMAL WEEKLY SCHEDULE                            ║");
        System.out.println("  ╠══════════╦═══════════════════════════════════════╦══════════════════╣");
        System.out.println("  ║   Day    ║  Project                              ║  Revenue (Rs.)   ║");
        System.out.println("  ╠══════════╬═══════════════════════════════════════╬══════════════════╣");

        double totalRevenue = 0;

        for (int i = 0; i < MAX_DAYS; i++) {
            if (slots[i] != null) {
                Project p = slots[i];
                totalRevenue += p.getRevenue();
                System.out.printf("  ║ %-8s ║  %-37s ║  %,14.2f  ║%n",
                        dayNames[i], p.getTitle(), p.getRevenue());
            } else {
                System.out.printf("  ║ %-8s ║  %-37s ║  %14s  ║%n",
                        dayNames[i], "-- No project scheduled --", "---");
            }
        }

        System.out.println("  ╠══════════╩═══════════════════════════════════════╬══════════════════╣");
        System.out.printf ("  ║                                         TOTAL    ║  %,14.2f  ║%n", totalRevenue);
        System.out.println("  ╚════════════════════════════════════════════════════════════════════╝");

        // ── Show missed projects ───────────────────────────────────────────
        if (!missedProjects.isEmpty()) {
            System.out.println("\n  ❌ Projects that could NOT be scheduled this week:");
            System.out.println("  ─────────────────────────────────────────────────────────────");
            System.out.printf("  %-10s  %-35s  %-10s  %s%n", "ID", "Title", "Deadline", "Revenue");
            System.out.println("  ─────────────────────────────────────────────────────────────");
            double missedRevenue = 0;
            for (Project p : missedProjects) {
                System.out.printf("  %-10s  %-35s  Day %-6d  Rs. %,.2f%n",
                        p.getProjectId(), p.getTitle(), p.getDeadline(), p.getRevenue());
                missedRevenue += p.getRevenue();
            }
            System.out.printf("%n  💸 Total missed revenue: Rs. %,.2f%n", missedRevenue);
        } else {
            System.out.println("\n  ✅ All available projects have been scheduled!");
        }

        System.out.println();
    }
}