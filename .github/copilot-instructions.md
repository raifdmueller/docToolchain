# docToolchain - Copilot Coding Agent Instructions

## Repository Overview

**docToolchain** is an open-source documentation generation tool implementing the "docs-as-code" approach for technical documentation, with special focus on software architecture documentation. Built around the arc42 template, it treats documentation with the same processes as source code.

### Key Facts
- **Language**: Groovy/Java with Gradle build system  
- **Version**: 3.4.2 (current 'ng' branch)
- **Build Tool**: Gradle 8.1.1 with Java 17
- **Primary Interface**: `dtcw` wrapper script (NOT direct Gradle commands)
- **Size**: ~200 files, multi-language project (~35MB)
- **Target Platforms**: Cross-platform (Linux, macOS, Windows)

## High-Level Architecture

### Core Components
- **`/core/`** - Main Java/Groovy source code for docToolchain functionality
- **`/src/docs/`** - Documentation source files (AsciiDoc format)
- **`/scripts/`** - Gradle task scripts for various export and generation features
- **`/test/`** - BATS integration tests and Spock unit tests
- **`dtcw`, `dtcw.ps1`, `dtcw.bat`** - Cross-platform wrapper scripts (PRIMARY INTERFACE)

### Key Configuration Files
- **`docToolchainConfig.groovy`** - Main project configuration
- **`gradle.properties`** - Build-time configuration and JVM settings
- **`build.gradle`** - Gradle build configuration with plugins
- **`libs.versions.toml`** - Dependency version catalog

### Execution Environments
1. **local** - Local installation in `$HOME/.doctoolchain`
2. **docker** - Container-based execution (recommended for CI/CD)
3. **sdk** - SDKMAN installation (requires SDKMAN to be installed)

## Build and Validation Procedures

### Prerequisites
- **Java 17** (REQUIRED - exactly this version)
- **Docker** (for container execution, recommended)
- **Gradle 8.1.1** (auto-downloaded via wrapper)

### Bootstrap/Installation Commands

**CRITICAL**: Always use `dtcw` wrapper, never direct Gradle commands.

```bash
# Check dtcw status and available environments
./dtcw --version

# Install Java 17 locally (if needed)
./dtcw local install java

# Install docToolchain locally
./dtcw local install doctoolchain

# List available tasks
./dtcw tasks --group doctoolchain
```

### Build Commands

```bash
# Build project (with expected test failures)
./gradlew build
# OR for clean build
./gradlew clean build

# Build core module only  
./gradlew core:jar

# Clean build artifacts
./gradlew clean

# Check dependency updates
./gradlew dependencyUpdates
```

**Expected Behavior**: 
- Build may show 11 test failures out of 53 total tests. This is normal and doesn't prevent successful builds.
- Full build takes 1-3 minutes on first run, faster on subsequent runs
- Gradle daemon may be forked with specific JVM settings for memory management

### Test Commands

```bash
# Run all tests (expect some failures related to external dependencies)
./gradlew test

# Run core module tests only (should pass)
./gradlew core:test

# Run integration tests (BATS)
# Note: Requires bats-core to be installed
cd test && bats *.bats
```

**Known Test Issues**: Tests may fail if external tools (pandoc, Enterprise Architect, etc.) are missing. This is expected behavior.

### Documentation Generation

```bash
# Generate HTML documentation
./dtcw generateHTML

# Generate PDF documentation  
./dtcw generatePDF

# Generate complete microsite
./dtcw generateSite

# Preview generated microsite locally
./dtcw previewSite
```

**Output Locations**: 
- HTML: `build/html5/`
- PDF: `build/pdf/` 
- Microsite: `build/microsite/output/`

### Validation Pipeline

The CI pipeline (`.ci.sh`) performs these steps:
1. **cleaning** - `./gradlew clean`
2. **dependency_info** - `./gradlew dependencyUpdates` 
3. **unit_tests** - `./gradlew core:test --info && ./gradlew test --info`
4. **create_doc** - Generate documentation for publishing
5. **publish_doc** - Publish to GitHub Pages (on specific branches)

