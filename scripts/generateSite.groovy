#!/usr/bin/env groovy
// @task
// v4: Generate microsite using jBake (replaces generateSite.gradle + bake plugin)

import org.jbake.app.Oven
import groovy.io.FileType

def docDir = System.getProperty('docDir', '.')
def configFile = System.getProperty('mainConfigFile', 'docToolchainConfig.groovy')
def dtcHome = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile.parentFile

def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile
def DtcConfig = new GroovyClassLoader(this.class.classLoader).parseClass(new File(scriptDir, 'lib/DtcConfig.groovy'))
def dtcConfig = DtcConfig.load(docDir, configFile)
def config = dtcConfig.getRaw()

def inputPath = config.inputPath ?: 'src/docs'
def outputPath = config.outputPath ?: 'build'
def targetDir = new File(docDir, outputPath).absolutePath

println "docToolchain v4 — generateSite"
println "  inputPath:  ${new File(docDir, inputPath).absolutePath}"
println "  outputPath: ${targetDir}/microsite/output"
println ""

// --- Helper closures ---

def color = { c, text ->
    def colors = [black: 30, red: 31, green: 32, yellow: 33, blue: 34, magenta: 35, cyan: 36, white: 37]
    return new String((char) 27) + "[${colors[c]}m${text}" + new String((char) 27) + "[0m"
}

def copyDir = { File from, File to ->
    if (!from.exists()) return
    to.mkdirs()
    from.eachFileRecurse(FileType.FILES) { f ->
        def rel = from.toPath().relativize(f.toPath())
        def target = new File(to, rel.toString())
        target.parentFile.mkdirs()
        target.bytes = f.bytes
    }
}

// --- 1. Prepare temp site directory ---

def tmpDir = new File("${targetDir}/microsite/tmp")
tmpDir.mkdirs()
def siteDir = new File(tmpDir, "site")

// Copy internal theme from dtcHome/src/site
def internalTheme = new File(dtcHome, 'src/site')
if (internalTheme.exists()) {
    println "Copying internal theme from ${internalTheme.absolutePath}"
    copyDir(internalTheme, siteDir)
}

// Copy project theme if configured
if (config.microsite?.siteFolder) {
    def projectTheme = new File(new File(docDir, inputPath), config.microsite.siteFolder)
    if (projectTheme.exists()) {
        println "Copying project theme from ${projectTheme.absolutePath}"
        copyDir(projectTheme, siteDir)
    }
}

// --- 2. Copy docs ---

def docSrcDir = new File(docDir, inputPath)
def docDestDir = new File(siteDir, "doc")
println "Copying docs from ${docSrcDir.absolutePath}"
copyDir(docSrcDir, docDestDir)

// --- 3. Fix metadata headers (ported from v3 generateSite.gradle) ---

def parseAsciiDocAttribs = { origText, jbake ->
    def parseAttribs = true
    def text = ""
    def beforeToc = ""
    origText.eachLine { line ->
        if (parseAttribs && line.startsWith(":jbake")) {
            def parsed = (line - ":jbake-").split(": +", 2)
            if (parsed.length == 2) {
                jbake[parsed[0]] = parsed[1]
            }
        } else {
            if (line.startsWith("[")) parseAttribs = false
            text += line + "\n"
            if (line.startsWith(":toc")) beforeToc += line + "\n"
        }
    }
    return [text, beforeToc]
}

def parseOtherAttribs = { origText, jbake ->
    if (origText.contains('~~~~~~')) {
        def parseAttribs = true
        def text = ""
        origText.eachLine { line ->
            if (parseAttribs && line.contains("=")) {
                def parts = (line - "jbake-").split("=", 2)
                jbake[parts[0]] = parts[1]
            } else {
                if (line.startsWith("~~~~~~")) {
                    parseAttribs = false
                } else {
                    text += line + "\n"
                }
            }
        }
        return text
    }
    return origText
}

def renderHeader = { fileName, jbake ->
    def header = ''
    if (fileName.toLowerCase() ==~ '^.*(html|md)$') {
        jbake.each { key, value ->
            if (key == 'order') {
                header += "jbake-${key}=${(value ?: '1') as Integer}\n"
            } else if (key in ['type', 'status']) {
                header += "${key}=${value}\n"
            } else {
                header += "jbake-${key}=${value}\n"
            }
        }
        header += "~~~~~~\n\n"
    } else {
        jbake.each { key, value ->
            if (key == 'order') {
                header += ":jbake-${key}: ${(value ?: '1') as Integer}\n"
            } else {
                header += ":jbake-${key}: ${value}\n"
            }
        }
    }
    return header
}

