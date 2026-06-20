#!/usr/bin/env groovy
// @task
// v4: Download and install documentation templates (arc42, req42, or custom)

import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipInputStream

def docDir = System.getProperty('docDir', '.')
def configFile = System.getProperty('mainConfigFile', 'docToolchainConfig.groovy')
def isHeadless = System.getProperty('DTC_HEADLESS', System.getenv('DTC_HEADLESS') ?: 'false') == 'true'

def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile
def DtcConfig = new GroovyClassLoader(this.class.classLoader).parseClass(new File(scriptDir, 'lib/DtcConfig.groovy'))
def dtcConfig = DtcConfig.load(docDir, configFile)
def config = dtcConfig.getRaw()
def inputPath = config.inputPath ?: 'src/docs'

def color = { c, text ->
    def colors = [black: 30, red: 31, green: 32, yellow: 33, blue: 34, magenta: 35, cyan: 36, white: 37]
    return new String((char) 27) + "[${colors[c]}m${text}" + new String((char) 27) + "[0m"
}

println "docToolchain v4 — downloadTemplate"

def lang = 'EN'
def help = 'plain'
def templates = [
    arc42: { -> "https://github.com/arc42/arc42-template/raw/master/dist/arc42-template-${lang}-${help}-asciidoc.zip" },
    req42: { -> "https://github.com/Hruschka/req42-framework/raw/main/dist/req42-framework-${lang}-${help}-asciidoc.zip" },
]
def languages = [arc42: ['CZ', 'DE', 'EN', 'ES', 'FR', 'IT', 'NL', 'UA'], req42: ['EN', 'DE']]

// Add custom templates from environment
for (int i = 1; i < 20; i++) {
    def tmpl = System.getenv("DTC_TEMPLATE${i}") ?: ""
    if (tmpl) {
        def name = tmpl.replaceAll("^.*/", "").replaceAll(/\.zip$/, "")
        templates[name] = { -> tmpl }
    }
}

// Parse command-line args passed after the task name
def args = this.args as List
def templateArg = args.find { !it.startsWith('-') }
def langArg = args.find { it.startsWith('--lang=') }?.split('=', 2)?.getAt(1)
def helpArg = args.find { it.startsWith('--help=') }?.split('=', 2)?.getAt(1)

def template
if (templateArg) {
    template = templateArg
} else if (isHeadless) {
    template = 'arc42'
    println "${color('green', "Headless mode: using default template 'arc42'.")}"
} else {
    def keys = templates.keySet() as List
    def reader = System.console() ?: new BufferedReader(new InputStreamReader(System.in))
    println "${color('green', 'Which template do you want to install?')}"
    keys.eachWithIndex { name, i -> println "  ${i + 1}) ${name}" }
    print "${color('green', "Choice [1-${keys.size()}] or name (default: 1 = ${keys[0]}): ")}"
    def input = (reader instanceof Console ? reader.readLine() : reader.readLine())?.trim()
    if (!input) {
        template = keys[0]
    } else if (input.isInteger() && (input as int) in (1..keys.size())) {
        template = keys[(input as int) - 1]
    } else {
        template = input
    }
}
if (!templates.containsKey(template)) {
    System.err.println "Unknown template '${template}'. Available: ${templates.keySet().join(', ')}"
    System.exit(1)
}

if (template in ['arc42', 'req42']) {
    println "For more information see https://${template == 'arc42' ? 'arc42.org' : 'req42.de/en'}"

    if (langArg) {
        lang = langArg.toUpperCase()
    } else if (isHeadless) {
        lang = 'EN'
    } else {
        def reader = System.console() ?: new BufferedReader(new InputStreamReader(System.in))
        print "${color('green', "Language [${languages[template].join(',')}] (default: EN): ")}"
        def input = reader instanceof Console ? reader.readLine() : reader.readLine()
        lang = input?.trim()?.toUpperCase() ?: 'EN'
    }

    if (helpArg) {
        help = helpArg
    } else if (isHeadless) {
        help = 'plain'
    } else {
        def reader = System.console() ?: new BufferedReader(new InputStreamReader(System.in))
        print "${color('green', "Help variant [withhelp,plain] (default: plain): ")}"
        def input = reader instanceof Console ? reader.readLine() : reader.readLine()
        help = input?.trim() ?: 'plain'
    }
}

