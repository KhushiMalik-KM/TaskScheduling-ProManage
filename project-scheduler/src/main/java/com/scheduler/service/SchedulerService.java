package com.scheduler.service;

import com.scheduler.dao.ProjectDAO;
import com.scheduler.dao.StatisticsDAO;
import com.scheduler.model.Project;
import com.scheduler.model.ScheduledEntry;
import com.scheduler.util.SchedulingCalendar;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * The scheduling brain of the system.
 *
 * THIS CLASS OWNS ALL BUSINESS LOGIC:
 *   - Which projects are viable for next week
 *   - How priority scores are calculated
 *   - How projects are sorted
 *   - Which slot each project gets assigned to
 *   - When to expire projects that can no longer be scheduled
 *
 * THIS CLASS DOES NOT:
 *   - Talk to the user (that's CLIMenu)
 *   - Write raw SQL (that's ProjectDAO / StatisticsDAO)
 *   - Make date arithmetic decisions (that's SchedulingCalendar)
 *
 * SCHEDULING SYSTEM RULES:
 *   - Scheduling runs every Saturday
 *   - Execution slots: next Monday through next Friday (max 5 projects)
 *   - One project per day, no exceptions
 *   - Projects are assigned the LATEST valid slot before their deadline
 *   - Priority Score = Revenue / (Slack + 1), where Slack is measured from Monday
 *   - Tie-break order: higher revenue → earlier arrival date
 */
public class SchedulerService {

    private static final int MAX_PROJECTS_PER_WEEK = 5;

    private final ProjectDAO    projectDAO;
    private final StatisticsDAO statisticsDAO;

    public SchedulerService() {
        this.projectDAO    = new ProjectDAO();
        this.statisticsDAO = new StatisticsDAO();
    }

    // ── MAIN PIPELINE ─────────────────────────────────────────────────────────

    /**
     * Runs the full 8-step scheduling pipeline.
     * Returns the final list of ScheduledEntry objects (the week's plan).
     *
     * ORCHESTRATION DESIGN:
     * This method calls private methods that each do ONE thing.
     * This makes individual steps independently testable and readable.
     * If a step changes (e.g. new scoring formula), only that private
     * method changes — the pipeline structure stays the same.
     */
    public List<ScheduledEntry> runSchedulingPipeline() throws SQLException {

        // ── Date anchors — everything derives from today (Saturday) ───────────
        LocalDate today      = LocalDate.now();
        LocalDate planningDay = SchedulingCalendar.getPlanningDay(today);
        LocalDate nextMonday  = SchedulingCalendar.getNextMonday(planningDay);
        LocalDate nextFriday  = SchedulingCalendar.getNextFriday(nextMonday);

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  HYBRID GREEDY PREDICTIVE SCHEDULER — PIPELINE START");
        System.out.printf( "  Planning Day   : %s (Saturday)%n", planningDay);
        System.out.printf( "  Execution Week : %s (Mon) → %s (Fri)%n", nextMonday, nextFriday);
        System.out.println("═══════════════════════════════════════════════════════════");

        // ── Step 1: Fetch all active projects ─────────────────────────────────
        List<Project> activeProjects = projectDAO.fetchActiveProjects();
        System.out.printf("%n[Step 1] %d active project(s) fetched.%n", activeProjects.size());

        // ── Step 2: Remove projects that have aged out since last week ────────
        List<Project> viableProjects = removeUnschedulableProjects(
                activeProjects, planningDay, nextMonday);
        System.out.printf("[Step 2] %d project(s) viable for next week's execution.%n",
                viableProjects.size());

        // ── Step 3: Compute and persist historical revenue statistics ─────────
        computeAndStoreStatistics();

        // ── Steps 4 & 5: Score every project, then sort by priority ──────────
        List<Project> sorted = scoreAndSort(viableProjects, nextMonday);

        System.out.printf("%n[Steps 4–5] Projects ranked by priority score:%n");
        System.out.printf("  %-4s  %-28s  %10s  %6s  %12s%n",
                "Rank", "Title", "Score", "Slack", "Deadline");
        System.out.println("  " + "─".repeat(68));
        for (int i = 0; i < sorted.size(); i++) {
            Project p = sorted.get(i);
            System.out.printf("  #%-3d  %-28s  %10.2f  %6d  %12s%n",
                    i + 1,
                    truncate(p.getTitle(), 28),
                    p.getPriorityScore(),
                    p.getSlackDays(),
                    p.getAbsoluteDeadline()
            );
        }

        // ── Steps 6–8: Assign execution slots ─────────────────────────────────
        List<ScheduledEntry> schedule = buildSchedule(sorted, nextMonday, nextFriday);

        // ── Persist the scheduling decisions ──────────────────────────────────
        persistSchedule(schedule);

        System.out.printf("%n[Done] %d project(s) scheduled for the week.%n", schedule.size());
        System.out.println("═══════════════════════════════════════════════════════════");

        return schedule;
    }

    // ── STEP 2: REMOVE UNSCHEDULABLE PROJECTS ─────────────────────────────────

    /**
     * Filters out projects whose absolute deadline falls before next Monday.
     *
     * WHY A SECOND CHECK HERE? (First check is at insertion in ProjectDAO)
     *
     * Imagine this sequence across two weeks:
     *   Week 1 Saturday: Project X has deadline = next Wednesday → viable, inserted.
     *   Week 1 Saturday: Scheduler runs, but 5 higher-priority projects fill all slots.
     *                    Project X is not scheduled.
     *   Week 2 Saturday: Project X's deadline was last Wednesday — 3 days ago.
     *                    It can never be scheduled now. It must be expired.
     *
     * This "aging out" between runs is the gap that the second check covers.
     * The insertion gate only guar
     * ds against structurally impossible deadlines
     * at the moment of entry. Time does the rest of the damage.
     *
     * @param projects    all currently active projects from the DB
     * @param planningDay this Saturday
     * @param nextMonday  first slot of the execution window
     * @return only the projects that still have at least one valid slot
     */
    private List<Project> removeUnschedulableProjects(List<Project> projects,
                                                      LocalDate planningDay,
                                                      LocalDate nextMonday)
            throws SQLException {
        List<Project> viable = new ArrayList<>();

        for (Project p : projects) {
            LocalDate deadline = p.getAbsoluteDeadline();

            if (!SchedulingCalendar.isViable(deadline, planningDay)) {
                // Project has aged out — mark expired and skip it
                projectDAO.markProjectExpired(p.getProjectId());
                System.out.printf(
                        "  [AGED OUT → EXPIRED] '%s' | deadline=%s is before next Monday=%s%n",
                        p.getTitle(), deadline, nextMonday
                );
            } else {
                viable.add(p);
            }
        }

        return viable;
    }

    // ── STEP 3: HISTORICAL STATISTICS ─────────────────────────────────────────

    /**
     * Computes average and max revenue from historical (SCHEDULED/COMPLETED)
     * projects and saves a snapshot to the revenue_statistics table.
     *
     * WHY STORE STATISTICS SEPARATELY?
     * - Dashboard displays: "average project value this quarter"
     * - Future algorithm enhancement: weight priority score by how a project
     *   compares to historical average (a $5k project that's 3x the average
     *   is more notable than the same $5k in a high-revenue portfolio)
     * - Anomaly detection: flag projects with unusually high/low revenue
     *
     * For now, these stats are persisted for future use.
     */
    private void computeAndStoreStatistics() throws SQLException {
        List<Double> revenues = projectDAO.fetchHistoricalRevenues();

        if (revenues.isEmpty()) {
            System.out.printf("%n[Step 3] No historical data yet — statistics skipped.%n");
            return;
        }

        double sum     = revenues.stream().mapToDouble(Double::doubleValue).sum();
        double average = sum / revenues.size();
        double max     = revenues.stream().mapToDouble(Double::doubleValue).max().orElse(0);

        statisticsDAO.saveStatistics(average, max, revenues.size());
        System.out.printf(
                "%n[Step 3] Statistics snapshot saved — Avg: $%,.2f | Max: $%,.2f | n=%d%n",
                average, max, revenues.size()
        );
    }

    // ── STEPS 4 & 5: SCORE AND SORT ───────────────────────────────────────────

    /**
     * Computes each project's slack and priority score, then sorts them.
     *
     * PRIORITY SCORE FORMULA:
     *   Score = Revenue / (Slack + 1)
     *
     *   Slack = days between next Monday and the project's absolute deadline.
     *           (Measured from Monday, not Saturday — see SchedulingCalendar.computeSlack)
     *
     *   +1 prevents division by zero when Slack = 0.
     *   When Slack = 0 (project due Monday), Score = Revenue — the highest
     *   possible score for that revenue value.
     *
     * WHY THIS FORMULA WORKS:
     *   High revenue + low slack → very high score → scheduled first ✓
     *   High revenue + high slack → medium score → can wait ✓
     *   Low revenue + low slack → medium score → urgency keeps it competitive ✓
     *   Low revenue + high slack → very low score → scheduled last or deferred ✓
     *
     * TIE-BREAKING (applied in order when scores are equal):
     *   1. Higher revenue wins — same urgency, more money
     *   2. Earlier arrival date wins — fairness / FIFO for truly identical projects
     *
     * @param projects   list of viable projects (already passed expiry check)
     * @param nextMonday first day of the execution window (slack measured from here)
     * @return the same list, mutated with slack/score set, sorted descending by score
     */
    private List<Project> scoreAndSort(List<Project> projects, LocalDate nextMonday) {

        for (Project p : projects) {
            long   slack = SchedulingCalendar.computeSlack(p.getAbsoluteDeadline(), nextMonday);
            double score = p.getRevenue() / (slack + 1.0);

            p.setSlackDays(slack);
            p.setPriorityScore(score);
        }

        projects.sort(
                Comparator
                        .comparingDouble(Project::getPriorityScore).reversed()   // primary: score DESC
                        .thenComparingDouble(Project::getRevenue).reversed()      // tie-break 1: revenue DESC
                        .thenComparing(Project::getArrivalDate)                   // tie-break 2: arrival ASC
        );

        return projects;
    }

    // ── STEPS 6–8: BUILD THE SCHEDULE ─────────────────────────────────────────

    /**
     * Assigns each project (in priority order) to the LATEST available slot
     * that does not violate its deadline.
     *
     * ── WHY "LATEST POSSIBLE SLOT"? ──────────────────────────────────────────
     * Scheduling into the earliest available slot is wasteful of optionality.
     * Suppose you have two projects:
     *   A: due Wednesday  B: due Friday
     * If you give A the Monday slot, then B's scope is Tue–Fri (4 options).
     * If you give A the Wednesday slot (latest), B can still use Mon, Tue, Thu, Fri (4 options).
     * No difference here — but consider a third project C also due Wednesday:
     *   Earliest-slot: A takes Mon, B takes Fri. C must fit Tue–Wed.
     *                  C gets Tue or Wed — fine.
     *   Latest-slot:   A takes Wed, B takes Fri. C needs ≤ Wed, all of Mon, Tue free.
     *                  C gets Tue → A gets Wed → more flexibility preserved. ✓
     *
     * In general: later slots are "cheaper" — they consume fewer options.
     * Greedy selection of latest slot maximises remaining schedule flexibility.
     *
     * ── DATA STRUCTURE: TreeSet ───────────────────────────────────────────────
     * TreeSet keeps dates in sorted order automatically.
     * TreeSet.floor(x) returns the largest element ≤ x in O(log n) time.
     * This is the exact operation we need: "latest available slot ≤ project deadline".
     * Once a slot is taken, remove it — floor() will never return it again.
     *
     * ── DEADLINE CONSTRAINT ───────────────────────────────────────────────────
     * We never assign a slot after the project's deadline.
     * floor(min(deadline, Friday)) guarantees the assigned slot ≤ deadline.
     *
     * ── ONE PROJECT PER DAY ───────────────────────────────────────────────────
     * TreeSet.remove(slot) after assignment ensures each slot is used at most once.
     *
     * @param sortedProjects  projects sorted by priority score (highest first)
     * @param nextMonday      first available slot
     * @param nextFriday      last available slot
     * @return list of ScheduledEntry objects (the final week plan)
     */
    private List<ScheduledEntry> buildSchedule(List<Project> sortedProjects,
                                               LocalDate nextMonday,
                                               LocalDate nextFriday) {

        // Build the pool of 5 working slots: Mon, Tue, Wed, Thu, Fri
        TreeSet<LocalDate> availableSlots = new TreeSet<>();
        for (int i = 0; i < MAX_PROJECTS_PER_WEEK; i++) {
            availableSlots.add(nextMonday.plusDays(i));
        }

        List<ScheduledEntry> schedule = new ArrayList<>();

        System.out.printf("%n[Steps 6–8] Assigning slots (latest-slot-first rule):%n");
        System.out.println("  " + "─".repeat(72));

        for (Project p : sortedProjects) {

            if (schedule.size() >= MAX_PROJECTS_PER_WEEK) {
                System.out.printf("  [WEEK FULL] '%s' deferred to next cycle.%n", p.getTitle());
                continue;
            }

            LocalDate deadline = p.getAbsoluteDeadline();

            // Latest valid slot = earlier of (project deadline, next Friday)
            // We cannot schedule beyond the week, and cannot exceed the deadline.
            LocalDate latestValid = deadline.isBefore(nextFriday) ? deadline : nextFriday;

            // Find the largest available slot that is ≤ latestValid
            // Returns null if all remaining slots are AFTER latestValid
            LocalDate assigned = availableSlots.floor(latestValid);

            if (assigned == null) {
                // All remaining slots are after this project's deadline.
                // Assigning any of them would violate the deadline — skip.
                System.out.printf(
                        "  [NO VALID SLOT] %-28s | deadline=%s | remaining slots all > deadline%n",
                        truncate(p.getTitle(), 28), deadline
                );
                continue;
            }

            // Defensive guard: floor() guarantees assigned ≤ latestValid ≤ deadline.
            // This check should never trigger, but catches any future logic errors.
            if (assigned.isAfter(deadline)) {
                System.out.printf(
                        "  [GUARD TRIGGERED] '%s' — slot %s would exceed deadline %s. Skipped.%n",
                        p.getTitle(), assigned, deadline
                );
                continue;
            }

            // Claim the slot — remove so no other project can take it
            availableSlots.remove(assigned);

            ScheduledEntry entry = new ScheduledEntry(
                    p.getProjectId(),
                    p.getTitle(),
                    assigned,
                    nextMonday,
                    p.getRevenue()
            );
            schedule.add(entry);

            System.out.printf(
                    "  [ASSIGNED] %-28s → %s (%s) | score=%8.2f | deadline=%s%n",
                    truncate(p.getTitle(), 28),
                    assigned,
                    assigned.getDayOfWeek(),
                    p.getPriorityScore(),
                    deadline
            );
        }

        System.out.println("  " + "─".repeat(72));
        return schedule;
    }

    // ── PERSIST ───────────────────────────────────────────────────────────────

    /**
     * Writes all scheduling decisions to the database.
     *
     * WHY PERSIST AFTER THE FULL SCHEDULE IS BUILT?
     * If we persisted slot-by-slot and then hit a failure midway,
     * the database would contain a partial schedule — some projects
     * marked SCHEDULED with slots, others still ACTIVE. This is an
     * inconsistent state that's hard to recover from.
     *
     * Building the full in-memory schedule first, then writing it all,
     * reduces the window of inconsistency. For true atomicity in production,
     * wrap all inserts in a single database transaction:
     *   conn.setAutoCommit(false);
     *   ... all inserts ...
     *   conn.commit();  // or conn.rollback() on failure
     */
    private void persistSchedule(List<ScheduledEntry> schedule) throws SQLException {
        for (ScheduledEntry entry : schedule) {
            projectDAO.insertScheduledEntry(entry);
            projectDAO.markProjectScheduled(entry.getProjectId());
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}