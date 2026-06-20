// v4: Turn cryptic asciidoctor-diagram "Could not find the 'xxx' executable"
// failures into an actionable hint — how to install the tool, or how to render
// diagrams remotely via a Kroki server (no local tools needed).
//
// AsciidoctorJ delivers diagram failures (e.g.
//   "Could not find the 'dot' executable in PATH; ...")
// to registered LogHandlers. The raw message still goes to stderr — AsciidoctorJ
// does not let us selectively suppress a single record — so we collect the
// missing tools and print one consolidated hint block at the end.
//
// Usage:
//   def Hints = new GroovyClassLoader(this.class.classLoader)
//       .parseClass(new File(scriptDir, 'lib/DiagramToolHints.groovy'))
//   def hints = Hints.newInstance()
//   hints.register(asciidoctor)   // right after Asciidoctor.Factory.create()
//   ... convert ...
//   hints.printHints()            // before asciidoctor.close()

import org.asciidoctor.Asciidoctor
import org.asciidoctor.log.LogHandler
import org.asciidoctor.log.LogRecord

class DiagramToolHints {

    // executable name -> description + install commands
    private static final Map TOOLS = [
        dot  : [name: 'Graphviz',    purpose: 'GraphViz (and some PlantUML) diagrams',
                install: ['Linux (Debian/Ubuntu): sudo apt-get install -y graphviz',
                          'macOS:                 brew install graphviz']],
        mmdc : [name: 'Mermaid CLI', purpose: 'Mermaid diagrams',
                install: ['npm install -g @mermaid-js/mermaid-cli']],
        ditaa: [name: 'ditaa',       purpose: 'ditaa diagrams',
                install: ['Linux (Debian/Ubuntu): sudo apt-get install -y ditaa']],
    ]

    private final Set<String> missing = new LinkedHashSet<>()

    /** Register a log handler that records missing diagram executables. */
    void register(Asciidoctor asciidoctor) {
        asciidoctor.registerLogHandler({ LogRecord record ->
            def msg = record?.message
            if (msg) {
                def m = (msg =~ /Could not find the '([\w.+-]+)' executable/)
                if (m) {
                    missing << m[0][1]
                }
            }
        } as LogHandler)
    }

    boolean hasMissing() { !missing.isEmpty() }

    /** Print one consolidated, actionable hint for every missing tool. */
    void printHints() {
        if (missing.isEmpty()) {
            return
        }
        def out = System.err
        def line = '-' * 72
        out.println ""
        out.println line
        out.println "Some diagrams were not rendered - a local diagram tool is missing:"
        out.println ""
        missing.each { tool ->
            def info = TOOLS[tool]
            if (info) {
                out.println "  * '${tool}' (${info.name}) - needed for ${info.purpose}"
                info.install.each { out.println "        ${it}" }
            } else {
                out.println "  * '${tool}' executable not found in PATH"
            }
        }
        out.println ""
        out.println "  Or render diagrams remotely via a Kroki server (no local tools"
        out.println "  needed) by adding to docToolchainConfig.groovy:"
        out.println ""
        out.println "      asciidoctorAttributes = ["
        out.println "          'diagram-server-url' : 'https://kroki.io/',"
        out.println "          'diagram-server-type': 'kroki_io',"
        out.println "      ]"
        out.println ""
        out.println "  See the 'Kroki Configuration Guide' in the docToolchain tutorial"
        out.println "  (https://doctoolchain.org) or run your own server: https://kroki.io"
        out.println line
    }
}
