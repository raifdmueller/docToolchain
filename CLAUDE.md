# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**docToolchain** is an open-source documentation generation tool implementing the "docs-as-code" approach. It's built with Groovy/Java using Gradle and targets cross-platform deployment (Linux, macOS, Windows).

- **Current Version**: 3.4.2 (ng branch)
- **Primary Language**: Groovy with Java 17
- **Build System**: Gradle 8.1.1
- **Primary Interface**: `dtcw` wrapper script (NOT direct Gradle commands)

## Essential Commands

### Build and Test

```bash
# Build the project (expect ~11 test failures out of 53 - this is normal)
./gradlew clean build

# Run core tests only (should always pass - 66 tests, 3 skipped)
./gradlew core:test

# Run all tests (some failures expected if external tools missing)
./gradlew test

# Check for dependency updates
./gradlew dependencyUpdates
```

### Using dtcw Wrapper (Primary Interface)

**CRITICAL**: Always use `./dtcw` for docToolchain operations, not direct Gradle commands.

```bash
# Check version and status
./dtcw --version

# List all docToolchain tasks
./dtcw tasks --group doctoolchain

# Install Java 17 locally
./dtcw local install java

# Install docToolchain locally
./dtcw local install doctoolchain

# Generate documentation (basic functionality test)
./dtcw local generateHTML    # Output: build/html5/manual_test_script.html (~37KB)
./dtcw local generatePDF     # Output: build/pdf/manual_test_script.pdf (~62KB)

# Generate complete microsite
./dtcw local generateSite

# Preview microsite
./dtcw local previewSite
```

### Development Workflow

```bash
# 1. Validate shell scripts
find . -name "*.sh" -exec shellcheck {} \;

# 2. Run full CI pipeline locally
./.ci.sh

# 3. Build core module and create distribution
./gradlew core:jar
./gradlew prepareDist
./gradlew createDist
```

### Running Tests

```bash
# Unit tests (Spock framework in core module)
./gradlew core:test --info

# Integration tests (BATS - requires bats-core installed)
cd test && bats *.bats

# Test specific environment
bats test/local_environment.bats
bats test/docker_environment.bats
bats test/sdk_installation.bats
```

## Architecture and Code Organization

### Directory Structure

```
docToolchain/
├── core/                          # Core Java/Groovy library
│   ├── src/main/groovy/org/docToolchain/
│   │   ├── tasks/                 # Gradle task implementations
│   │   ├── atlassian/             # Jira & Confluence integration
│   │   │   ├── jira/              # Jira client, converters, utilities
│   │   │   └── confluence/        # Confluence publishing
│   │   ├── configuration/         # Configuration handling
│   │   └── scripts/               # Core script utilities
│   └── src/test/groovy/           # Spock unit tests
├── scripts/                       # Gradle task scripts (plugins)
│   ├── AsciiDocBasics.gradle      # Core document generation
│   ├── publishToConfluence.gradle # Confluence publishing
│   ├── exportJiraIssues.gradle    # Jira integration
│   ├── exportEA.gradle            # Enterprise Architect export
│   ├── generateSite.gradle        # Microsite generation (jBake)
│   └── [20+ other task scripts]
├── src/
│   ├── docs/                      # Documentation sources (AsciiDoc)
│   │   ├── 010_manual/            # User manual
│   │   ├── 015_tasks/             # Task documentation
│   │   ├── 020_tutorial/          # Tutorials
│   │   └── images/                # Image assets
│   ├── site/                      # Microsite templates (jBake)
│   └── test/                      # Test resources
├── test/                          # BATS integration tests
├── bin/                           # Shell scripts (doctoolchain, autobuildSite.bash)
├── template_config/               # Default configuration templates
├── dtcw, dtcw.ps1, dtcw.bat      # Cross-platform wrapper scripts
├── build.gradle                   # Main build configuration
├── docToolchainConfig.groovy      # Project configuration
├── Config.groovy                  # Alternative configuration file
└── libs.versions.toml             # Dependency version catalog
```