### Environment Setup Details

**Critical Java Setup**: 
```bash
# Set JAVA_HOME if using local installation
export JAVA_HOME=/path/to/java17

# Required JVM args (already set in gradle.properties)
export GRADLE_OPTS="-Xmx2048m -Dfile.encoding=UTF-8"

# Check current Java version
java --version  # Must be exactly 17.x.x
```

**Version Information**:
- **Current docToolchain version**: 3.4.2
- **dtcw wrapper version**: 0.51  
- **Required Java version**: 17 (any patch level)
- **Gradle version**: 8.1.1 (auto-managed)

**Docker Environment Variables**:
```bash
# Create dtcw_docker.env file for proxy settings
cat > dtcw_docker.env << EOF
HTTP_PROXY=http://proxy.example.com:8080
HTTPS_PROXY=http://proxy.example.com:8080
NO_PROXY=localhost,127.0.0.1
EOF
```

## Common Tasks and Workflows

### Documentation Tasks
- `generateHTML` - Convert AsciiDoc to HTML5
- `generatePDF` - Convert AsciiDoc to PDF
- `generateDocbook` - Convert to DocBook format
- `generateDeck` - Create RevealJS presentations
- `generateSite` - Build complete microsite with jBake

### Export Tasks
- `exportJiraIssues` - Export Jira issues to AsciiDoc
- `exportEA` - Export Enterprise Architect diagrams
- `exportExcel` - Convert Excel files to AsciiDoc tables  
- `exportPPT` - Export PowerPoint slides
- `exportChangeLog` - Generate changelog from Git history
- `exportMarkdown` - Convert Markdown to AsciiDoc

### Publishing Tasks
- `publishToConfluence` - Publish directly to Confluence
- `verifyConfluenceApiAccess` - Test Confluence connectivity

### Utility Tasks
- `downloadTemplate` - Download arc42 template
- `collectIncludes` - Aggregate include files
- `createTask` - Generate new custom task

## Error Troubleshooting

### Common Build Failures

1. **"Task not found"**
   - **Cause**: Using `gradle` instead of `./dtcw`
   - **Fix**: Always use `./dtcw [task]`

2. **Java version errors**
   - **Cause**: Wrong Java version (not 17)
   - **Fix**: `./dtcw local install java` or set JAVA_HOME correctly

3. **Memory errors during PDF generation**
   - **Cause**: Insufficient heap space
   - **Fix**: Already configured in `gradle.properties` with `-Xmx2048m`

4. **Docker permission errors**
   - **Cause**: Docker not running or permission issues
   - **Fix**: Start Docker, check user permissions

5. **Test failures with external tools**
   - **Cause**: Missing pandoc, Enterprise Architect, etc.
   - **Fix**: These failures are expected if tools aren't installed

### Environment-Specific Issues

**Windows**: Use `dtcw.ps1` or `dtcw.bat` instead of `dtcw`
**macOS**: May need to install Docker Desktop
**Linux**: Install Docker via package manager

### Timeout Issues
- **Document generation**: May take 15-30 seconds for large docs
- **PDF generation**: Can take 20-60 seconds depending on content
- **Full build**: Allow 2-5 minutes for complete test suite
- **Docker pull**: First-time Docker execution may take 2-3 minutes to download image
- **CI pipeline**: Full `.ci.sh` execution can take 5-15 minutes

## GitHub Workflows and CI/CD

### Workflow Files (`.github/workflows/`)
- **`default-build.yml`** - Main CI pipeline (Java 17, Ubuntu)
- **`dtcw-tests.yaml`** - Tests for dtcw wrapper script
- **`codeql.yml`** - Security analysis
- **`sdkman.yml`** - SDKMAN release automation

### CI Dependencies
The CI environment installs:
```bash
sudo apt-get install -y graphviz shellcheck pandoc powershell
```

