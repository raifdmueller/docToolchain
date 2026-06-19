#!/usr/bin/env groovy
// @task
// v4: Generate PDF from AsciiDoc sources using AsciidoctorJ PDF

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
def pdfOutputDir = new File(outputPath, 'pdf')
pdfOutputDir.mkdirs()

println "docToolchain v4 — generatePDF"
println "  inputPath:  ${inputPath.absolutePath}"
println "  outputPath: ${pdfOutputDir.absolutePath}"
println ""

def inputFiles = config.inputFiles ?: []
if (!inputFiles) {
    System.err.println "No inputFiles configured in ${configFile}."
    System.err.println "Add: inputFiles = [[file: 'arc42/arc42.adoc', formats: ['pdf']]]"
    System.exit(1)
}

def pdfFiles = inputFiles.findAll { entry ->
    def formats = entry.formats ?: ['html']
    formats.any { it.toLowerCase().contains('pdf') }
}

if (!pdfFiles) {
    println "No input files configured for PDF output."
    System.exit(0)
}

def imageDirs = config.imageDirs ?: ['images']
def asciidoctor = Asciidoctor.Factory.create()

// Register PDF converter by requiring the gem
asciidoctor.requireLibrary('asciidoctor-pdf')
asciidoctor.requireLibrary('asciidoctor-diagram')
println "AsciidoctorJ PDF ready (with diagram support)"

def failed = false

pdfFiles.each { entry ->
    def sourceFile = new File(inputPath, entry.file)
    if (!sourceFile.exists()) {
        System.err.println "Source file not found: ${sourceFile.absolutePath}"
        failed = true
        return
    }

    println "Processing: ${entry.file}"

    def pdfThemeDir = config.pdfThemeDir ?: null
    if (pdfThemeDir?.startsWith('.')) {
        pdfThemeDir = new File(docDir, pdfThemeDir).canonicalPath
    }
    def pdfTheme = config.pdfTheme ?: null
    def pdfFontsDir = config.pdfFontsDir ?: (pdfThemeDir ? "${pdfThemeDir}/fonts" : null)

    def attrsBuilder = Attributes.builder()
        .imagesDir(imageDirs[0])
        .sourceHighlighter(config.sourceHighlighter ?: 'rouge')
        .attribute('icons', config.icons ?: 'font')
        .attribute('doctype', 'book')

    if (pdfTheme) attrsBuilder.attribute('pdf-theme', pdfTheme)
    if (pdfThemeDir) attrsBuilder.attribute('pdf-themesdir', pdfThemeDir)
    if (pdfFontsDir) attrsBuilder.attribute('pdf-fontsdir', pdfFontsDir)

    def safeMode = SafeMode.valueOf((config.safeMode ?: 'UNSAFE').toUpperCase())

    def options = Options.builder()
        .backend('pdf')
        .safe(safeMode)
        .toDir(pdfOutputDir)
        .mkDirs(true)
        .baseDir(sourceFile.parentFile)
        .attributes(attrsBuilder.build())
        .build()

    try {
        asciidoctor.convertFile(sourceFile, options)
        def outputFile = new File(pdfOutputDir, sourceFile.name.replaceAll(/\.(adoc|ad|asciidoc)$/, '.pdf'))
        println "  -> ${outputFile.absolutePath} (${String.format('%.1f', outputFile.length() / 1024.0)} KB)"
    } catch (Exception e) {
        System.err.println "Failed: ${entry.file} — ${e.message}"
        failed = true
    }
}

asciidoctor.close()
println ""
if (failed) {
    System.err.println "PDF generation completed with errors."
    System.exit(1)
} else {
    println "PDF generation completed successfully."
}