### Core Architecture

#### 1. Multi-Layer Build System
- **Wrapper Layer**: `dtcw` scripts abstract environment complexity (local/docker/sdk)
- **Gradle Layer**: Main build orchestration in `build.gradle`
- **Core Module**: Standalone library in `/core` with task implementations
- **Script Plugins**: Modular Gradle scripts in `/scripts` directory

#### 2. Task Organization
All tasks are implemented in `/scripts/*.gradle` files and applied in `build.gradle`:
- Each script file represents a feature/capability (e.g., `exportJiraIssues.gradle`)
- Tasks use core library classes from `org.docToolchain.tasks.*`
- Configuration-driven via `docToolchainConfig.groovy` or `Config.groovy`

#### 3. Core Package Structure
```
org.docToolchain/
├── tasks/                         # Task base classes
│   ├── DocToolchainTask.groovy    # Base task
│   ├── AbstractConfluenceTask     # Confluence operations
│   ├── ExportJiraIssuesTask       # Jira export
│   └── [other task implementations]
├── atlassian/                     # External integrations
│   ├── jira/
│   │   ├── JiraService            # Main Jira service
│   │   ├── clients/               # REST clients
│   │   ├── converter/             # Format converters (AsciiDoc, Excel)
│   │   └── utils/                 # Date utilities, etc.
│   └── confluence/                # Confluence integration
├── configuration/                 # Config handling
└── scripts/                       # Utility scripts
```

#### 4. Configuration System
Two configuration files supported:
- **docToolchainConfig.groovy**: Primary config (Groovy DSL)
- **Config.groovy**: Alternative/legacy config
- **gradle.properties**: Build-time settings (Java version, memory, paths)

Key configuration sections:
- `inputPath`: Where to find source documents
- `outputPath`: Where to write generated docs
- `inputFiles`: List of files to process with output formats
- `imageDirs`: Image resource directories
- Task-specific configs: `jira`, `confluence`, `exportChangelog`, etc.

#### 5. Execution Environments
docToolchain supports three execution modes:
1. **local**: Installed in `$HOME/.doctoolchain`
2. **docker**: Container-based execution
3. **sdk**: SDKMAN-based installation

The `dtcw` wrapper automatically handles environment selection and setup.

## Key Dependencies and Technologies

### Primary Dependencies (libs.versions.toml)
- **Groovy 3.0.13**: Core language
- **AsciiDoctor 4.0.4**: Document processing
- **jBake 5.5.0**: Static site generation
- **Apache POI 5.3.0**: Excel integration
- **Jsoup 1.18.1**: HTML parsing
- **Apache HttpClient 5.3**: REST client for Jira/Confluence
- **Spock 2.3**: Testing framework
- **PlantUML/Graphviz**: Diagram generation

### Gradle Plugins
- `org.jbake.site`: Static site generation
- `org.aim42.htmlSanityCheck`: HTML validation
- `com.github.ben-manes.versions`: Dependency updates
- `org.openapi.generator`: OpenAPI integration
- `com.github.johnrengelman.shadow`: Fat JAR creation (core module)

## Testing Strategy

### Unit Tests (Spock)
- Located in `core/src/test/groovy/`
- Use Spock framework with Groovy
- **Expected behavior**: Core tests should always pass (66 tests, 3 skipped)
- Run with: `./gradlew core:test`

### Integration Tests (Project-level)
- Located in root test directory (Spock)
- **Expected behavior**: ~11 failures out of 53 tests is NORMAL
- Failures typically relate to missing external tools (Pandoc, Enterprise Architect)
- Run with: `./gradlew test`

### End-to-End Tests (BATS)
- Located in `/test/*.bats`
- Bash Automated Testing System
- Test wrapper functionality across environments
- Examples: `local_environment.bats`, `docker_environment.bats`, `sdk_installation.bats`
- Requires `bats-core` to be installed

