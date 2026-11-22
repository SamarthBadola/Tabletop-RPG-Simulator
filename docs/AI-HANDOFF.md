# AI and Contributor Handoff

Last reconciled: 2026-11-22. This file indexes the approved project baseline; read the linked specifications before implementation. Re-check the repository, PR state and CI before relying on implementation-status details.

## Canonical documents

1. [Requirements Specification v1.20](Tabletop%20RPG%20Simulator%20Requirements%20Specification.md)
2. [Architecture v5.16](architecture.md)
3. [AI Build Brief v1.28](Tabletop%20RPG%20Simulator%20AI%20Build%20Brief.md)
4. [Ruleset Specification v1.3](Tabletop%20RPG%20Simulator%20Ruleset%20Specification.md)
5. [Decision Log and Progress Tracker v1.34](Tabletop%20RPG%20Simulator%20Decision%20Log%20and%20Progress%20Tracker.md)
6. [Content Schema Specification v1.1](Tabletop%20RPG%20Simulator%20Content%20Schema%20Specification.md)
7. [UX and API Specification v1.5](Tabletop%20RPG%20Simulator%20UX%20and%20API%20Specification%20%28v1.5%29.md)
8. [Grid and Pathfinder Specification v0.4 (draft)](Tabletop%20RPG%20Simulator%20Grid%20and%20Pathfinder%20Specification%20%28v0.4%29.md)
9. [Repository governance](REPOSITORY-GOVERNANCE.md)

The Ruleset is the source of truth for game mechanics. Each more-specific specification governs its own domain; the AI Build Brief defines the implementation sequence and working constraints. Preserve the versions and wording of these approved documents. Report conflicts instead of resolving them by guesswork. The UX/API specification v1.5 is accepted, including UXD-11, UXQ-24 and UX-059 to UX-061. The Grid and Pathfinder specification remains draft at v0.4 until its shared contract corpus exists.

## Project and platform

- Repository: `SamarthBadola/Tabletop-RPG-Simulator`.
- Product: self-hosted, browser-based D&D SRD 5.1 character manager and encounter runner; it is not a virtual tabletop.
- Project baseline recorded in the approved brief: Java 25, Spring Boot 4.1.1, PostgreSQL 16.15, Maven Wrapper.
- Maven coordinates: `com.github.sbadolankaur:tabletop-rpg-simulator`; root package: `com.github.sbadolankaur.rpg`.
- The coding AI owns the backend and demo-only frontend elements. Claude owns the real templates and static frontend from the Figma designs.
- Never merge a PR on the owner's behalf. Leave documentation and implementation PRs open for owner review and merge.

## Implementation status

Step 2 is complete and merged in PR #27 (merge commit `4110e2f`); its CI run 122 passed. The dice engine implements D-16's term-level `criticalNotApplied` marker without logging or writing events. Step 7 will consume that marker. Step 3 (kernel and architecture tests) is next.
