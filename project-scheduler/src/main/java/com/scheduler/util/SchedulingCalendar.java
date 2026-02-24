package com.scheduler.util;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;

/**
 * Single source of truth for ALL scheduling date arithmetic.
 *
 * WHY A DEDICATED CLASS?
 * Every layer — DAO (viability check at insert), Service (slot window),
 * and CLI (schedule display) — needs to answer the same questions:
 *   "When is the next Saturday?"
 *   "When does next week start?"
 *   "Is this project's deadline reachable?"
 *
 * If that logic lived in each class separately, a single rule change
 * (e.g. company moves to Sunday planning) would require hunting down
 * every occurrence. Here you change it once.
 *
 * SYSTEM RULES ENCODED HERE:
 *   - Planning happens every Saturday
 *   - Execution week = next Monday through next Friday (5 slots)
 *   - A project is viable only if its deadline >= next Monday
 *   - Slack is measured from next Monday (first working day),
 *     NOT from Saturday (weekend days are not working slots)
 */
public class SchedulingCalendar {

    private SchedulingCalendar() {}

    // ── ANCHOR DATES ──────────────────────────────────────────────────────────

    /**
     * Returns the Saturday of the current week.
     * If today IS Saturday, returns today.
     *
     * DayOfWeek values: MON=1, TUE=2, WED=3, THU=4, FRI=5, SAT=6, SUN=7
     * daysUntilSaturday = (6 - today.getValue() + 7) % 7
     *
     * Examples:
     *   today = Monday (1)  → (6 - 1 + 7) % 7 = 12 % 7 = 5 → Monday + 5 = Saturday ✓
     *   today = Friday (5)  → (6 - 5 + 7) % 7 =  8 % 7 = 1 → Friday  + 1 = Saturday ✓
     *   today = Saturday(6) → (6 - 6 + 7) % 7 =  7 % 7 = 0 → Saturday + 0 = Saturday ✓
     */
    public static LocalDate getPlanningDay(LocalDate today) {
        int daysUntilSaturday = (DayOfWeek.SATURDAY.getValue()
                - today.getDayOfWeek().getValue() + 7) % 7;
        return today.plusDays(daysUntilSaturday);
    }

    /**
     * Next Monday = the Monday immediately after the planning Saturday.
     * Saturday + 2 days = Monday, always.
     */
    public static LocalDate getNextMonday(LocalDate planningDay) {
        return planningDay.plusDays(2);
    }

    /**
     * Next Friday = next Monday + 4 days.
     * Mon(+0) Tue(+1) Wed(+2) Thu(+3) Fri(+4)
     */
    public static LocalDate getNextFriday(LocalDate nextMonday) {
        return nextMonday.plusDays(4);
    }

    // ── VIABILITY GATE ────────────────────────────────────────────────────────

    /**
     * A project is viable for this Saturday's scheduling run if and only if
     * its absolute deadline falls ON or AFTER next Monday.
     *
     * WHY NEXT MONDAY and not Saturday?
     * The earliest slot we can assign is Monday. If a project is due Sunday
     * (the day before Monday), there is literally no slot we can put it in.
     * It must be rejected.
     *
     * A project due exactly on Monday (deadline == nextMonday) IS viable:
     * it gets assigned the Monday slot with slack = 0.
     *
     * @param absoluteDeadline  the project's computed due date (arrival + deadlineDays)
     * @param planningDay       the Saturday on which scheduling runs
     * @return true if the project can be scheduled next week
     */
    public static boolean isViable(LocalDate absoluteDeadline, LocalDate planningDay) {
        LocalDate nextMonday = getNextMonday(planningDay);
        return !absoluteDeadline.isBefore(nextMonday);
    }

    // ── SLACK CALCULATION ─────────────────────────────────────────────────────

    /**
     * Slack = working days between next Monday and the project's deadline.
     *
     * WHY MEASURE FROM NEXT MONDAY, NOT FROM SATURDAY?
     *
     * Saturday and Sunday are non-working days. No slots exist on those days.
     * If you measured slack from Saturday:
     *   - A project due next Monday would show slack = 2 (Sat → Mon)
     *   - But those 2 days are a weekend — zero usable slots
     *   - Its Priority Score = Revenue / (2 + 1) would be artificially low
     *   - A genuinely urgent project (due Monday) would look less urgent than it is
     *
     * Measuring from Monday gives slack in actual available working slots:
     *   - Due Monday  → slack = 0 → score = Revenue / 1 → maximum urgency ✓
     *   - Due Tuesday → slack = 1 → score = Revenue / 2
     *   - Due Friday  → slack = 4 → score = Revenue / 5
     *
     * @param absoluteDeadline  the project's computed due date
     * @param nextMonday        first working day of the execution week
     * @return number of days between nextMonday and deadline (0 = due Monday)
     */
    public static long computeSlack(LocalDate absoluteDeadline, LocalDate nextMonday) {
        return ChronoUnit.DAYS.between(nextMonday, absoluteDeadline);
    }
}