## CI/CD Pipeline

### GitHub Actions Workflows (`.github/workflows/`)
- **default-build.yml**: Main CI (Java 17, Ubuntu, runs `.ci.sh`)
- **dtcw-tests.yaml**: Tests dtcw wrapper (bash)
- **dtcw.ps1-tests.yaml**: Tests PowerShell wrapper
- **codeql.yml**: Security analysis
- **sdkman.yml**: SDKMAN release automation

### CI Script (`.ci.sh`)
Execution phases:
1. **cleaning**: `./gradlew clean`
2. **dependency_info**: Check for updates
3. **unit_tests**: Run core and project tests
4. **create_doc**: Generate documentation (on ng/main-2.x branches with Java 17)
5. **publish_doc**: Publish to GitHub Pages (specific branches only)

### CI Dependencies
```bash
sudo apt-get install -y graphviz shellcheck pandoc powershell
```

## Important Timing Expectations

**CRITICAL**: Set long timeouts (600+ seconds) for builds and tests. Do NOT cancel long-running tasks.

- **First-time task listing**: ~95 seconds (downloads Gradle wrapper)
- **Core tests**: ~11 seconds (should always pass)
- **Full build with tests**: 3-5 minutes (11 failures expected)
- **generateHTML**: 13-19 seconds
- **generatePDF**: 19-20 seconds
- **Full CI pipeline**: 3-4 minutes

## Common Development Tasks

### Adding a New Export Task
1. Create new Gradle script in `/scripts/exportMyFeature.gradle`
2. Implement task class in `core/src/main/groovy/org/docToolchain/tasks/`
3. Add `apply from: 'scripts/exportMyFeature.gradle'` to `build.gradle`
4. Add configuration section to `docToolchainConfig.groovy`
5. Add tests in `core/src/test/groovy/`
6. Document in `/src/docs/015_tasks/`

### Modifying Core Functionality
1. Edit source in `core/src/main/groovy/org/docToolchain/`
2. Add/update unit tests in `core/src/test/groovy/`
3. Build core module: `./gradlew core:jar`
4. Test changes: `./gradlew core:test`
5. Integration test: `./dtcw local generateHTML`

### Updating Documentation
- Documentation sources: `src/docs/` (AsciiDoc format)
- Generate locally: `./dtcw local generateHTML`
- Output: `build/html5/` and `build/pdf/`
- Microsite: `./dtcw local generateSite` → `build/microsite/output/`

## Critical Configuration Files

### gradle.properties
- **dtc_version**: Current version (3.4.2)
- **org.gradle.jvmargs**: JVM settings (-Xmx2048m for PDF generation)
- **docDir**: Document directory (default: `.`)
- **inputPath**: Source path (default: `.`)

### docToolchainConfig.groovy
- **outputPath**: Where to write generated docs
- **inputPath**: Relative path to source documents
- **inputFiles**: Array of `[file: 'name.adoc', formats: ['html','pdf']]`
- **imageDirs**: Image resource directories
- **microsite**: Microsite configuration (jBake)
- Task-specific sections: `jira`, `confluence`, `exportChangelog`, etc.

### libs.versions.toml
- Centralized dependency version management
- Three sections: `[versions]`, `[plugins]`, `[libraries]`
- Referenced in build files as `libs.groovy.all`, `libs.plugins.jbake.site`, etc.

## Common Pitfalls and Solutions

### 1. "Task not found" Error
- **Cause**: Using `gradle` or `./gradlew` instead of `./dtcw`
- **Fix**: Always use `./dtcw [task]` for docToolchain tasks

### 2. Java Version Errors
- **Cause**: Wrong Java version (must be exactly Java 17)
- **Fix**: `./dtcw local install java` or set JAVA_HOME to Java 17

### 3. Test Failures
- **Normal**: ~11 test failures in main project (external tools missing)
- **Problem**: Core tests failing (should never fail)
- **Fix**: Check Java version, dependencies, or investigate specific failure

