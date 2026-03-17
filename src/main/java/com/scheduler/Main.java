package com.scheduler;

import com.scheduler.cli.CLIMenu;

/**
 * Application entry point.
 *
 * Main does exactly one thing: hand control to the CLI.
 * All logic, validation, and database access lives in the appropriate layers below.
 *
 * Layer order:
 *   Main → CLIMenu → SchedulerController → SchedulerService → ProjectDAO → PostgreSQL
 */
public class Main {

    public static void main(String[] args) {
        CLIMenu menu = new CLIMenu();
        menu.start();
    }
}