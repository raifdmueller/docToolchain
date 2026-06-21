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
        // Prefer an exact leaf match so falsy values (false, 0, '', []) are
        // returned as themselves rather than being mistaken for "missing"
        // by Groovy-truth. flatten() exposes dotted leaf keys like
        // 'confluence.api'; a first-level key like 'confluence' is not a leaf
        // and falls through to the subtree lookup below.
        def flat = configObject.flatten()
        if (flat.containsKey(propertyPath)) {
            return flat.get(propertyPath)
        }
        def nested = configObject.get(propertyPath)
        // ConfigObject auto-vivifies a missing key to an empty ConfigObject;
        // treat that as genuinely absent rather than returning an empty tree.
        if (nested instanceof ConfigObject && nested.isEmpty()) {
            return null
        }
        return nested
    }

    /**
     * Get all properties under a path as a flat map.
     * E.g. getSubTree('confluence') returns [api: '...', spaceKey: '...', 'proxy.host': '...']
     * Returns empty map if path not found.
     */
    Map getSubTree(String propertyPath) {
        // Match on a literal dotted prefix and strip it with substring, not a
        // regex: propertyPath may contain regex metacharacters, and requiring
        // the trailing '.' avoids matching sibling keys like 'confluenceX'.
        def prefix = propertyPath + "."
        return configObject.flatten().inject([:]) { result, key, value ->
            if (key.startsWith(prefix)) {
                result.put(key.substring(prefix.length()), value)
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
