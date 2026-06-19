# Open Questions and Assumptions — docToolchain v4

This document tracks open questions and assumptions for the v4 architecture.
Each entry includes source references and the arc42 section it affects.

## Open Questions

### Q1: How should dependencies be managed without Gradle? (ADR-TBD-6)

- **Source**: ADR-3 removes Gradle. AsciiDoctor JVM alone has ~20 transitive JARs.
- **Affects**: Section 05 (Building Blocks), Section 06 (Runtime), Section 08 (Concepts)
- **Options**: lib/ directory with bundled JARs, download-on-demand, Groovy Grape (@Grab)
- **Impact**: Affects startup time (QS-9), distribution size, offline capability, and first-run experience (QS-6).

### Q2: Is the Confluence v1 API planned for deprecation? (ADR-TBD-1)

- **Source**: `ConfluenceClientV1.groovy`, `ConfluenceClientV2.groovy` — two parallel implementations
- **Affects**: Section 09 (ADRs), Section 11 (Risks)
- **Impact**: Maintaining two clients doubles integration effort. Atlassian is deprecating v1 for Cloud.

### Q3: What is the modern UI design direction?

- **Source**: ADR-4 (jBake replacement) creates the opportunity for a new theme.
- **Affects**: Section 08 (Concepts — Custom Site Generator)
- **Status**: Phase 1: modern, clean design with dark mode and search. Phase 2 (future): LLM-augmented UI.
- **Open**: Specific design system, CSS framework, search implementation not yet decided.

### Q4: How will the v3 → v4 migration path work?

- **Source**: ADR-3 (Gradle removal), ADR-4 (jBake replacement)
- **Affects**: Section 11 (Risks), QS-14 (v3 compatibility)
- **Impact**: Users with custom Gradle tasks, Gradle plugins, or `./gradlew` invocations need a migration path. `docToolchainConfig.groovy` compatibility is confirmed.

### Q5: Should docToolchain itself become an MCP server?

- **Source**: ADR-5 (LLM-native architecture). Currently LLM access is via daCLI (separate tool).
- **Affects**: Section 03 (Context), Section 05 (Building Blocks)
- **Status**: Deferred. daCLI handles MCP for now. docToolchain remains a CLI tool. Future version may add MCP endpoint for task invocation (generateHTML, publishToConfluence as MCP tools).

## Assumptions

### A1: Scripts are the right granularity for task logic

- **Basis**: User requirement — "Scripts are easier to adapt and maintain than compiled Java code." Community contributors can modify behavior without understanding build systems.
- **Confidence**: HIGH — explicit decision by project maintainer.
- **Used in**: Section 04 (Solution Strategy), Section 05 (Building Block View)

### A2: Task dependencies are not needed

- **Basis**: User observation — "I used to think people would chain tasks, but they don't. They call them individually."
- **Confidence**: HIGH — based on real-world usage patterns.
- **Used in**: Section 06 (Runtime View), Section 08 (Script Execution Model)

### A3: Existing Groovy SimpleTemplate files are compatible with the custom SSG

- **Basis**: jBake uses Groovy's `SimpleTemplateEngine`, which is a standard Groovy feature. The custom SSG will use the same engine.
- **Confidence**: MEDIUM — templates may reference jBake-specific variables (`published_posts`, `config.site_*`) that need to be provided by the new generator.
- **Used in**: ADR-4 (jBake replacement), Section 08 (Custom Site Generator)

### A4: Docker remains equally important in v4

- **Basis**: User confirmation — "Docker bleibt gleichwertig."
- **Confidence**: HIGH — explicit decision.
- **Used in**: Section 07 (Deployment View)

### A5: v3 docToolchainConfig.groovy files work without changes in v4

- **Basis**: User requirement — backward compatibility is a top-level quality goal (QS-14).
- **Confidence**: HIGH for core features (generateHTML, generatePDF, publishToConfluence). MEDIUM for site generation (jBake metadata headers must be supported by new SSG).
- **Used in**: Section 02 (Constraints), ADR-4
