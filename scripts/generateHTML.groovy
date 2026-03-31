#!/usr/bin/env groovy
// @task
// v4: Direct Groovy script for HTML generation (replaces AsciiDocBasics.gradle)
// Invoked by: java -cp <classpath> groovy.ui.GroovyMain scripts/generateHTML.groovy
//
// This script loads the project configuration, invokes AsciiDoctor as an external
// tool (ADR-6), and generates HTML5 output from AsciiDoc sources.

def docDir = System.getProperty('docDir', '.')
def configFile = System.getProperty('mainConfigFile', 'docToolchainConfig.groovy')

// Load configuration via DtcConfig (v4-S8)
def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile
def DtcConfig = new GroovyClassLoader(this.class.classLoader).parseClass(new File(scriptDir, 'lib/DtcConfig.groovy'))
def dtcConfig = DtcConfig.load(docDir, configFile)
def config = dtcConfig.getRaw()

// Resolve paths
def inputPath = new File(docDir, config.inputPath ?: 'src/docs')
def outputPath = new File(docDir, config.outputPath ?: 'build/html5')
outputPath.mkdirs()

println "docToolchain v4 — generateHTML"
println "  inputPath:  ${inputPath.absolutePath}"
println "  outputPath: ${outputPath.absolutePath}"
println "  configFile: ${configPath.absolutePath}"
println ""

// Determine input files
def inputFiles = config.inputFiles ?: []
if (!inputFiles) {
    System.err.println "No inputFiles configured in ${configFile}."
    System.err.println ""
    System.err.println "Add an inputFiles entry, for example:"
    System.err.println "  inputFiles = [[file: 'arc42/arc42.adoc', formats: ['html']]]"
    System.exit(1)
}

// Check AsciiDoctor availability
def asciidoctorCmd = ['asciidoctor', '--version']
try {
    def proc = asciidoctorCmd.execute()
    proc.waitFor()
    if (proc.exitValue() != 0) {
        throw new Exception("asciidoctor exited with ${proc.exitValue()}")
    }
    def version = proc.text.readLines().first()
    println "Using ${version}"
} catch (Exception e) {
    System.err.println "AsciiDoctor not found on PATH."
    System.err.println ""
    System.err.println "Install AsciiDoctor with one of:"
    System.err.println "  gem install asciidoctor"
    System.err.println "  brew install asciidoctor    (macOS)"
    System.err.println "  apt install asciidoctor     (Debian/Ubuntu)"
    System.err.println ""
    System.err.println "Or use Docker: ./dtcw docker generateHTML"
    System.exit(1)
}

// Process each input file that includes 'html' format
def htmlFiles = inputFiles.findAll { entry ->
    def formats = entry.formats ?: ['html']
    formats.any { it.toLowerCase().contains('html') }
}

if (!htmlFiles) {
    println "No input files configured for HTML output. Nothing to do."
    System.exit(0)
}

def imageDirs = config.imageDirs ?: ['images']
def failed = false

htmlFiles.each { entry ->
    def sourceFile = new File(inputPath, entry.file)
    if (!sourceFile.exists()) {
        System.err.println "Source file not found: ${sourceFile.absolutePath}"
        System.err.println "Check the 'file' path in your inputFiles configuration."
        failed = true
        return
    }

    println "Processing: ${entry.file}"

    // Build AsciiDoctor command — read options from config with sensible defaults
    def highlighter = config.sourceHighlighter ?: 'rouge'
    def toc = config.toc ?: 'left'
    def toclevels = config.toclevels ?: '3'
    def icons = config.icons ?: 'font'
    def requires = config.asciidoctorRequires ?: ['asciidoctor-diagram']

    def cmd = ['asciidoctor']
    cmd += ['-b', 'html5']
    cmd += ['-D', outputPath.absolutePath]
    cmd += ['-a', "imagesdir=${imageDirs[0]}"]
    cmd += ['-a', "source-highlighter=${highlighter}"]
    cmd += ['-a', "toc=${toc}"]
    cmd += ['-a', "toclevels=${toclevels}"]
    cmd += ['-a', "icons=${icons}"]
    requires.each { req -> cmd += ['-r', req] }
    cmd += [sourceFile.absolutePath]

    println "  ${cmd.join(' ')}"

    def proc = cmd.execute(null, new File(docDir))
    proc.consumeProcessOutput(System.out, System.err)
    proc.waitFor()

    if (proc.exitValue() != 0) {
        System.err.println "AsciiDoctor failed for ${entry.file} (exit code: ${proc.exitValue()})"
        failed = true
    } else {
        def outputFile = new File(outputPath, sourceFile.name.replaceAll(/\.adoc$/, '.html'))
        println "  -> ${outputFile.absolutePath}"
    }
}

println ""
if (failed) {
    System.err.println "HTML generation completed with errors."
    System.exit(1)
} else {
    println "HTML generation completed successfully."
}
