package org.docToolchain.scripts

import spock.lang.Shared
import spock.lang.Specification

/**
 * Locks in the v4 actionable-error carrier {@code scripts/lib/DtcException.groovy}
 * (ADR-8): differentiated exit codes and secret redaction. Loaded via
 * GroovyClassLoader exactly as task scripts load it at runtime.
 */
class DtcExceptionSpec extends Specification {

    @Shared Class dtcException
    @Shared Class dtcConfigException
    @Shared Class dtcApiException
    @Shared Class dtcError

    def setupSpec() {
        def scriptFile = ['../scripts/lib/DtcException.groovy', 'scripts/lib/DtcException.groovy']
            .collect { new File(it) }.find { it.exists() }
        assert scriptFile != null : 'could not locate scripts/lib/DtcException.groovy'
        def gcl = new GroovyClassLoader(getClass().classLoader)
        gcl.parseClass(scriptFile)
        dtcException = gcl.loadClass('DtcException')
        dtcConfigException = gcl.loadClass('DtcConfigException')
        dtcApiException = gcl.loadClass('DtcApiException')
        dtcError = gcl.loadClass('DtcError')
    }

    def "report() maps a generic DtcException to exit code 1"() {
        expect:
            dtcError.report(dtcException.newInstance('do X')) == 1
    }

    def "report() maps a config exception to exit code 2"() {
        expect:
            dtcError.report(dtcConfigException.newInstance('set Y')) == 2
    }

    def "report() maps an API exception to exit code 3"() {
        expect:
            dtcError.report(dtcApiException.newInstance('check token')) == 3
    }

    def "report() maps an unexpected throwable to exit code 99 (bug)"() {
        expect:
            dtcError.report(new IllegalStateException('boom')) == 99
    }

    def "redact() masks secret-shaped values"() {
        given:
            def out = dtcError.redact(input)

        expect:
            out.contains('***')
            !out.contains(secret)

        where:
            input                                 || secret
            'Authorization: Bearer abcDEF123456'  || 'abcDEF123456'
            'Basic bXk6c2VjcmV0Cg'                || 'bXk6c2VjcmV0Cg'
            'password=hunter2'                    || 'hunter2'
            'token: ABC123XYZ'                    || 'ABC123XYZ'
            'confluence.credentials = zzz999abc'  || 'zzz999abc'
            'apikey:DEADBEEF42'                   || 'DEADBEEF42'
    }

    def "redact() leaves non-secret text unchanged and handles null"() {
        expect:
            dtcError.redact('just a normal status message') == 'just a normal status message'
            dtcError.redact(null) == null
    }
}
