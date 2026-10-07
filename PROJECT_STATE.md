# PROJECT STATE — Personal Fitness Coach

Last updated: 2026-10-07 · Master prompt: v3.0 (7 Oct 2026)

## Current phase
**Phase 1 — Fitness Research & Evidence Engine: COMPLETE (draft), AWAITING PRODUCT OWNER SIGN-OFF.**
Do not start Phase 2 until the Product Owner replies "APPROVED".

## Where things live
(In the claude.ai "Fitness App" Project, every repo file below is stored under the `claude/` prefix, e.g. `claude/rules/rule_registry_v0.1.json`.)
- Phase 1 report (reviewable, commentable): Claude Doc "Personal Fitness Coach — Phase 1: Fitness Research & Evidence Engine"
  https://claude.ai/code/artifact/277679ce-731c-4ce0-8870-9cb5347a5d51
- Markdown copy of the report: `docs/phase1/phase1_report.md` (also saved in the "Fitness App" Project)
- Rule Registry v0.1 (140 rules, 64 sources, all status "proposed", version 0.1.0): `rules/rule_registry_v0.1.json`
- Registry generator: `tools/build_registry.py` (single source of truth for rule parameters)
- Checks (registry integrity, doc/registry agreement, worked-example arithmetic): `tools/check_phase1.py` — 44 checks, 0 failures on 2026-10-07

## Deliverable status (A5 labels)
| Deliverable | Status |
|---|---|
| 1 Environment audit | VERIFIED (commands run 2026-10-07) |
| 2–4 Evidence map, confidence matrix, seed validation S01–S22 | Complete; 11 sources checked, 52 located, 1 UNVERIFIED |
| 5–36 Frameworks, algorithms, safety, data taxonomy | Complete as specification; expert-practice values REQUIRE VALIDATION (Phase 4) |
| 37–38 Research update architecture, versioning, Rule Registry v0.1 | Complete; registry VERIFIED by script |
| 39 Traceability matrix | Complete; tests NOT STARTED |
| 40 Uncertainties | Complete (16 items) |

## Environment (A8, 2026-10-07)
- Available: web search/fetch, shell, Python 3.13, git, JDK 21, Gradle 8.14.3 (Kotlin 2.0.21 bundled), pip/npm registries.
- Not available: Android SDK, Flutter SDK, emulator (no /dev/kvm), physical device, GitHub auth (token invalid).
- Network policy blocks dl.google.com, maven.google.com, Maven Central, Gradle Plugin Portal, Flutter storage → Android builds impossible here → F3 Option B (GitHub Actions + GitHub Releases).
- PubMed/PMC pages blocked by bot check; Europe PMC API rate-limited (do not retry).

## Open questions for the Product Owner (none block sign-off)
1. Air/fan bikes and treadmill walking: excluded by default (ASSUMPTION) — confirm? (doc comment left on section 16)
2. Part 0 profile is blank; defaults used: adult, beginner rules until classified, 3 days × 60 min, typical commercial gym incl. rower/SkiErg/elliptical, no injuries, kg. Collected in onboarding.
3. Before Phase 3: Product Owner needs a free GitHub account (cloud builds).

## Next actions
1. Wait for Phase 1 sign-off ("APPROVED") or feedback; on feedback revise doc + registry, re-run checks, re-present gate.
2. On approval: bump all rules to 1.0.0/approved (rebuild registry), log in DECISIONS.md and CHANGELOG.md.
3. Phase 2 prep: read ACSM 2026 position stand full text; re-check Pelland 2024 publication status; licence check on PAR-Q+ wording.
4. Start Phase 2 (PRD, UX, architecture, stack decision).
