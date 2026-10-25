# Impact of change set 4.0.0 (MAJOR)

Safety audit decisions: strong muscle pain, form with missed reps, the bone-loading block

Base registry 3.0.1 · 2026-10-25

Three safety boundaries change after the Phase 4 Part 4 independent safety review, each chosen by the Product Owner on 25 October 2026. SAF-003: muscle pain (a burn or soreness) rated 7/10 or more stops that exercise for today and the rest of the day goes easy on that area; it was always 'carry on'. PROG-008: a form answer other than 'yes' no longer stops the PROG-004 reduction when the reps fell short or the sets were much harder than planned; the easier version is offered only when the library has one. CON-004: the osteoporosis bone-loading block starts with heel drops for its first 4 weeks; small hops follow only for someone who is not a beginner, is under 65, doesn't have low back pain and has reported no hip, knee or ankle pain in the last 4 weeks; the block stays when another condition sets impact 'as tolerated' (controlled high blood pressure). The health-condition table's entries don't change.

| Rule | Change | Safety rule | Algorithms | Code that uses it | Tests (registry → found) |
| --- | --- | --- | --- | --- | --- |
| SAF-003 | MAJOR 1.0.0→2.0.0: wording; parameters replaced; uncertainty note | yes | guided_workout, substitution, safety_validator | AppController.kt, Pain.kt, Coach.kt, SessionPlayer.kt, PainGate.kt, SessionValidator.kt | TC-SAF-003a ✓, TC-SAF-003b ✓, TC-SAF-003e ✓ |
| PROG-008 | MAJOR 1.0.0→2.0.0: wording; uncertainty note | no | progressive_overload, guided_workout | EngineBridge.kt, ExerciseProgress.kt, Progression.kt | TC-PROG-008a ✓, TC-PROG-008b ✓, TC-PROG-008c ✓, TC-PROG-008d ✓ |
| CON-004 | MAJOR 1.1.0→2.0.0: wording; parameters replaced; evidence + ROS22; uncertainty note | no | conditioning_selection | EngineBridge.kt, SessionGenerator.kt, WeekPlanner.kt, SessionValidator.kt | TC-CON-004a ✓, TC-CON-004b ✓, TC-CON-004d ✓, TC-CON-004e ✓ |

**Before release:** every rule above keeps its two tests passing (rule coverage 100%); safety rules need the safety review note; the Product Owner approves the change set; the app is rebuilt and the whole test suite runs (Phase 1 step 10).
