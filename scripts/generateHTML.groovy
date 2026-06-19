#!/usr/bin/env groovy
// @task
// v4: Generate HTML5 from AsciiDoc sources using AsciidoctorJ (no external Ruby needed)

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Options
import org.asciidoctor.Attributes
import org.asciidoctor.SafeMode

def docDir = System.getProperty('docDir', '.')
def configFile = System.getProperty('mainConfigFile', 'docToolchainConfig.groovy')

def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile
def DtcConfig = new GroovyClassLoader(this.class.classLoader).parseClass(new File(scriptDir, 'lib/DtcConfig.groovy'))
def dtcConfig = DtcConfig.load(docDir, configFile)
def config = dtcConfig.getRaw()

def inputPath = new File(docDir, config.inputPath ?: 'src/docs')
def outputPath = new File(docDir, config.outputPath ?: 'build')
def htmlOutputDir = new File(outputPath, 'html5')
htmlOutputDir.mkdirs()

println "docToolchain v4 — generateHTML"
println "  inputPath:  ${inputPath.absolutePath}"
println "  outputPath: ${htmlOutputDir.absolutePath}"
println ""

def inputFiles = config.inputFiles ?: []
if (!inputFiles) {
    System.err.println "No inputFiles configured in ${configFile}."
    System.err.println "Add: inputFiles = [[file: 'arc42/arc42.adoc', formats: ['html']]]"
    System.exit(1)
}

def htmlFiles = inputFiles.findAll { entry ->
    def formats = entry.formats ?: ['html']
    formats.any { it.toLowerCase().contains('html') }
}

if (!htmlFiles) {
    println "No input files configured for HTML output."
    System.exit(0)
}

def imageDirs = config.imageDirs ?: ['images']
def asciidoctor = Asciidoctor.Factory.create()
asciidoctor.requireLibrary('asciidoctor-diagram')
println "AsciidoctorJ ${Asciidoctor.class.package.implementationVersion ?: '2.5.x'} ready (with diagram support)"

def failed = false

htmlFiles.each { entry ->
    def sourceFile = new File(inputPath, entry.file)
    if (!sourceFile.exists()) {
        System.err.println "Source file not found: ${sourceFile.absolutePath}"
        failed = true
        return
    }

    println "Processing: ${entry.file}"

    def attrs = Attributes.builder()
        .imagesDir(imageDirs[0])
        .sourceHighlighter(config.sourceHighlighter ?: 'rouge')
        .tableOfContents(true)
        .attribute('toc', config.toc ?: 'left')
        .attribute('toclevels', (config.toclevels ?: 3) as String)
        .attribute('icons', config.icons ?: 'font')
        .attribute('doctype', 'book')
        .build()

    def safeMode = SafeMode.valueOf((config.safeMode ?: 'UNSAFE').toUpperCase())

    def options = Options.builder()
        .backend('html5')
        .safe(safeMode)
        .toDir(htmlOutputDir)
        .mkDirs(true)
        .baseDir(sourceFile.parentFile)
        .attributes(attrs)
        .build()

    try {
        asciidoctor.convertFile(sourceFile, options)
        def outputFile = new File(htmlOutputDir, sourceFile.name.replaceAll(/\.(adoc|ad|asciidoc)$/, '.html'))
        println "  -> ${outputFile.absolutePath} (${String.format('%.1f', outputFile.length() / 1024.0)} KB)"
    } catch (Exception e) {
        System.err.println "Failed: ${entry.file} — ${e.message}"
        failed = true
    }
}

asciidoctor.close()
println ""
if (failed) {
    System.err.println "HTML generation completed with errors."
    System.exit(1)
} else {
    println "HTML generation completed successfully."
}
