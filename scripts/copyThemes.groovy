#!/usr/bin/env groovy
// @task
// v4: Copy a built-in theme (jBakeTheme or pdfTheme) into your project for customization

def docDir = System.getProperty('docDir', '.')
def configFile = System.getProperty('mainConfigFile', 'docToolchainConfig.groovy')
def isHeadless = System.getProperty('DTC_HEADLESS', System.getenv('DTC_HEADLESS') ?: 'false') == 'true'

def dtcHome = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile.parentFile
def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile
def DtcConfig = new GroovyClassLoader(this.class.classLoader).parseClass(new File(scriptDir, 'lib/DtcConfig.groovy'))
def dtcConfig = DtcConfig.load(docDir, configFile)
def config = dtcConfig.getRaw()
def inputPath = config.inputPath ?: 'src/docs'

def color = { c, text ->
    def colors = [black: 30, red: 31, green: 32, yellow: 33, blue: 34, magenta: 35, cyan: 36, white: 37]
    return new String((char) 27) + "[${colors[c]}m${text}" + new String((char) 27) + "[0m"
}

def copyDir
copyDir = { File src, File dst ->
    dst.mkdirs()
    src.listFiles()?.each { File child ->
        def target = new File(dst, child.name)
        if (child.isDirectory()) {
            copyDir(child, target)
        } else {
            target.parentFile.mkdirs()
            target.bytes = child.bytes
        }
    }
}

println "docToolchain v4 — copyThemes"

def themes = ['pdfTheme', 'jBakeTheme']

// Parse command-line args passed after the task name
def args = this.args as List
def whatArg = args.find { !it.startsWith('-') }

if (!whatArg || !(whatArg in themes)) {
    System.err.println "Usage: dtcw4 copyThemes <${themes.join('|')}>"
    System.err.println "  jBakeTheme  copy the microsite (jBake) theme into your project"
    System.err.println "  pdfTheme    copy the PDF theme into your project"
    System.exit(1)
}

switch (whatArg) {
    case 'pdfTheme':
        def source = new File(dtcHome, 'template_config/pdfTheme')
        if (!source.exists()) {
            System.err.println "Source theme not found: ${source.absolutePath}"
            System.exit(1)
        }
        def pdfThemeDir = config.pdfThemeDir ?: './src/docs/pdfTheme'
        def target = pdfThemeDir.startsWith('/') ? new File(pdfThemeDir) : new File(docDir, pdfThemeDir)
        copyDir(source, target)
        println "pdfTheme copied into ${target.canonicalPath}"
        if (!config.pdfThemeDir) {
            println color('green', "Hint: set pdfThemeDir = '${pdfThemeDir}' in ${configFile} so generatePDF picks up your theme.")
        }
        break

    case 'jBakeTheme':
        def source = new File(dtcHome, 'src/site')
        if (!source.exists()) {
            System.err.println "Source theme not found: ${source.absolutePath}"
            System.exit(1)
        }
        def siteFolder = config.microsite?.siteFolder ?: '../site'
        def target = new File(new File(docDir, inputPath), siteFolder)
        copyDir(source, target)
        println "jBakeTheme copied into ${target.canonicalPath}"
        if (!config.microsite?.siteFolder) {
            println color('green', "Hint: set microsite.siteFolder = '${siteFolder}' in ${configFile} so generateSite picks up your theme.")
        }
        break
}