### 4. Memory Issues During PDF Generation
- **Already configured**: `gradle.properties` sets `-Xmx2048m`
- **If still occurring**: Increase in `org.gradle.jvmargs`

### 5. Docker Permission/Execution Errors
- Ensure Docker is running
- Check user permissions for Docker
- Verify `dtcw_docker.env` if using proxy

### 6. Build Output Locations
- HTML: `build/html5/` (direct generation) or `build/microsite/output/` (site)
- PDF: `build/pdf/`
- DocBook: `build/docbook/`

## Validation Checklist

Before committing changes:

```bash
# 1. Validate shell scripts
find . -name "*.sh" -exec shellcheck {} \;

# 2. Run core tests (must pass)
./gradlew core:test

# 3. Test basic functionality
./dtcw --version
./dtcw local generateHTML
./dtcw local generatePDF

# 4. Verify outputs exist
ls -la build/html5/manual_test_script.html
ls -la build/pdf/manual_test_script.pdf

# 5. Run full build (11 failures expected)
./gradlew clean build
```

## External Integration Points

### Atlassian (Jira & Confluence)
- **Jira**: Export issues via REST API, convert to AsciiDoc/Excel
- **Confluence**: Publish documentation directly, supports new editor
- Configuration via environment variables or config files
- Use API tokens, not passwords

### Enterprise Tools
- **Enterprise Architect**: Export diagrams via COM automation (Windows)
- **Visio**: Export diagrams to images
- **PowerPoint**: Extract slides as images
- **Excel**: Convert tables to AsciiDoc

### Diagram Tools
- **PlantUML**: Embedded diagram generation
- **Graphviz**: Graph visualization
- **Structurizr**: Architecture diagrams (DSL support)

### Version Control
- **Git**: Export changelog, contributor lists
- Supports git-based metadata extraction

## Special Notes

### Wrapper Script (dtcw) Architecture
The `dtcw` wrapper is the primary interface and handles:
- Environment detection (local/docker/sdk)
- Java version management
- Dependency downloading
- Task delegation
- Platform-specific execution (bash, PowerShell, batch)

### Distribution Creation
```bash
# Create release distribution
./gradlew prepareDist createDist
# Output: build/docToolchain-3.4.2.zip
```

### Module Isolation
When docToolchain is used as a submodule:
- Only tasks in groups 'docToolchain', 'Documentation', 'docToolchain helper' are enabled
- Prevents interference with parent project tasks
- Requires `docDir` to point to actual documentation (not `.`)

### Headless Mode Support
Recent addition supports non-interactive operation for CI/CD pipelines.

## Risk Radar Assessment

_Generated by `/risk-assess` on 2026-03-30_

### Module: docToolchain
| Dimension | Score | Level | Evidence |
|-----------|-------|-------|----------|
| Code Type | 2 | Business Logic | Doku-Generierung + REST-Client für Jira/Confluence (read/publish, kein eigener API-Server) |
| Language | 2 | Dynamically typed | 81 .groovy, 32 .gradle files |
| Deployment | 1 | Internal tool | Open-Source CLI, lokal oder in CI/CD, kein Server/Service |
| Data Sensitivity | 1 | Internal business data | Verarbeitet Dokumentation, Credentials nur durchgereicht |
| Blast Radius | 1 | Performance / DoS | Kaputte Doku oder fehlerhafte Confluence-Seiten, Quellen in Git |

**Tier: 2 — Extended Assurance** (determined by Code Type + Language = 2)

### Mitigations: docToolchain (Tier 2)

_Updated by `/risk-mitigate` on 2026-03-30_

