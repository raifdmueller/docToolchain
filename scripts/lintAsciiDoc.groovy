#!/usr/bin/env groovy
// @task
// v4: Lint the project's AsciiDoc sources with the docToolchain AsciiDoc Linter

def docDir = System.getProperty('docDir', '.')
def configFile = System.getProperty('mainConfigFile', 'docToolchainConfig.groovy')
def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile

def DtcConfig = new GroovyClassLoader(this.class.classLoader).parseClass(new File(scriptDir, 'lib/DtcConfig.groovy'))
def config = DtcConfig.load(docDir, configFile).getRaw()
def inputPath = new File(docDir, config.inputPath ?: 'src/docs')

def color = { c, text ->
    def colors = [red: 31, green: 32, yellow: 33, cyan: 36]
    return new String((char) 27) + "[${colors[c]}m${text}" + new String((char) 27) + "[0m"
}

println "docToolchain v4 — lintAsciiDoc"

def lintCfg = config.lintAsciiDoc ?: [:]
def failOnError = lintCfg.failOnError ?: false
def format = lintCfg.format ?: 'console'

// --- 1. Which files to lint -------------------------------------------------
// Explicit globs in config.lintAsciiDoc.files win; otherwise lint the AsciiDoc
// entries from inputFiles (the documents the generators build).
def isAdoc = { String n -> n.toLowerCase() ==~ /.*\.(adoc|ad|asciidoc)$/ }
def files = []
if (lintCfg.files) {
    (lintCfg.files as List).each { pattern ->
        def f = new File(inputPath, pattern as String)
        if (f.exists()) files << f
    }
} else {
    (config.inputFiles ?: []).each { entry ->
        def f = new File(inputPath, entry.file as String)
        if (isAdoc(f.name) && f.exists()) files << f
    }
}
files = files.unique()

if (!files) {
    println color('yellow', "No AsciiDoc files to lint (configure inputFiles or lintAsciiDoc.files).")
    return
}

// --- 2. The linter must be installed ----------------------------------------
def linterPresent = {
    try {
        def p = ['asciidoc-linter', '--help'].execute()
        p.consumeProcessOutput()
        p.waitFor()
        return p.exitValue() == 0
    } catch (IOException ignored) {
        return false
    }
}()

if (!linterPresent) {
    System.err.println color('red', "The 'asciidoc-linter' command is not on your PATH.")
    System.err.println "Install it (a Python tool) with uv, then re-run './dtcw4 lintAsciiDoc':"
    System.err.println "  uv tool install git+https://github.com/docToolchain/asciidoc-linter"
    System.err.println "  (uv: https://docs.astral.sh/uv/ — or 'pip install' from the same repo)"
    System.exit(1)
}

// --- 3. Lint ----------------------------------------------------------------
def cmd = ['asciidoc-linter']
if (format) cmd += ['--format', format as String]
cmd += files*.absolutePath
println "Linting ${files.size()} file(s)..."

def proc = cmd.execute()
proc.waitForProcessOutput(System.out, System.err)
def code = proc.exitValue()

println ""
if (code == 0) {
    println color('green', "lintAsciiDoc: no problems found.")
} else if (failOnError) {
    System.err.println color('red', "lintAsciiDoc: problems found (failOnError=true).")
    System.exit(code)
} else {
    println color('yellow', "lintAsciiDoc: problems found (reported above). " +
        "Set lintAsciiDoc.failOnError = true to make the build fail.")
}