### Validation Steps
1. **ShellCheck** - Validates all executable shell scripts
2. **Java Setup** - Uses actions/setup-java@v4 with Temurin distribution
3. **Build Pipeline** - Runs `.ci.sh` with full test suite
4. **Artifact Upload** - Saves test reports on failure

## Repository Layout

### Root Directory Files
```
├── .ci.sh                 # CI/CD pipeline script
├── build.gradle           # Main Gradle build file  
├── docToolchainConfig.groovy # Project configuration
├── dtcw                   # Unix wrapper script
├── dtcw.ps1              # PowerShell wrapper script
├── dtcw.bat              # Windows batch wrapper
├── gradle.properties     # Gradle configuration
├── gradlew               # Gradle wrapper
├── LLM.md                # AI assistant instructions
├── README.adoc           # Main documentation
└── settings.gradle       # Gradle settings
```

### Source Structure
```
├── core/                 # Main source code
│   ├── src/main/groovy  # Core business logic
│   └── src/test/groovy  # Unit tests (Spock)
├── scripts/             # Gradle task implementations
├── src/docs/            # Documentation sources (AsciiDoc)
│   ├── 010_manual/      # User manual
│   ├── 015_tasks/       # Task documentation  
│   ├── 020_tutorial/    # Tutorials
│   └── images/          # Image assets
├── test/                # Integration tests (BATS)
└── template_config/     # Default templates
```

### Key Dependencies
- **AsciiDoctor** (2.5.13) - Document processing
- **JBake** - Static site generation
- **PlantUML** - Diagram generation  
- **Spock Framework** - Testing
- **Gradle Plugins**: jbake-site, versions, openapi-generator

## Validation and Quality Checks

### Pre-commit Checks
1. Run `./gradlew clean build` 
2. Check for shell script issues: `find . -name "*.sh" -exec shellcheck {} \;`
3. Test basic functionality: `./dtcw generateHTML`

### Code Quality Tools
- **ShellCheck** - Shell script linting (required for CI)
- **Gradle Versions Plugin** - Dependency update checking
- **HTML Sanity Check** - Generated HTML validation

### Manual Testing
```bash
# Test wrapper functionality
./dtcw --version

# Test document generation
./dtcw generateHTML
./dtcw generatePDF

# Verify outputs
ls -la build/html5/
ls -la build/pdf/
```

## Architecture Principles

### Core Design Patterns
1. **Wrapper Pattern**: `dtcw` abstracts environment complexity
2. **Plugin Architecture**: Gradle tasks in `/scripts/` directory
3. **Configuration-Driven**: Groovy DSL for flexible configuration
4. **Multi-Format Output**: Single source, multiple output formats

### Dependencies and Integration Points
- **External Tools**: PlantUML, Graphviz, Pandoc (optional)
- **Enterprise Integration**: Jira, Confluence, Enterprise Architect
- **Version Control**: Git-based changelog and contributor exports
- **Container Support**: Full Docker containerization available

---

## Agent Instructions

**ALWAYS use the `dtcw` wrapper script instead of direct Gradle commands.**

**Trust these instructions** - they are based on current repository analysis (September 2025). Only search for additional information if these instructions are incomplete or you encounter errors not covered here.

### Quick Command Reference
```bash
# Essential commands for agents
./dtcw --version              # Check version and environment status
./dtcw tasks --group doctoolchain  # List all available tasks (~20 tasks)
./dtcw generateHTML           # Basic document generation test
./gradlew clean build         # Full build with tests  
./gradlew core:test           # Core functionality tests (should pass)
```

**Key Success Factors:**
1. Use Java 17 exactly (other versions will fail)
2. Use `./dtcw` for all docToolchain operations  
3. Allow sufficient time for document generation (15-60 seconds)
4. Expect some test failures related to optional external tools
5. Check `build/` directory for generated outputs

**For new features or changes:**
1. Add tests in `/test/` (BATS) or `/core/src/test/` (Spock)
2. Update documentation in `/src/docs/`
3. Consider impact on all three execution environments (local, docker, sdk)
4. Test with `./dtcw generateHTML` to verify basic functionality