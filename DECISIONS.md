# DECISIONS — Personal Fitness Coach

Format: decision · alternatives considered · reason · date. Phase 1 decisions are PROPOSED until sign-off.

| # | Decision | Alternatives considered | Reason | Date |
|---|---|---|---|---|
| D-001 | Phase 1 report delivered as a Claude Doc (commentable), with a markdown copy and a JSON registry in the Fitness App Project | Inline chat delivery; markdown files only | Product Owner can comment per section at the gate; Project keeps state across sessions (A7) | 2026-10-07 |
| D-002 | APK route will be F3 Option B (GitHub Actions + GitHub Releases) | Build in this environment | Android/Flutter/Maven hosts blocked; no emulator; no GitHub auth (A8 audit) | 2026-10-07 |
| D-003 | Periodisation: autoregulated hybrid (5-week blocks + within-week DUP + session autoregulation) | Linear; pure DUP; pure block; non-periodised | Periodisation helps strength modestly (Moesgaard 2022); ACSM 2026 says complexity is unnecessary; blocks suit multiple objectives | 2026-10-07 |
| D-004 | Fractional set counting (1.0 primary, 0.5 secondary) | Direct-only; total sets | Strongest relative evidence in Pelland 2024 (preprint) | 2026-10-07 |
| D-005 | Volume caps 12/16/20 fractional sets/muscle/week; block start 6/8/10 | Higher caps from hypertrophy literature | Concurrent conditioning adds recovery cost; diminishing returns | 2026-10-07 |
| D-006 | Failure only on FailureSafe exercises, ≤2/session, never beginners' first 8 weeks | Allow failure broadly; ban failure | Failure adds little (Robinson 2024, Grgic 2022, ACSM 2026) and raises risk on free-weight compounds | 2026-10-07 |
| D-007 | e1RM = Epley with RIR adjustment; only sets with reps+RIR ≤12, RIR ≤3 | Brzycki; multi-formula blend | Simple; accurate near failure (LeSuer 1997); Epley ≈ Brzycki in valid range | 2026-10-07 |
| D-008 | Readiness: 4 items, weights 0.30/0.30/0.25/0.15, 3 = normal = 50; personal-baseline blend after 14 check-ins; tiers 40/28/15 | Seed "R<40 low" on unspecified scale; objective-only | Self-report tracks load well (Saw 2016); seed threshold recast so a normal day is FULL | 2026-10-07 |
| D-009 | ACWR downgraded to a soft flag (EWMA, ≥28 days); planning cap (+20% / 1.20 × 3-week mean) is the real guard | ACWR 0.8–1.3 target zone | Mathematical coupling and weak causal evidence (Lolli 2019, Impellizzeri 2021) | 2026-10-07 |
| D-010 | Deloads triggered by 6 fatigue signals; reduced volume, not rest | Fixed every-4th-week deloads; full rest weeks | Prompt rejects fixed calendars; Coleman 2024: full week off no benefit; Bell 2023: cut volume | 2026-10-07 |
| D-011 | HIIT ≤2/week default; 3rd only under strict conditions; hard circuits count | Seed ≤2–3 | Recovery cost and interference; little direct evidence for general fitness | 2026-10-07 |
| D-012 | Excluded modalities: treadmill running, all stationary bikes, stair machines; air/fan bikes and treadmill walking excluded pending confirmation | Allow air bikes | Prompt A11: treat uncertain modalities as excluded | 2026-10-07 |
| D-013 | New SSU (systemic stress units) equation for planning caps, calibrated to the user's session-RPE load after 4 weeks | sRPE only; volume-only caps | Prompt B3.4 requires one systemic stress model; labelled Expert Practice, to be validated in Phase 4 | 2026-10-07 |
| D-014 | Pain gate threshold 3/10 for continuing unsupervised | 5/10 (rehab pain-monitoring model) | Silbernagel 2007 used 5/10 under clinical supervision; app is unsupervised | 2026-10-07 |
| D-015 | Screening questions written originally (ACSM 2015 logic, PAR-Q+ domains) | Use PAR-Q+ verbatim | Licence not yet checked (D7) | 2026-10-07 |
| D-016 | Engineering/product rules labelled "Product Rule (not an evidence claim)" in the registry, separate from the four evidence levels | Force into the four-level scale | Honesty: these are not scientific claims (A5) | 2026-10-07 |
| D-017 | Phase 1 approved by the Product Owner; Rule Registry promoted to v1.0.0 (all 140 rules "approved") | — | Explicit sign-off: "Phase 1 is approved" | 2026-10-07 |
| D-018 | Air/fan bikes and treadmill walking remain excluded | Allow them | No reply to the doc comment; approval given with the default in place (A11) | 2026-10-07 |
