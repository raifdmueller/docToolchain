package org.docToolchain.configuration

import spock.lang.Specification

/**
 * Locks in the behaviour of the v4 config helper {@code scripts/lib/DtcConfig.groovy}.
 *
 * DtcConfig is a script-loaded class (no package, no compilation) — it is parsed at
 * runtime by every v4 task exactly as done here via GroovyClassLoader, so this spec
 * exercises it the same way production does. Regression guard for the get()/getSubTree()
 * hardening (PR #1638): falsy config values must be returned as themselves, and a
 * subtree lookup must not leak sibling keys that merely share a prefix.
 */
class DtcConfigSpec extends Specification {

    File tmpDir
    def dtcConfig

    private static File locateDtcConfigScript() {
        ['../scripts/lib/DtcConfig.groovy', 'scripts/lib/DtcConfig.groovy', '../../scripts/lib/DtcConfig.groovy']
            .collect { new File(it) }
            .find { it.exists() }
    }

    private def loadConfig(String text) {
        new File(tmpDir, 'docToolchainConfig.groovy').setText(text, 'UTF-8')
        def scriptFile = locateDtcConfigScript()
        assert scriptFile != null : 'could not locate scripts/lib/DtcConfig.groovy relative to the test working dir'
        def cls = new GroovyClassLoader(getClass().classLoader).parseClass(scriptFile)
        return cls.load(tmpDir.absolutePath, 'docToolchainConfig.groovy')
    }

    def setup() {
        tmpDir = File.createTempDir()
        dtcConfig = loadConfig('''
            failOnError = false
            port = 0
            emptyText = ''
            emptyList = []
            confluence {
                api = 'https://example.com'
                enabled = false
                retries = 0
            }
        ''')
    }

    def cleanup() {
        tmpDir?.deleteDir()
    }

    def "get() returns falsy leaf values as themselves, not as 'missing'"() {
        expect:
            dtcConfig.get(path) == expected

        where:
            path                 || expected
            'failOnError'        || false
            'port'               || 0
            'emptyText'          || ''
            'confluence.enabled' || false
            'confluence.retries' || 0
            'confluence.api'     || 'https://example.com'
    }

    def "get() returns an empty-list leaf as an empty list"() {
        expect:
            dtcConfig.get('emptyList') == []
    }

    def "get() returns null for a missing key"() {
        expect:
            dtcConfig.get('does.not.exist') == null
    }

    def "get() returns the full subtree for a first-level key"() {
        when:
            def subtree = dtcConfig.get('confluence')

        then:
            subtree instanceof Map
            subtree.api == 'https://example.com'
            subtree.enabled == false
            subtree.retries == 0
    }

    def "getSubTree() flattens a subtree and strips the dotted prefix"() {
        expect:
            dtcConfig.getSubTree('confluence') == [api: 'https://example.com', enabled: false, retries: 0]
    }

    def "getSubTree() does not leak sibling keys that share a prefix"() {
        given: 'two top-level keys, one a prefix of the other'
            def cfg = loadConfig('''
                confluence { api = 'a' }
                confluenceExtra { api = 'b' }
            ''')

        expect: 'only the exact-prefix subtree is returned'
            cfg.getSubTree('confluence') == [api: 'a']
    }
}
