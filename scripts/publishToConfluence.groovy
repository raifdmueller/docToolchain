#!/usr/bin/env groovy
// @task
// v4: Direct Groovy script for Confluence publishing (replaces publishToConfluence.gradle)
// Invoked by: java -cp <classpath> groovy.ui.GroovyMain scripts/publishToConfluence.groovy
//
// This script loads the project configuration, reads generated HTML,
// and publishes it to Confluence via the REST API.
// Core classes (ConfluenceService, HtmlTransformer) are on the classpath from lib/.

import org.docToolchain.tasks.Asciidoc2ConfluenceTask

def docDir = System.getProperty('docDir', '.')
def configFile = System.getProperty('mainConfigFile', 'docToolchainConfig.groovy')

// Load configuration via DtcConfig (v4-S8)
def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile
def DtcConfig = new GroovyClassLoader(this.class.classLoader).parseClass(new File(scriptDir, 'lib/DtcConfig.groovy'))
def dtcConfig = DtcConfig.load(docDir, configFile)
def config = dtcConfig.getRaw()

// Validate Confluence settings
if (!config.confluence?.api) {
    System.err.println "Confluence API URL not configured."
    System.err.println ""
    System.err.println "Add to your ${configFile}:"
    System.err.println "  confluence = ["
    System.err.println "    api: 'https://your-wiki.atlassian.net/wiki/rest/api',"
    System.err.println "    spaceKey: 'YOUR_SPACE',"
    System.err.println "  ]"
    System.exit(1)
}

println "docToolchain v4 — publishToConfluence"
println "  target: ${config.confluence.api}"
println ""

// Delegate to the existing task class (from core JAR in lib/)
def task = new Asciidoc2ConfluenceTask(config)
task.execute()
