# Open Questions and Assumptions

This document tracks open questions and assumptions made during the creation of the arc42 architecture documentation for docToolchain.
Each entry includes the source file reference where the question originated and the arc42 section it affects.

## Open Questions

### Q1: What is the intended precedence between Config.groovy and docToolchainConfig.groovy?

- **Source**: `gradle.properties` line 39 (`mainConfigFile = Config.groovy`) vs. `dtcw` line 29 (`DTC_CONFIG_FILE:=docToolchainConfig.groovy`)
- **Affects**: Section 02 (Constraints), Section 08 (Configuration Concept), ADR-TBD-3
- **Impact**: New users receive conflicting signals about which config file to use. The dtcw wrapper and the Gradle default disagree.

### Q2: Is the Confluence v1 API planned for deprecation?

- **Source**: `core/src/main/groovy/org/docToolchain/tasks/AbstractConfluenceTask.groovy`, `core/src/main/groovy/org/docToolchain/atlassian/confluence/clients/ConfluenceClientV1.groovy`
- **Affects**: Section 09 (ADR-TBD-1), Section 11 (Risks)
- **Impact**: Atlassian is deprecating v1 for Cloud. Maintaining two client implementations indefinitely doubles integration effort.

### Q3: What is the target state for the core module migration?

- **Source**: `src/docs/050_ADRs/ADR-2-separate-core-logic-from-gradle.adoc` (status: "under ongoing discussion"), `core/src/main/groovy/org/docToolchain/tasks/` (7 task classes)
- **Affects**: Section 05 (Building Block View Level 2), Section 09 (ADR-TBD-4), Section 11 (Risks)
- **Impact**: The migration boundary is unclear. Should all tasks move to core, or only those with external API calls?

### Q4: Is there a formal versioning policy for external API integrations?

- **Source**: No documentation found. API versions are hardcoded in `ConfluenceClientV1.groovy`, `ConfluenceClientV2.groovy`, `JiraServerClient.groovy`.
- **Affects**: Section 02 (Constraints), Section 10 (Quality Requirements)
- **Impact**: Atlassian API changes could break publishing without warning. No policy exists for API version support lifecycle.

### Q5: What is the strategy for Enterprise Architect export on non-Windows platforms?

- **Source**: `scripts/exportEA.gradle`, VBScript files in `scripts/`
- **Affects**: Section 03 (Context), Section 11 (Risks)
- **Impact**: EA export is Windows-only (COM automation). Non-Windows users cannot export EA diagrams. No fallback or alternative approach is documented.

## Assumptions

### A1: The 4-layer architecture is the intended design

- **Basis**: Consistent code structure across all execution paths: `dtcw` → `build.gradle` → `scripts/*.gradle` → `core/`
- **Confidence**: HIGH
- **Verified by**: CLAUDE.md, copilot-instructions.md, and codebase structure all describe this layering consistently.

### A2: ADR-2 represents the current strategic direction

- **Basis**: ADR-2 status says "under ongoing discussion" but the `core/` module exists, is actively used, and new Confluence/Jira features are being built there.
- **Confidence**: MEDIUM — The migration is partially complete, but no timeline or completion criteria exist.
- **Assumption used in**: Section 04 (Solution Strategy), Section 05 (Building Block View)

### A3: Quality goals are inferred from design choices, not from a formal requirements document

- **Basis**: No explicit quality requirements document was found. Quality goals in Section 01 and scenarios in Section 10 were derived from observed architectural patterns, README.adoc, test structure, and wrapper script design.
- **Confidence**: MEDIUM — The goals reflect what the architecture achieves, but may not capture all stakeholder priorities.
- **Assumption used in**: Section 01 (Quality Goals), Section 10 (Quality Scenarios)

### A4: The three execution environments (local, Docker, SDKMAN) are all equally supported

- **Basis**: `dtcw` wrapper supports all three with equal feature coverage. Docker images are built in CI. SDKMAN release is part of the release workflow.
- **Confidence**: HIGH — Evidence in `dtcw`, `.github/workflows/release.yml`, and `docker-image/` repository.
- **Assumption used in**: Section 07 (Deployment View)

### A5: Spock was chosen deliberately for testing, despite no formal ADR

- **Basis**: A stub ADR-3 exists in `src/test/groovy/docToolchain/ADR-3-useSpock.adoc` containing only placeholder text. However, Spock 2.3 is extensively used across all 66 core tests and is listed in `libs.versions.toml`.
- **Confidence**: HIGH for the de facto decision, LOW for formal documentation.
- **Assumption used in**: Section 02 (Conventions), Section 04 (Technology Decisions)
