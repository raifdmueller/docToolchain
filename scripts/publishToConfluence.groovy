#!/usr/bin/env groovy
// @task
// v4: Self-contained Groovy task for Confluence publishing (replaces publishToConfluence.gradle)
// Invoked by: java -cp <lib/*.jar> groovy.ui.GroovyMain scripts/publishToConfluence.groovy
//
// ADR-12 / #1626: this task has NO compiled core/ dependency. The Confluence
// publishing class graph (Asciidoc2ConfluenceTask and its transitive
// org.docToolchain.* closure) lives as SOURCE under scripts/lib/org/docToolchain/...
// It is compiled on demand at runtime by a GroovyClassLoader whose classpath
// is the scripts/lib source root: addClasspath(scriptDir/lib) lets Groovy
// resolve every inter-class reference in that graph from source. The only JARs
// on the JVM classpath are the third-party libs in lib/ (jsoup, httpclient,
// guava, poi) — the same ones GroovyClassLoader inherits from this.class.classLoader.

def docDir = System.getProperty('docDir', '.')
def configFile = System.getProperty('mainConfigFile', 'docToolchainConfig.groovy')

// scriptDir = the directory this script lives in (…/scripts). For a script run
// via groovy.ui.GroovyMain the codeSource location is the script file itself.
def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile

// Load configuration via DtcConfig (v4-S8)
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

try {
    // Load the Confluence publishing class graph from source. addClasspath points
    // the GroovyClassLoader at the scripts/lib source root; loadClass then triggers
    // on-demand compilation of Asciidoc2ConfluenceTask and every org.docToolchain.*
    // class it (transitively) references.
    def gcl = new GroovyClassLoader(this.class.classLoader)
    gcl.addClasspath("${scriptDir}/lib")
    def taskClass = gcl.loadClass('org.docToolchain.tasks.Asciidoc2ConfluenceTask')

    // Two-arg constructor (config, docDir). execute() dereferences docDir, so the
    // one-arg form used previously was a latent bug.
    def task = taskClass.getConstructor(ConfigObject, String).newInstance(config, docDir.toString())
    task.execute()
} catch (Throwable t) {
    // Unwrap reflection wrappers so the real cause (e.g. a network/auth error)
    // is what the user sees.
    def cause = t
    while (cause in java.lang.reflect.InvocationTargetException && cause.cause) {
        cause = cause.cause
    }
    System.err.println ""
    System.err.println "publishToConfluence failed: ${cause.class.simpleName}: ${cause.message}"
    System.exit(1)
}