println "Fixing metadata headers..."
docDestDir.traverse(type: FileType.FILES) { file ->
    if (file.name.toLowerCase() ==~ '^.*(ad|adoc|asciidoc|html|md)$') {
        if (file.name.startsWith("_") || file.name.startsWith(".")) return

        def origText = file.text
        def text = ""
        def jbake = [status: "published", order: -1, type: 'page_toc']
        if (file.name.toLowerCase() ==~ '^.*(md|html)$') jbake.type = 'page'
        def beforeToc = ""

        if (file.name.toLowerCase() ==~ '^.*(ad|adoc|asciidoc)$') {
            (text, beforeToc) = parseAsciiDocAttribs(origText, jbake)
        } else {
            text = parseOtherAttribs(origText, jbake)
        }

        def name = file.canonicalPath - (docDestDir.canonicalPath + File.separator)
        name = name.split("/")

        if (name.size() > 1) {
            if (!jbake.menu) {
                jbake.menu = name[0]
                if (jbake.menu ==~ /[0-9]+[-_].*/) {
                    jbake.menu = jbake.menu.split("[-_]", 2)[1]
                }
            }
            def docname = name[-1]
            if (docname ==~ /[0-9]+[-_].*/) {
                jbake.order = docname.split("[-_]", 2)[0]
                docname = docname.split("[-_]", 2)[1]
            }
            if (name.size() > 2) {
                if ((jbake.order as Integer) == 0) {
                    def secondLevel = name[1]
                    if (secondLevel ==~ /[0-9]+[-_].*/) {
                        jbake.order = secondLevel.split("[-_]", 2)[0]
                    }
                }
            }
            if (jbake.order == -1 && docname.startsWith('index')) {
                jbake.order = -987654321
                jbake.status = "published"
            }
            if (jbake.order == -1 && jbake.type == 'post') {
                jbake.order = 0
                try {
                    jbake.order = Date.parse("yyyy-MM-dd", jbake.date).time / 100000
                } catch (ignored) {}
                jbake.status = "published"
            }

            def leveloffset = 0
            if (file.name.toLowerCase() ==~ '^.*(ad|adoc|asciidoc)$') {
                text.eachLine { line ->
                    if (!jbake.title && line ==~ "^=+ .*") {
                        jbake.title = (line =~ "^=+ (.*)")[0][1]
                        def level = (line =~ "^(=+) .*")[0][1]
                        if (level == "=") leveloffset = 1
                    }
                }
            } else if (file.name.toLowerCase() ==~ '^.*(html)$') {
                text.eachLine { line ->
                    if (!jbake.title && line ==~ "^<h[1-9]>.*</h.*") {
                        jbake.title = (line =~ "^<h[1-9]>(.*)</h.*")[0][1]
                    }
                }
            } else {
                text.eachLine { line ->
                    if (!jbake.title && line ==~ "^#+ .*") {
                        jbake.title = (line =~ "^#+ (.*)")[0][1]
                    }
                }
            }
            if (!jbake.title) jbake.title = docname
            if (leveloffset == 1) {
                text = text.replaceAll("(?ms)^(=+) ", '$1= ')
            }

            def header = renderHeader(file.name, jbake)
            if (file.name.toLowerCase() ==~ '^.*(ad|adoc|asciidoc)$') {
                file.write(header + "\nifndef::dtc-magic-toc[]\n:dtc-magic-toc:\n${beforeToc}\n\n:toc: left\n\n++++\n<!-- endtoc -->\n++++\nendif::[]\n" + text, "utf-8")
            } else {
                file.write(header + "\n" + text, "utf-8")
            }
        }
    }
}

// --- 4. Run jBake ---

println "Running jBake..."
def outputDir = new File("${targetDir}/microsite/output")
outputDir.mkdirs()

// Write jbake.properties with site config
def jbakeProps = new File(siteDir, 'jbake.properties')
def propsText = new StringBuilder()

def micrositeContextPath = config.microsite?.contextPath ?: '/'
if (!micrositeContextPath.endsWith('/')) micrositeContextPath += '/'

propsText.append("asciidoctor.option.requires=asciidoctor-diagram\n")
def asciidoctorAttrs = [
    "sourceDir=${targetDir}",
    'source-highlighter=prettify@',
    "imagesoutDir=${targetDir}/microsite/output/images@",
    "imagesDir=${micrositeContextPath}images@",
    "targetDir=${targetDir}",
    "docDir=${docDir}",
    "projectRootDir=${new File(docDir).canonicalPath}@",
]
if (config.jbake?.asciidoctorAttributes) {
    asciidoctorAttrs.addAll(config.jbake.asciidoctorAttributes)
}
propsText.append("asciidoctor.attributes=${asciidoctorAttrs.join(',')}\n")

config.microsite?.each { key, value ->
    if (key != 'siteFolder' && key != 'additionalConverters' && key != 'customConvention') {
        propsText.append("site.${key}=${value ?: ''}\n")
    }
}

// Merge by key: our properties override existing ones, existing-only keys are kept
def mergedProps = new Properties()
if (jbakeProps.exists()) {
    jbakeProps.withInputStream { mergedProps.load(it) }
}
propsText.toString().eachLine { line ->
    if (line.contains('=')) {
        def parts = line.split('=', 2)
        mergedProps.setProperty(parts[0], parts[1])
    }
}
jbakeProps.withOutputStream { mergedProps.store(it, 'Generated by docToolchain v4 generateSite') }

def oven = new Oven(siteDir, outputDir, false)
oven.setupPaths()
oven.bake()

// --- 5. Copy images ---

println "Copying images..."
def imageDirs = config.imageDirs ?: ['images']
imageDirs.each { imageDir ->
    def imgSrc = new File(new File(docDir, inputPath), imageDir)
    if (imgSrc.exists()) {
        copyDir(imgSrc, new File(outputDir, 'images'))
    }
}

def resourceDirs = config.resourceDirs ?: []
resourceDirs.each { resource ->
    def resSrc = new File(new File(docDir, inputPath), resource.source)
    if (resSrc.exists()) {
        copyDir(resSrc, new File(outputDir, resource.target))
    }
}

println ""
println "Microsite generated at: ${outputDir.absolutePath}"
println "Open ${outputDir.absolutePath}/index.html in your browser."
