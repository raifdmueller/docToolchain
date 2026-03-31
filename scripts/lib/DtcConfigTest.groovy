// v4: Self-contained test for DtcConfig (runs without Spock/JUnit)
// Usage: groovy scripts/lib/DtcConfigTest.groovy

def DtcConfig = new GroovyClassLoader(this.class.classLoader).parseClass(new File('scripts/lib/DtcConfig.groovy'))

def passed = 0
def failed = 0

def assertEq(name, actual, expected) {
    if (actual == expected) {
        println "  PASS: ${name}"
        return true
    } else {
        println "  FAIL: ${name} — expected '${expected}', got '${actual}'"
        return false
    }
}

// Setup: create a temp config file
def tmpDir = File.createTempDir('dtcconfig-test', '')
def configFile = new File(tmpDir, 'testConfig.groovy')
configFile.text = """
outputPath = 'build/docs'
inputPath = 'src/docs'

confluence = [:]
confluence.with {
    api = 'https://my.confluence/rest/api/'
    spaceKey = 'asciidoc'
    proxy = [
        host: 'proxy.example.com',
        port: 8080,
        schema: 'https'
    ]
}
"""

println "=== DtcConfig.load() ==="
def config = DtcConfig.load(tmpDir.absolutePath, 'testConfig.groovy')
if (assertEq('config loaded', config != null, true)) passed++ else failed++

println "\n=== get() simple property ==="
if (assertEq('outputPath', config.get('outputPath'), 'build/docs')) passed++ else failed++
if (assertEq('inputPath', config.get('inputPath'), 'src/docs')) passed++ else failed++
if (assertEq('docDir injected', config.get('docDir'), tmpDir.absolutePath)) passed++ else failed++
if (assertEq('mainConfigFile injected', config.get('mainConfigFile'), 'testConfig.groovy')) passed++ else failed++

println "\n=== get() nested property ==="
if (assertEq('confluence.api', config.get('confluence.api'), 'https://my.confluence/rest/api/')) passed++ else failed++
if (assertEq('confluence.spaceKey', config.get('confluence.spaceKey'), 'asciidoc')) passed++ else failed++

println "\n=== get() first-level tree ==="
def confluence = config.get('confluence')
if (assertEq('confluence is map', confluence instanceof Map, true)) passed++ else failed++
if (assertEq('confluence.api from tree', confluence.api, 'https://my.confluence/rest/api/')) passed++ else failed++

println "\n=== get() nested tree returns null ==="
if (assertEq('confluence.proxy via get()', config.get('confluence.proxy'), null)) passed++ else failed++

println "\n=== get() non-existing ==="
if (assertEq('non-existing', config.get('non-sense'), null)) passed++ else failed++

println "\n=== getSubTree() ==="
def confTree = config.getSubTree('confluence')
if (assertEq('subtree size', confTree.size(), 5)) passed++ else failed++
if (assertEq('subtree api', confTree.api, 'https://my.confluence/rest/api/')) passed++ else failed++
if (assertEq('subtree proxy.host', confTree['proxy.host'], 'proxy.example.com')) passed++ else failed++
if (assertEq('subtree proxy.port', confTree['proxy.port'], 8080)) passed++ else failed++

println "\n=== getSubTree() second level ==="
def proxyTree = config.getSubTree('confluence.proxy')
if (assertEq('proxy subtree size', proxyTree.size(), 3)) passed++ else failed++
if (assertEq('proxy host', proxyTree.host, 'proxy.example.com')) passed++ else failed++

println "\n=== getSubTree() non-existing ==="
def empty = config.getSubTree('non-sense')
if (assertEq('empty subtree', empty, Collections.EMPTY_MAP)) passed++ else failed++

println "\n=== getRaw() ==="
if (assertEq('raw is ConfigObject', config.getRaw() instanceof ConfigObject, true)) passed++ else failed++

// Cleanup
tmpDir.deleteDir()

println "\n========================================="
println "Results: ${passed} passed, ${failed} failed"
if (failed > 0) {
    System.exit(1)
}
