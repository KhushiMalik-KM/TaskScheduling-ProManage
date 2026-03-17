package com.scheduler.cli;

import com.scheduler.controller.SchedulerController;
import com.scheduler.dao.ProjectDAO;
import com.scheduler.model.Project;
import com.scheduler.model.ScheduledEntry;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

/**
 * Terminal-based interactive interface for the Project Scheduling System.
 *
 * LAYER POSITION: CLI → Controller → Service → DAO → Database
 *
 * RESPONSIBILITIES:
 *   - Display menus and prompt the user for input
 *   - Validate all user input before passing to the controller
 *   - Display results returned by the controller
 *
 * WHAT IT DOES NOT DO:
 *   - No business logic (that's SchedulerService)
 *   - No database access (that's ProjectDAO)
 *   - No scheduling decisions (that's SchedulerService)
 *
 * INPUT VALIDATION PHILOSOPHY:
 *   Every input method loops until valid data is entered.
 *   The user is never dropped out of a field because of a typo.
 *   Clear error messages tell them exactly what's wrong and what's expected.
 *
 * DATE FORMAT: dd-MM-yyyy
 *   Unambiguous for international users.
 *   01-03-2026 = March 1st. No confusion with 03-01-2026.
 */
public class CLIMenu {

    private final SchedulerController controller;
    private final ProjectDAO          projectDAO;
    private final Scanner             scanner;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public CLIMenu() {
        this.controller = new SchedulerController();
        this.projectDAO = new ProjectDAO();
        this.scanner    = new Scanner(System.in);
    }

    // ── MAIN LOOP ─────────────────────────────────────────────────────────────

    /**
     * Entry point — runs the menu loop until the user exits.
     * Each iteration: show menu → read choice → dispatch to handler → repeat.
     */
    public void start() {
        printBanner();

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Enter choice", 0, 6);

            switch (choice) {
                case 1 -> addProject();
                case 2 -> viewActiveProjects();
                case 3 -> runScheduler();
                case 4 -> viewCurrentSchedule();
                case 5 -> viewExpiredProjects();
                case 6 -> viewStatistics();
                case 0 -> {
                    running = false;
                    System.out.println("\n  Goodbye.\n");
                }
            }
        }

