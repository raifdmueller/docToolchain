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
            System.err.println "Configuration file not found: ${configFile.canonicalPath}"
            System.err.println ""
            System.err.println "Create a 'docToolchainConfig.groovy' in your project root."
            System.err.println "You can download a template with: ./dtcw downloadTemplate"
            System.exit(1)
        }
        ConfigObject config = new ConfigSlurper().parse(configFile.toURI().toURL())
        config.put('docDir', configFile.parent)
        config.put('mainConfigFile', configFile.name)
        return new DtcConfig(config)
    }

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