println "${color('green', "Installing ${template} template (${lang}, ${help})...")}"

def url = templates[template].call()
def outputDir = new File(docDir, "${inputPath}/${template}")
outputDir.mkdirs()

def zipFile = new File(outputDir, 'template.zip')
println "Downloading ${url}"
def conn = new URL(url).openConnection()
conn.connectTimeout = 15000
conn.readTimeout = 60000
conn.inputStream.withCloseable { zipFile.bytes = it.bytes }

// Unzip
new ZipInputStream(new FileInputStream(zipFile)).withCloseable { zis ->
    def entry
    def canonicalDest = outputDir.canonicalPath + File.separator
    while ((entry = zis.nextEntry) != null) {
        def target = new File(outputDir, entry.name)
        if (!target.canonicalPath.startsWith(canonicalDest)) {
            throw new SecurityException("Zip entry '${entry.name}' would escape target directory")
        }
        if (entry.isDirectory()) {
            target.mkdirs()
        } else {
            target.parentFile.mkdirs()
            target.bytes = zis.readAllBytes()
        }
    }
}
zipFile.delete()
println "${template} template unpacked into ${outputDir}"

// Reorganize: move images, rename src -> chapters, add jbake headers
new File(outputDir, '../images/.').mkdirs()
def imagesDir = new File(outputDir, 'images')
if (imagesDir.exists()) {
    imagesDir.eachFile { f -> f.renameTo(new File(outputDir, "../images/${f.name}")) }
    imagesDir.deleteDir()
}

def srcDir = new File(outputDir, 'src')
def chaptersDir = new File(outputDir, 'chapters')
if (srcDir.exists()) {
    srcDir.renameTo(chaptersDir)
}

if (chaptersDir.exists()) {
    chaptersDir.eachFileRecurse { file ->
        if (file.name.endsWith('.adoc') && file.name ==~ /[0-9]+_.*/) {
            def text = file.getText('utf-8')
            def title = ""
            text.eachLine { line ->
                if (title == "" && line.startsWith('=')) {
                    title = line.split("[ \t]+", 2)[1]
                }
            }
            text = text.replaceAll(/ifndef::imagesdir\[:imagesdir: \.\.\/images]/, "")
            text = """\
:jbake-title: ${title}
:jbake-type: page_toc
:jbake-status: published
:jbake-menu: ${template}
:jbake-order: ${file.name.split("_")[0] as Integer}
:filename: ${file.canonicalPath - outputDir.canonicalPath}
ifndef::imagesdir[:imagesdir: ../../images]

:toc:

""" + text
            file.write(text, 'utf-8')
        }
    }
}

// Rename main template file
def mainSource = template == "arc42" ? "${template}-template.adoc" : "${template}-framework.adoc"
def mainFile = new File(outputDir, mainSource)
def targetFile = new File(outputDir, "${template}.adoc")
if (mainFile.exists()) {
    def mainText = ":imagesdir: ../images\n:jbake-menu: -\n" + mainFile.text.replaceAll('src/', 'chapters/')
    targetFile.write(mainText, 'utf-8')
    if (mainFile != targetFile) mainFile.delete()
}

// Write asciidoctorconfig for editor preview
new File(outputDir, 'chapters/.asciidoctorconfig.adoc').write(':imagesdir: ../../images\n\n', 'utf-8')
new File(outputDir, '.asciidoctorconfig.adoc').write(':imagesdir: ../images\n\n', 'utf-8')

// Update project config file
def configFileObj = new File(docDir, configFile)
if (configFileObj.exists()) {
    def configText = configFileObj.text
    configText = configText
        .replaceAll('[, \\t\\r\\n]+/[*]{2} inputFiles [*]{2}/',
            ",\n\t[file: '${template}/${template}.adoc', formats: ['html','pdf']],\n\t/** inputFiles **/")
        .replaceAll('/[*]{2} imageDirs [*]{2}/',
            "'images/.',\n\t/** imageDirs **/")
        .replaceAll('[, \\t\\r\\n]+/[*]{2} imageDirs [*]{2}/',
            ",\n\t'images/.',\n\t/** imageDirs **/")
        .replaceAll("\\[,", "[")
    configFileObj.write(configText, 'utf-8')
    println "Updated ${configFile}"
}

println ""
println "Use 'generateHTML', 'generatePDF' or 'generateSite' to convert the template."