#### Tier 1 — Automated Gates
| Measure | Status | Details |
|---------|--------|---------|
| Linter & Formatter | ✅ Present | shellcheck in CI, asciidoc-linter (pre-commit + CI) |
| Type Checking | ✅ Set up | CodeNarc static analysis for Groovy (`codenarc.yml`) |
| Pre-Commit Hooks | ✅ Set up | bash-syntax, shellcheck, asciidoc-linter, gitleaks (`.pre-commit-config.yaml`) |
| Dependency Check | ✅ Set up | Trivy vulnerability scan (`dependency-check.yml`), weekly + on PRs |
| CI Build & Tests | ✅ Present | GitHub Actions: `default-build.yml` (build + test), `dtcw-tests.yaml` (shellcheck + BATS), `dtcw.ps1-tests.yaml` |

#### Tier 2 — Extended Assurance
| Measure | Status | Details |
|---------|--------|---------|
| SAST | ✅ Present | CodeQL (`codeql.yml`) |
| AI Code Review | ✅ Set up | GitHub Copilot Code Review (enabled on default branch) |
| Property-Based Tests | ❌ Pending | Deferred until v4 Groovy scripts are production-ready |
| SonarQube Quality Gate | ✅ Present | SonarCloud as external PR check |
| Sampling Review (~20%) | ✅ Present | PR-based review process |

**Summary: 9/10 mitigations present, 1 pending (Property-Based Tests — deferred).**

## Semantic Contracts

These contracts define how we work in this repository. They override the workspace-level contracts where they differ.

*Source: https://llm-coding.github.io/Semantic-Anchors/#/contracts*

### Specification

When we talk about a "specification" or "spec", we mean:
- Persona Use Cases in Cockburn's Fully Dressed format (Primary Actor, Trigger, Main Success Scenario, Extensions, Postconditions) at User Goal level, with Business Rules (BR-IDs)
- System Use Cases for each technical interface (API endpoint, CLI command, event, file format): input/validation, processing, output/status codes, error responses
- Activity Diagrams for all flows (not just the happy path)
- Acceptance criteria in Gherkin format (Given/When/Then)
- Individual requirements in EARS syntax where applicable (When/While/If/Shall)
- Supplementary Specifications as needed: Entity Model, State Machines, Interface Contracts, Validation Rules

### Requirements Discovery

Clarify requirements using the Socratic Method:
- Ask at most 3 questions at a time, challenge assumptions
- Use MECE to ensure questions cover all areas without overlap
- Keep asking until you fully understand the requirements

Frame the scope before writing it down:
- Impact Mapping connects deliverables to business goals and actors — so you build what moves a goal, not just what was asked.
- User Story Mapping lays stories along the user's journey and exposes a coherent first slice.

Document the result as a PRD (problem, goals, personas, success criteria, scope).

### Architecture Documentation

Architecture documentation follows arc42. Scaffold the arc42 "with-help" template into the project's `src/docs/` via docToolchain `downloadTemplate` rather than restating chapter structure here — each chapter's help text is its structural spec, which the process fills and then replaces.

Every context, building-block and runtime chapter carries at least one diagram. Diagrams are PlantUML, not Mermaid; building blocks use C4 via PlantUML's bundled C4-PlantUML standard library — the `!include <C4/...>` stdlib form (angle brackets), never the remote `https://` URL and never vendored file copies. Not generic boxes.

Decisions are ADRs (Nygard) with a 3-point Pugh Matrix (-1/0/+1). When the rationale is unconfirmed, ADR Status is "Accepted (inferred)" and Pugh cells needing team judgment are marked `?` rather than guessed. Each ADR's Consequences name the risks the decision creates, referencing the Chapter 11 risk IDs (R-NNN); a decision that creates a risk not yet in Chapter 11 either adds it there or records the consequence as explicitly accepted without a tracked risk. Conversely, Chapter 8 concepts back-reference the ADR that decided them.

