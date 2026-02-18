package com.promanage.service;

import com.promanage.dao.ProjectDAO;
import com.promanage.model.Project;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

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

    private static final int DAYS_PER_WEEK = 5;
    private static final String[] DAY_NAMES = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};

    private final ProjectDAO projectDAO;

    public SchedulerService() {
        this.projectDAO = new ProjectDAO();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC: Main entry point
    // ─────────────────────────────────────────────────────────────────────────
    public void generateSchedule() {
        List<Project> allProjects = projectDAO.getAllProjects();

        if (allProjects.isEmpty()) {
            System.out.println("\n   No projects found in the database.");
            System.out.println("      Please add some projects first (Option 1).\n");
            return;
        }

        // ── STEP 1: Filter projects that are still eligible (deadline ≥ 1) ──
        List<Project> eligibleProjects = new ArrayList<>();
        for (Project p : allProjects) {
            if (p.getDeadline() >= 1) {
                eligibleProjects.add(p);
            }
        }

        if (eligibleProjects.isEmpty()) {
            System.out.println("\n    No eligible projects (all deadlines expired).\n");
            return;
        }

        // ── STEP 2: Sort by revenue — HIGHEST first ───────────────────────
        Project[] projects = eligibleProjects.toArray(new Project[0]);
        Arrays.sort(projects, (a, b) -> Double.compare(b.getRevenue(), a.getRevenue()));

        // ── STEP 3: Create the weekly schedule (5 slots) ──────────────────
        Project[] slots = new Project[DAYS_PER_WEEK];

        // ── STEP 4: Greedily assign projects to slots ─────────────────────
        List<Project> missedProjects = new ArrayList<>();

        for (Project project : projects) {
            // Determine the latest day this project can be scheduled THIS week
            // If deadline = 2, latest = day 2 (index 1)
            // If deadline = 8 (or any > 5), latest = day 5 (index 4)
            int latestDay = Math.min(project.getDeadline(), DAYS_PER_WEEK) - 1;

            // Try from latest day backwards to day 0
            boolean scheduled = false;
            for (int day = latestDay; day >= 0; day--) {
                if (slots[day] == null) {        // This slot is free!
                    slots[day] = project;
                    scheduled = true;
                    break;
                }
            }

            if (!scheduled) {
                missedProjects.add(project);
            }
        }

        // ── STEP 5: Print the results ─────────────────────────────────────
        printSchedule(slots, missedProjects);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE: Pretty-print the schedule
    // ─────────────────────────────────────────────────────────────────────────
    private void printSchedule(Project[] slots, List<Project> missedProjects) {

        System.out.println("\n");
        System.out.println("  ╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("  ║              📅  OPTIMAL WEEKLY SCHEDULE                            ║");
        System.out.println("  ╠══════════╦═══════════════════════════════════════╦══════════════════╣");
        System.out.println("  ║   Day    ║  Project                              ║  Revenue (Rs.)   ║");
        System.out.println("  ╠══════════╬═══════════════════════════════════════╬══════════════════╣");

        double totalRevenue = 0;

        for (int i = 0; i < DAYS_PER_WEEK; i++) {
            if (slots[i] != null) {
                Project p = slots[i];
                totalRevenue += p.getRevenue();
                System.out.printf("  ║ %-8s ║  %-37s ║  %,14.2f  ║%n",
                        DAY_NAMES[i], p.getTitle(), p.getRevenue());
            } else {
                System.out.printf("  ║ %-8s ║  %-37s ║  %14s  ║%n",
                        DAY_NAMES[i], "-- No project scheduled --", "---");
            }
        }

        System.out.println("  ╠══════════╩═══════════════════════════════════════╬══════════════════╣");
        System.out.printf ("  ║                                   TOTAL REVENUE  ║  %,14.2f  ║%n", totalRevenue);
        System.out.println("  ╚════════════════════════════════════════════════════════════════════╝");

        // ── Show projects not scheduled this week ─────────────────────────
        if (!missedProjects.isEmpty()) {
            System.out.println("\n    Projects NOT scheduled this week (can be done later or skipped):");
            System.out.println("  ─────────────────────────────────────────────────────────────────");
            System.out.printf("  %-10s  %-35s  %-12s  %s%n", "ID", "Title", "Deadline", "Revenue (Rs.)");
            System.out.println("  ─────────────────────────────────────────────────────────────────");
            double missedRevenue = 0;
            for (Project p : missedProjects) {
                System.out.printf("  %-10s  %-35s  %-12d  %,.2f%n",
                        p.getProjectId(), p.getTitle(), p.getDeadline(), p.getRevenue());
                missedRevenue += p.getRevenue();
            }
            System.out.printf("%n    Total unscheduled revenue: Rs. %,.2f%n", missedRevenue);
            System.out.println("      (These can potentially be scheduled in future weeks)");
        } else {
            System.out.println("\n   All available projects have been scheduled this week!");
        }

        System.out.println();
    }
}
