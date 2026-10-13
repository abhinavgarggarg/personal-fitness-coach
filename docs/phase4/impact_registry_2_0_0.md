# Impact of change set 2.0.0 (MAJOR)

Weekly limits that follow how often you train, with a floor and a spike guard (Phase 4 Part 1)

Base registry 1.1.2 · 2026-10-13

The weekly limits (VOL-007 training stress, LOAD-005 effort-load) compare a week with the last three weeks. Twelve-month simulations of eight people under three ways of turning up (Phase 4 Part 1, D-110) showed that, measured against the weeks as done, a missed session makes its week look light and the next limit follows it down: below about 87% turnout (1 / 1.15) the limits fall week after week. Someone who misses about 1 session in 5 was down to recovery days within 2-3 months (82-125 a year), and a bodyweight-only user was, even turning up every time, because whole conditioning blocks were dropped and the week ended far below its limit. Calibration weeks looked light too, because the easy ramp sets of a weight still being found count nothing. This change counts each past week at the level of the sessions that were done (a week cut short by more than half is left out, like a missed week), counts a weight still being found as two sets at the target effort as it is planned, plans the week within the limits (other exercises' sets first, then conditioning shortened or made easy, then main lifts), and never lets the limits cut a week below half of its plan (about a deload week, DEL-003). Above that floor, a new spike guard holds a week to twice the last three weeks as done. The 1.15 and 1.20 multipliers stay. It is MAJOR because it changes where a safety limit applies.

| Rule | Change | Safety rule | Algorithms | Code that uses it | Tests (registry → found) |
| --- | --- | --- | --- | --- | --- |
| VOL-007 | MAJOR 1.0.0→2.0.0: wording; parameters replaced; evidence + PFCSim2026; uncertainty note | yes | volume_allocation, workload, safety_validator | EngineBridge.kt, Ssu.kt, SessionGenerator.kt, WeekPlanner.kt, SessionValidator.kt, WeeklyLimits.kt | TC-VOL-007a ✓, TC-VOL-007b ✓ |
| LOAD-005 | MAJOR 1.0.0→2.0.0: wording; evidence + Impellizzeri2021, PFCSim2026; uncertainty note | yes | weekly_scheduling, safety_validator | EngineBridge.kt, Workload.kt, SessionGenerator.kt, WeekPlanner.kt, SessionValidator.kt | TC-LOAD-005a ✓, TC-LOAD-005b ✓ |

**Sources:** PFCSim2026 (new)

**Before release:** every rule above keeps its two tests passing (rule coverage 100%); safety rules need the safety review note; the Product Owner approves the change set; the app is rebuilt and the whole test suite runs (Phase 1 step 10).