Cross-section traceability — arc42 templates do not enforce these, so the contract does:
- Every Chapter 1.2 quality goal maps to a named approach in Chapter 4.
- The external systems in Chapter 3 (context) and the Chapter 5 Level-1 building-block view are the same set — one system boundary in both.
- Every Chapter 5 building block appears in at least one Chapter 6 runtime scenario; Chapter 6 includes at least one error/recovery scenario, not only the happy path.
- Chapter 9 carries an in-document ADR index (ADR | Title | Status), even when the ADRs live in a separate register.
- Each Chapter 5 building block states responsibility, interface, and source location.

Chapter 1.2 lists only the top 3-5 quality goals — the ones that drive architecture decisions. Chapter 10 may elaborate further quality characteristics beyond those top goals; that is correct arc42, not a defect. The Chapter 10 quality tree marks each characteristic as either concretising a Chapter 1.2 top goal or as a derived quality requirement, and each Chapter 10 quality scenario cross-links back to the Chapter 1.2 goal it concretises (or is marked "derived"). Each Chapter 10 scenario is written in the six-part quality attribute scenario form (Source, Stimulus, Artifact, Environment, Response, Response Measure); the Response Measure carries a literal figure, so the requirement is testable rather than an adjective.

Chapter 11 separates Risks from Technical Debt into two subsections. Each Risk carries probability, impact, a derived priority, and a mitigation/action cross-referencing an existing mitigation in Chapter 8 or a quality scenario where one exists; risks are ordered by priority. Each Technical Debt item references the specific Chapter 5 building block it burdens.

### Crosscutting Concepts

arc42 leaves Chapter 8 open. We require five baseline crosscutting concepts, in this order:

- 8.1 Threat Model — STRIDE; threats get IDs (T-001…).
- 8.2 Security — every mitigation references the T-IDs it closes.
- 8.3 Test — testing pyramid; tests trace to Use Cases and Business Rules.
- 8.4 Observability — logs, metrics, traces, audit trails.
- 8.5 Error Handling — retry, circuit breaker, fallback, recovery.

Add further Chapter 8.x concepts (persistence, i18n, accessibility, configuration, performance) only when the system actually has that concern.

### Layer Boundaries

At every layer boundary:
- Expose only well-defined DTOs and contracts — never domain entities
- Use explicit mapping at every seam
- Apply Anti-Corruption Layers when integrating external systems
- Dependency direction points inward (DIP)

### Backlog Management