        scanner.close();
    }

    // ── MENU DISPLAY ──────────────────────────────────────────────────────────

    private void printBanner() {
        System.out.println();
        System.out.println("  ╔═══════════════════════════════════════════════════════╗");
        System.out.println("  ║          PROJECT SCHEDULING SYSTEM                    ║");
        System.out.println("  ║          Hybrid Greedy Predictive Strategy            ║");
        System.out.println("  ╚═══════════════════════════════════════════════════════╝");
        System.out.printf( "    Today      : %s (%s)%n", LocalDate.now(),
                LocalDate.now().getDayOfWeek());
        System.out.printf( "    Scheduling : Every Saturday → plans next Mon–Fri%n");
        System.out.println();
    }

    private void printMenu() {
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────┐");
        System.out.println("  │              MAIN MENU                  │");
        System.out.println("  ├─────────────────────────────────────────┤");
        System.out.println("  │  1.  Add New Project                    │");
        System.out.println("  │  2.  View All Active Projects           │");
        System.out.println("  │  3.  Run Saturday Scheduler             │");
        System.out.println("  │  4.  View This Week's Schedule          │");
        System.out.println("  │  5.  View Expired Projects              │");
        System.out.println("  │  6.  View Revenue Statistics            │");
        System.out.println("  │  0.  Exit                               │");
        System.out.println("  └─────────────────────────────────────────┘");
    }

    // ── OPTION 1: ADD PROJECT ─────────────────────────────────────────────────

    /**
     * Collects project details field by field with full input validation.
     * Shows a confirmation summary before saving to prevent accidental entries.
     *
     * If the project's deadline cannot make next week's schedule, the controller
     * will display a clear rejection message (no exception leaks to the user).
     */
    private void addProject() {
        printSectionHeader("ADD NEW PROJECT");

        // ── Field 1: Title ────────────────────────────────────────────────────
        String title = readNonEmptyString("Project Title");

        // ── Field 2: Revenue ──────────────────────────────────────────────────
        double revenue = readPositiveDouble("Revenue ($)");

        // ── Field 3: Deadline (days) ──────────────────────────────────────────
        System.out.println();
        System.out.println("  Deadline = number of days from the arrival date by which");
        System.out.println("             the project must be completed.");
        System.out.println("  Example : Arrival = today, Deadline = 5 → due in 5 days.");
        System.out.println();
        int deadlineDays = readPositiveInt("Deadline (days)");

        // ── Field 4: Arrival date ─────────────────────────────────────────────
        System.out.println();
        System.out.println("  Arrival date = the date this project request was received.");
        System.out.println("  Format       : dd-MM-yyyy  (e.g. 01-03-2026)");
        System.out.println("  Press ENTER  : uses today's date");
        System.out.println();
        LocalDate arrivalDate = readDateOrToday("Arrival Date");

        // ── Confirmation summary ───────────────────────────────────────────────
        LocalDate absoluteDeadline = arrivalDate.plusDays(deadlineDays);
        System.out.println();
        System.out.println("  ┌─── Review Before Saving ──────────────────────────────┐");
        System.out.printf( "  │  Title            : %-34s│%n", truncate(title, 34));
        System.out.printf( "  │  Revenue          : $%-33.2f│%n", revenue);
        System.out.printf( "  │  Arrival Date     : %-34s│%n", arrivalDate);
        System.out.printf( "  │  Deadline         : %d days from arrival%-16s│%n",
                deadlineDays, "");
        System.out.printf( "  │  Must complete by : %-34s│%n", absoluteDeadline);
        System.out.println("  └────────────────────────────────────────────────────────┘");
        System.out.println();

        String confirm = readNonEmptyString("Save this project? (y/n)").trim().toLowerCase();

        if (!confirm.equals("y")) {
            System.out.println("\n  Cancelled. Project was not saved.");
            return;
        }

        // ── Delegate to controller ────────────────────────────────────────────
        controller.addProject(title, revenue, deadlineDays, arrivalDate);
    }

    // ── OPTION 2: VIEW ACTIVE PROJECTS ───────────────────────────────────────

    /**
     * Displays all ACTIVE projects as a formatted table.
     * Shows both the relative deadline (days) and the computed absolute due date,
     * so the user can see both what was entered and when it actually falls.
     */
    private void viewActiveProjects() {
        printSectionHeader("ACTIVE PROJECTS");

        try {
            List<Project> projects = projectDAO.fetchActiveProjects();

            if (projects.isEmpty()) {
                System.out.println("  No active projects found.");
                System.out.println("  Use option 1 to add projects.");
                return;
            }

            // Table header
            System.out.printf("  %-4s  %-26s  %12s  %10s  %12s  %12s%n",
                    "ID", "Title", "Revenue ($)", "Deadline", "Arrived", "Due Date");
            System.out.println("  " + "─".repeat(84));

            for (Project p : projects) {
                System.out.printf("  %-4d  %-26s  %12.2f  %7d days  %12s  %12s%n",
                        p.getProjectId(),
                        truncate(p.getTitle(), 26),
                        p.getRevenue(),
                        p.getDeadlineDays(),
                        p.getArrivalDate(),
                        p.getAbsoluteDeadline()
                );
            }

            System.out.println("  " + "─".repeat(84));
            System.out.printf("  Total: %d active project(s)%n", projects.size());

        } catch (SQLException e) {
            printError("Failed to fetch active projects: " + e.getMessage());
        }
    }

    // ── OPTION 3: RUN SATURDAY SCHEDULER ─────────────────────────────────────

    /**
     * Triggers the full scheduling pipeline.
     *
     * If run on a non-Saturday: warns the user and asks for confirmation.
     * This is important because:
     *   - The planning day (Saturday) determines which Saturday is used as anchor
     *   - Running on a Wednesday means "next Monday" is different from running on Saturday
     *   - The user should understand they're getting a non-standard run
     *
     * We don't BLOCK non-Saturday runs — useful for testing and demo purposes.
     */
    private void runScheduler() {
        printSectionHeader("RUN SATURDAY SCHEDULER");

        LocalDate today = LocalDate.now();
        boolean isSaturday = today.getDayOfWeek().getValue() == 6;

        if (!isSaturday) {
            System.out.printf("  ⚠  Warning: Today is %s (%s), not Saturday.%n",
                    today, today.getDayOfWeek());
            System.out.println();
            System.out.println("  The scheduler is designed to run on Saturdays.");
            System.out.println("  Running it now will compute 'next Saturday' from today,");
            System.out.println("  and plan for the Mon–Fri following that Saturday.");
            System.out.println("  This is fine for testing but may not reflect real schedule intent.");
            System.out.println();
            String proceed = readNonEmptyString("  Proceed anyway? (y/n)").trim().toLowerCase();

            if (!proceed.equals("y")) {
                System.out.println("\n  Scheduling cancelled.");
                return;
            }
        }

        System.out.println();
        controller.scheduleWeek();
    }

    // ── OPTION 4: VIEW THIS WEEK'S SCHEDULE ──────────────────────────────────

    /**
     * Displays the execution schedule for the current week.
     *
     * "Current week" = the Mon–Fri that follows the most recent (or upcoming) Saturday.
     * Uses SchedulingCalendar (via ProjectDAO) to compute the correct week_start.
     */
    private void viewCurrentSchedule() {
        printSectionHeader("THIS WEEK'S EXECUTION SCHEDULE");

        try {
            List<ScheduledEntry> entries = projectDAO.fetchCurrentWeekSchedule();

            if (entries.isEmpty()) {
                System.out.println("  No schedule found for this week.");
                System.out.println("  Run option 3 (Saturday Scheduler) to generate the plan.");
                return;
            }

            System.out.printf("  %-12s  %-10s  %-26s  %12s%n",
                    "Date", "Day", "Project", "Revenue ($)");
            System.out.println("  " + "─".repeat(68));

            double total = 0;
            for (ScheduledEntry e : entries) {
                System.out.printf("  %-12s  %-10s  %-26s  %12.2f%n",
                        e.getScheduledDay(),
                        e.getScheduledDay().getDayOfWeek(),
                        truncate(e.getProjectTitle(), 26),
                        e.getRevenue()
                );
                total += e.getRevenue();
            }

            System.out.println("  " + "─".repeat(68));
            System.out.printf("  %-50s  %12.2f%n", "TOTAL EXPECTED REVENUE", total);

        } catch (SQLException e) {
            printError("Failed to fetch schedule: " + e.getMessage());
        }
    }

    // ── OPTION 5: VIEW EXPIRED PROJECTS ──────────────────────────────────────

    /**
     * Displays all projects that have been marked EXPIRED.
     * Useful for understanding how many projects were lost due to tight
     * deadlines or capacity limits, and what revenue was forfeited.
     */
    private void viewExpiredProjects() {
        printSectionHeader("EXPIRED PROJECTS");

        try {
            List<Project> expired = projectDAO.fetchProjectsByStatus("EXPIRED");

            if (expired.isEmpty()) {
                System.out.println("  No expired projects. Good news!");
                return;
            }

            System.out.printf("  %-4s  %-26s  %12s  %12s%n",
                    "ID", "Title", "Revenue ($)", "Was Due");
            System.out.println("  " + "─".repeat(60));

            double totalLost = 0;
            for (Project p : expired) {
                System.out.printf("  %-4d  %-26s  %12.2f  %12s%n",
                        p.getProjectId(),
                        truncate(p.getTitle(), 26),
                        p.getRevenue(),
                        p.getAbsoluteDeadline()
                );
                totalLost += p.getRevenue();
            }

            System.out.println("  " + "─".repeat(60));
            System.out.printf("  %-4s  %-26s  %12.2f%n",
                    "", "TOTAL REVENUE LOST", totalLost);
            System.out.printf("%n  Total: %d expired project(s)%n", expired.size());

        } catch (SQLException e) {
            printError("Failed to fetch expired projects: " + e.getMessage());
        }
    }

    // ── OPTION 6: VIEW STATISTICS ─────────────────────────────────────────────

    /**
     * Displays revenue statistics — project counts by status plus
     * average, max, min, committed, and lost revenue figures.
     * All computed via a single SQL query in ProjectDAO.
     */
    private void viewStatistics() {
        printSectionHeader("REVENUE STATISTICS");

        try {
            projectDAO.printRevenueStatistics();
        } catch (SQLException e) {
            printError("Failed to fetch statistics: " + e.getMessage());
        }
    }

    // ── INPUT READING HELPERS ─────────────────────────────────────────────────

    /**
     * Reads an integer in [min, max]. Loops until the user enters a valid value.
     *
     * WHY A LOOP?
     * Scanner.nextInt() throws NumberFormatException on non-numeric input.
     * Users make typos. The loop ensures we never crash on bad input —
     * we just ask again with a clear message about what went wrong.
     */
    private int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.printf("  %s (%d–%d): ", prompt, min, max);
            String line = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(line);
                if (value >= min && value <= max) return value;
                System.out.printf("  Please enter a number between %d and %d.%n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("  That's not a valid number. Please try again.");
            }
        }
    }

    /**
     * Reads a positive integer (> 0). Used for deadline days.
     */
    private int readPositiveInt(String prompt) {
        while (true) {
            System.out.printf("  %s: ", prompt);
            String line = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(line);
                if (value > 0) return value;
                System.out.println("  Value must be at least 1. Please try again.");
            } catch (NumberFormatException e) {
                System.out.println("  Please enter a whole number (e.g. 5).");
            }
        }
    }

    /**
     * Reads a positive double. Used for revenue.
     * Accepts both integer input (15000) and decimal input (15000.50).
     */
    private double readPositiveDouble(String prompt) {
        while (true) {
            System.out.printf("  %s: ", prompt);
            String line = scanner.nextLine().trim();
            try {
                double value = Double.parseDouble(line);
                if (value > 0) return value;
                System.out.println("  Revenue must be greater than zero. Please try again.");
            } catch (NumberFormatException e) {
                System.out.println("  Please enter a number (e.g. 15000 or 15000.50).");
            }
        }
    }

    /**
     * Reads a non-empty string. Used for project title and yes/no prompts.
     */
    private String readNonEmptyString(String prompt) {
        while (true) {
            System.out.printf("  %s: ", prompt);
            String line = scanner.nextLine().trim();
            if (!line.isEmpty()) return line;
            System.out.println("  This field cannot be empty. Please enter a value.");
        }
    }

    /**
     * Reads a date in dd-MM-yyyy format, or returns today if the user presses Enter.
     *
     * WHY dd-MM-yyyy?
     *   "01-03-2026" is unambiguously March 1st.
     *   "03-01-2026" is unambiguously January 3rd.
     *   No ambiguity between day-first and month-first conventions.
     *
     * WHY ALLOW ENTER FOR TODAY?
     *   Most projects arrive the same day they're entered into the system.
     *   Forcing users to type today's date every time is unnecessary friction.
     */
    private LocalDate readDateOrToday(String prompt) {
        while (true) {
            System.out.printf("  %s [dd-MM-yyyy or ENTER for today]: ", prompt);
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                LocalDate today = LocalDate.now();
                System.out.printf("  Using today: %s%n", today);
                return today;
            }

            try {
                return LocalDate.parse(line, DATE_FORMAT);
            } catch (DateTimeParseException e) {
                System.out.println("  Invalid date format. Please use dd-MM-yyyy (e.g. 01-03-2026).");
            }
        }
    }

    // ── DISPLAY HELPERS ───────────────────────────────────────────────────────

    private void printSectionHeader(String title) {
        System.out.println();
        System.out.println("  ┌──────────────────────────────────────────────┐");
        System.out.printf( "  │  %-44s│%n", title);
        System.out.println("  └──────────────────────────────────────────────┘");
        System.out.println();
    }

    private void printError(String message) {
        System.out.println("\n  [ERROR] " + message);
    }

    /**
     * Truncates a string and appends "..." if it exceeds maxLen.
     * Used so table columns stay aligned regardless of title length.
     */
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}