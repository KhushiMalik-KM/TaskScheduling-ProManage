package com.promanage;

import com.promanage.dao.ProjectDAO;
import com.promanage.db.DBConnection;
import com.promanage.model.Project;
import com.promanage.service.SchedulerService;

import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

/**
 * Main.java — The Entry Point of the Application
 *
 * This is where the program starts. It shows a menu and lets the user:
 *   1. Add a new project
 *   2. View all projects
 *   3. Generate the optimal schedule
 *   4. Exit
 *
 * Think of this as the "receptionist" of the app — it takes requests
 * and sends them to the right department (DAO or SchedulerService).
 */
public class Main {

    // Scanner reads input from the keyboard
    private static final Scanner scanner = new Scanner(System.in);
    private static final ProjectDAO projectDAO = new ProjectDAO();
    private static final SchedulerService scheduler = new SchedulerService();

    public static void main(String[] args) {

        printBanner();

        // Try to connect to the database when the app starts
        if (DBConnection.getConnection() == null) {
            System.out.println("\n  ❌ Could not connect to the database. Please check your credentials in DBConnection.java");
            return;
        }

        // ── Main Menu Loop ─────────────────────────────────────────────────
        // Keeps running until the user chooses to exit
        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("  Enter your choice: ");

            switch (choice) {
                case 1 -> addProject();
                case 2 -> viewAllProjects();
                case 3 -> scheduler.generateSchedule();
                case 4 -> {
                    System.out.println("\n  👋 Thank you for using ProManage! Goodbye.\n");
                    DBConnection.closeConnection();
                    running = false;
                }
                default -> System.out.println("\n  ⚠️  Invalid choice. Please enter 1, 2, 3, or 4.\n");
            }
        }

        scanner.close();
    }

    // ── Option 1: Add a new project ───────────────────────────────────────
    private static void addProject() {
        System.out.println("\n  ─────────────────────────────────────────");
        System.out.println("           ➕ ADD NEW PROJECT");
        System.out.println("  ─────────────────────────────────────────");

        // Read project title
        System.out.print("  Enter project title: ");
        scanner.nextLine(); // clear leftover newline from previous input
        String title = scanner.nextLine().trim();

        if (title.isEmpty()) {
            System.out.println("  ⚠️  Title cannot be empty.\n");
            return;
        }

        // Read deadline (must be 1–5)
        int deadline = 0;
        while (deadline < 1 || deadline > 5) {
            deadline = readInt("  Enter deadline (1 to 5 working days): ");
            if (deadline < 1 || deadline > 5) {
                System.out.println("  ⚠️  Deadline must be between 1 and 5.");
            }
        }

        // Read revenue (must be positive)
        double revenue = 0;
        while (revenue <= 0) {
            revenue = readDouble("  Enter expected revenue (Rs.): ");
            if (revenue <= 0) {
                System.out.println("  ⚠️  Revenue must be greater than 0.");
            }
        }

        // Auto-generate a unique ID
        String projectId = projectDAO.generateProjectId();

        // Create the Project object
        Project project = new Project(projectId, title, deadline, revenue);

        // Save to database
        if (projectDAO.addProject(project)) {
            System.out.println("\n  ✅ Project added successfully!");
            System.out.printf("     ID: %s | Title: %s | Deadline: Day %d | Revenue: Rs. %,.2f%n%n",
                    projectId, title, deadline, revenue);
        } else {
            System.out.println("\n  ❌ Failed to add project. Please try again.\n");
        }
    }

    // ── Option 2: View all projects ───────────────────────────────────────
    private static void viewAllProjects() {
        System.out.println("\n  ─────────────────────────────────────────────────────────────────────");
        System.out.println("                         📋 ALL PROJECTS");
        System.out.println("  ─────────────────────────────────────────────────────────────────────");

        List<Project> projects = projectDAO.getAllProjects();

        if (projects.isEmpty()) {
            System.out.println("  ⚠️  No projects found. Add some projects first!\n");
            return;
        }

        // Print table header
        System.out.printf("  %-10s  %-35s  %-10s  %s%n", "ID", "Title", "Deadline", "Revenue (Rs.)");
        System.out.println("  ─────────────────────────────────────────────────────────────────────");

        // Print each project as a row
        for (Project p : projects) {
            System.out.printf("  %-10s  %-35s  Day %-6d  %,.2f%n",
                    p.getProjectId(), p.getTitle(), p.getDeadline(), p.getRevenue());
        }

        System.out.println("  ─────────────────────────────────────────────────────────────────────");
        System.out.printf("  Total projects in database: %d%n%n", projects.size());
    }

    // ── Helper: safely read an integer from the user ──────────────────────
    private static int readInt(String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                int value = scanner.nextInt();
                return value;
            } catch (InputMismatchException e) {
                System.out.println("  ⚠️  Please enter a whole number.");
                scanner.nextLine(); // discard bad input
            }
        }
    }

    // ── Helper: safely read a decimal number from the user ────────────────
    private static double readDouble(String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                double value = scanner.nextDouble();
                return value;
            } catch (InputMismatchException e) {
                System.out.println("  ⚠️  Please enter a valid number (e.g. 50000.00).");
                scanner.nextLine(); // discard bad input
            }
        }
    }

    // ── UI: Print the welcome banner ──────────────────────────────────────
    private static void printBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════════════════════╗");
        System.out.println("  ║                                                          ║");
        System.out.println("  ║       🏢  ProManage Solutions Pvt. Ltd.                  ║");
        System.out.println("  ║          Project Scheduling System v1.0                  ║");
        System.out.println("  ║                                                          ║");
        System.out.println("  ╚══════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    // ── UI: Print the main menu ───────────────────────────────────────────
    private static void printMenu() {
        System.out.println("  ┌───────────────────────────────────┐");
        System.out.println("  │           MAIN MENU               │");
        System.out.println("  ├───────────────────────────────────┤");
        System.out.println("  │  1. Add New Project               │");
        System.out.println("  │  2. View All Projects             │");
        System.out.println("  │  3. Generate Optimal Schedule     │");
        System.out.println("  │  4. Exit                          │");
        System.out.println("  └───────────────────────────────────┘");
    }
}
