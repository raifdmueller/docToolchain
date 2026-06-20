// v4: Script-based configuration loading (replaces core/configuration/ConfigBuilder + ConfigService)
// No package, no compilation — loaded via GroovyShell or 'evaluate' in task scripts.
//
// Usage in a task script:
//   def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile
//   def DtcConfig = new GroovyClassLoader(this.class.classLoader).parseClass(new File(scriptDir, 'lib/DtcConfig.groovy'))
//   def dtcConfig = DtcConfig.load(docDir, configFile)
//   def value = dtcConfig.get('confluence.api')
//   def tree = dtcConfig.getSubTree('confluence')

class DtcConfig {

    private final ConfigObject configObject

    private DtcConfig(ConfigObject configObject) {
        this.configObject = configObject
    }

    /**
     * Load and parse a docToolchainConfig.groovy file.
     * Injects docDir and mainConfigFile into the config.
     */
    static DtcConfig load(String docDir, String mainConfigFile) {
        def configFile = new File(docDir, mainConfigFile)
        if (!configFile.exists()) {
            if (!createMissingConfig(configFile, mainConfigFile)) {
                System.exit(1)
            }
        }
        ConfigObject config = new ConfigSlurper().parse(configFile.toURI().toURL())
        config.put('docDir', configFile.parent)
        config.put('mainConfigFile', configFile.name)
        return new DtcConfig(config)
    }

    // When the config is missing: in headless mode create it from the default
    // template automatically; interactively, offer to create it. Returns true
    // when a config now exists, false when the user declined.
    private static boolean createMissingConfig(File configFile, String mainConfigFile) {
        System.err.println "Configuration file not found: ${configFile.canonicalPath}"
        boolean create
        if (isHeadless()) {
            create = true
            System.err.println "Headless mode — creating a default '${mainConfigFile}'."
        } else {
            System.err.print "Create a default '${mainConfigFile}' now? [Y/n] "
            System.err.flush()
            def answer = readUserLine()?.trim()?.toLowerCase()
            create = (answer in [null, '', 'y', 'yes'])
        }
        if (!create) {
            System.err.println "No config created. Create a '${mainConfigFile}' in your project root,"
            System.err.println "e.g. with: ./dtcw4 downloadTemplate"
            return false
        }
        def template = defaultConfigTemplate()
        if (template?.exists()) {
            configFile.bytes = template.bytes
            System.err.println "Created '${configFile.canonicalPath}' from the default template."
        } else {
            configFile.text = MINIMAL_CONFIG
            System.err.println "Created a minimal '${configFile.canonicalPath}'."
        }
        System.err.println "Review it and set 'inputFiles' to your documents."
        return true
    }

    private static boolean isHeadless() {
        def v = System.getProperty('DTC_HEADLESS', System.getenv('DTC_HEADLESS') ?: 'false')
        return v == 'true'
    }

    private static String readUserLine() {
        def console = System.console()
        if (console != null) {
            return console.readLine()
        }
        try {
            return new BufferedReader(new InputStreamReader(System.in)).readLine()
        } catch (ignored) {
            return null
        }
    }

    // The canonical default config docToolchain ships, located relative to this
    // script: dtcHome/scripts/lib/DtcConfig.groovy -> dtcHome/template_config.
    private static File defaultConfigTemplate() {
        try {
            def src = DtcConfig.protectionDomain?.codeSource?.location
            if (src) {
                def dtcHome = new File(src.toURI()).parentFile?.parentFile?.parentFile
                def t = new File(dtcHome, 'template_config/Config.groovy')
                if (t.exists()) {
                    return t
                }
            }
        } catch (ignored) { }
        return null
    }

    private static final String MINIMAL_CONFIG = '''\
// Minimal docToolchain configuration.
// See template_config/Config.groovy in the docToolchain repo for all options.
outputPath = 'build'
inputPath  = 'src/docs'
inputFiles = [
    // [file: 'manual.adoc', formats: ['html', 'pdf']],
]
imageDirs  = []
'''

    /**
     * Get a single config property by path (e.g. 'confluence.api').
     * For first-level keys like 'confluence', returns the full subtree.
     * Returns null if not found.
     */
    Object get(String propertyPath) {
        def property = configObject.get(propertyPath)
        if (!property) {
            property = configObject.flatten().get(propertyPath)
        }
        return property
    }

    /**
     * Get all properties under a path as a flat map.
     * E.g. getSubTree('confluence') returns [api: '...', spaceKey: '...', 'proxy.host': '...']
     * Returns empty map if path not found.
     */
    Map getSubTree(String propertyPath) {
        return configObject.flatten().inject([:]) { result, key, value ->
            if (key.startsWith(propertyPath)) {
                result.put(key.replaceFirst("${propertyPath}.", ""), value)
            }
            return result
        }
    }

    /**
     * Access the raw ConfigObject (for backward compatibility or advanced use).
     */
    ConfigObject getRaw() {
        return configObject
    }
}