Create EPICs and User Stories as GitHub issues from the specification.
- User Stories follow INVEST criteria (Independent, Negotiable, Valuable, Estimable, Small, Testable)
- Prioritize with MoSCoW (Must/Should/Could/Won't)
- Mark dependencies between issues
- Groom the backlog regularly as the project evolves

### Vertical Slicing

Build the first increment as a walking skeleton: a deployable end-to-end slice that wires every architectural layer together and does almost nothing else.

Grow the system as thin vertical slices — each slice cuts through all layers and delivers one small piece of user value. Slices are tracer bullets: kept and refined, never thrown away.

When a technical unknown blocks a slice, run a spike solution first — a timeboxed, throwaway experiment that removes the risk. Spike code is discarded; only its lesson carries into the slice.

### Implement Next

For each issue:
- Create a feature branch for the EPIC
- Select next issue from backlog (respect dependencies)
- Analyze and document analysis as a comment on the issue
- Implement using TDD (London or Chicago School as appropriate)
- Each test references its Use Case ID for traceability
- Commit with Conventional Commits, reference issue number
- Check if spec or architecture docs need updating
- When EPIC is complete, create a Pull Request

### Refactoring

Refactoring targets are named code smells, not a vague urge to "clean up".

For any refactoring that does not complete in one step, use the Mikado Method: attempt the change, note what breaks, revert, and do the prerequisites first — never leave the build broken while you dig.

Refactoring commits change structure only. Behaviour changes go in separate commits, and the test suite stays green at every commit.

### Code Quality

Our code follows:
- SOLID principles
- DRY, KISS
- Ubiquitous Language from Domain-Driven Design (same terms in code as in the specification)

### Quality Review

Quality assurance follows three layers:
- Code review using Fagan Inspection (structured, systematic, with defined phases)
- Security review based on OWASP Top 10
- Architecture review using ATAM (scenario-based tradeoff analysis against quality goals)
- Use a different AI model or fresh session for reviews to avoid blind spots

### Docs-as-Code

Documentation follows Docs-as-Code according to Ralf D. Müller:
- AsciiDoc as format, PlantUML for inline diagrams, built by docToolchain
- Version-controlled, peer-reviewed, and built automatically
- Plain English according to Strunk & White (or Gutes Deutsch nach Wolf Schneider)
- Projects following this contract include the `dtcw` wrapper and `docToolchainConfig.groovy` so PlantUML / AsciiDoc actually render.

### Socratic Code Theory Recovery

Recover a program's "theory" (Naur 1985) from source code through recursive question refinement.

- Start with 5 root questions: Q1 Problem/Users, Q2 Specification, Q3 Architecture, Q4 Quality Goals, Q5 Risks.

- The second level of the tree is FIXED, not free. Every run emits exactly these nodes, in this order, even when a node's only leaf is [OPEN] or [ANSWERED: not applicable]:
  - Q1.1-Q1.6: product identity, primary users, channels, why-built, success metrics, segment priority
  - Q2.1-Q2.6: actors, use-case catalog, per-interface system specs, data/entity model, acceptance criteria, cross-cutting business rules
  - Q3.1-Q3.12: the twelve arc42 chapters, in arc42 order
  - Q4.1-Q4.8: the eight ISO/IEC 25010 characteristics; plus Q4.9: which characteristic has priority
  - Q5.1-Q5.5: technical debt, security risks, operational risks, dependency/supply-chain risks, scaling/performance risks

- Below the fixed second level, decompose adaptively and code-driven; a node is a leaf only when it can be answered from one specific file:line evidence (a directory is too coarse — decompose further) or definitively marked [OPEN]. Depth tracks code density: a small bounded context yields a shallow tree, a large one a deep tree, capped at four levels below a fixed node. Depth varies between runs — expected.

- Q-IDs are stable: Q3.7 is always Deployment View, in every run, so trees from different runs can be diffed node-by-node.

- Each leaf is [ANSWERED] (with file:line evidence) or [OPEN] (with Category, Ask role, and why it is unanswerable from code).

- Quality is not wholly team knowledge. Derive quality scenarios for the Q4 branch and arc42 Chapter 10 from measurable code behaviour — literal thresholds, timeouts, budgets, the threat catalogue and test concept from Q3.8 — as [ANSWERED] with file:line; never invent target numbers. Only the quality-goal ranking (Q4.9) is [OPEN]. arc42 Chapter 10 carries the derivable scenarios, never just an [OPEN] pointer. Chapter 1.2 names only the top 3-5 quality goals; Chapter 10 covers all eight characteristics — mark each Chapter 10 entry as concretising a Chapter 1.2 top goal or as derived.

- Open Questions are the handoff document: always emit one section per role (Product Owner, Architect, Developer, Domain Expert, Operations), even when a section is empty ("No open questions for this role").

- Two-phase workflow: Phase 1 builds the tree; the team answers the Open Questions; Phase 2 synthesizes documentation from the answered tree.

### Concise Response (TLDR)

Responses lead with the conclusion first (BLUF). Keep to essential points. No filler, no preamble. Use short sentences, active voice, and no unnecessary words (Strunk & White).

### Simple Explanation (ELI5)

Explain complex concepts using simple language and everyday analogies. When the explanation feels hard to write, that reveals gaps in understanding — study those areas first (Feynman Technique).

### Explaining and Teaching

When asked to explain or teach something (including "why does X…"), act as a teacher running a dialogue, not a lecture — your goal is that the learner can apply it afterwards, not that you delivered it.

Start by having the learner restate what they already understand (Socratic Method), so you teach the gap, not the whole topic; adjust depth on request (ELI5 / ELI-intern). Keep a short running checklist of what they must grasp — the problem and why it exists, the solution with its design decisions and edge cases, and why it matters — a Definition of Done for understanding, worked one item at a time; for a long or multi-session explanation, persist that checklist as a file so it survives context loss and can be resumed.

Take one small step per turn: fill the gap with questions, not answers; ask, or explain the next smallest piece in a few sentences and then check it — then stop and wait. Never stack several steps in one turn. Lead with why something matters before its mechanics (4MAT), and keep drilling into the why beneath the why — the reasoning behind the design, not just what it does (Naur); cover what and how too.

Check by quizzing, never "makes sense?" — open or multiple-choice questions; for multiple choice, vary which option is correct and don't reveal the answer until the learner has committed. The sharpest check is having them explain it back in their own words (Feynman Technique) or apply it to a fresh case; use a concrete artifact (an example, code, a trace) when it helps. React to the actual answer: if they've got it, advance; if not, give a short targeted hint and re-ask. "Understood" means they can use it on a new case, not recite it (Bloom's Apply, not recall) — don't move on, and don't end, until they've shown that.

Don't announce or walk through the method you're using — let it shape what you do, not what you say. Scale to the question: a small factual ask gets a one-line answer, and the learner can say "just tell me" anytime. If you're unsure of the topic, learn it before teaching.

### Writing Style

Writing follows Gutes Deutsch nach Wolf Schneider (or Plain English according to Strunk & White).

Additionally:
- Technical terms stay in English (LLM, Prompt, Token, Spec, etc.)
- Address the reader directly, use first person sparingly but deliberately
- Use analogies to human thinking to explain technical concepts
- One thought per paragraph (5-8 sentences is fine)
- Section headings are statements, not topic announcements
- First sentence says what the paragraph is about
- Show code and prompts, don't just claim things work
- Conclusions make a clear statement — never end with 'it remains exciting'

### TDD, Hamburg Style

Design-led TDD recipe by Ralf Westphal — close the requirements/logic gap before writing code, then test at service boundaries with minimal mocking. Use it when the problem is too complex for pure micro-step Red-Green-Refactor.

- **ACD cycle (Analyze → Design → Code)** precedes the test loop: first model the solution to close the gap between requirements and logic, only then code.
- **"Right from the start" philosophy** — implement correctly the first time so refactoring is a correction, not routine cleanup.
- **Service-level testing** — test behind the public API, independent of API technology.
- **Minimal mocking** — closer to *TDD, Chicago School* than *London School*.
- **IOSP (Integration Operation Segregation Principle)** — a function is either composition (Integration) or logic (Operation), never both; structural support for simple unit tests.
- **Deep Work over Small Steps** — accept that some problems can't be sliced into tiny green increments; stay red longer when the design demands it.

Composes: *TDD, London School*, *TDD, Chicago School*, *Red-Green-Refactor*, *IOSP*.
Sources: https://ralfw.de/hamburg-style-tdd/, https://ralfw.de/tdd-how-it-can-be-done-right/

### Strategic Architecture Analysis

Strategic architecture analysis combines four lenses, each for a different question. Reach for it when evaluating build-vs-buy, assessing architecture fitness for changing requirements, or running a strategic technology-radar review.

Map the value chain with Wardley Mapping to see how each component evolves — what is commodity, what is genesis, and where the strategic differentiation actually sits.

Classify each challenge with the Cynefin Framework — Clear, Complicated, Complex, or Chaotic — so the response fits the domain instead of forcing one playbook onto every problem.

When a decision has a wide solution space, lay the dimensions and their options out in a Morphological Box and combine them deliberately, rather than anchoring on the first design that comes to mind.

Evaluate the shortlisted architectures against the quality goals with ATAM, naming the sensitivity points, the tradeoff points, and the risks each option carries.

When the root cause of a problem stays unclear, drill down with the Five Whys before committing to a direction.
