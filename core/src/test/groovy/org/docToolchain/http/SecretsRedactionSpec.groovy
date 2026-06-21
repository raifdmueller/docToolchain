package org.docToolchain.http

import spock.lang.Shared
import spock.lang.Specification

/**
 * Verifies the secret redaction applied to DtcRestClient's error output
 * (T-002 / R-008). Loads scripts/lib/DtcRestClient.groovy via GroovyClassLoader
 * exactly as it is loaded at runtime, and exercises the file-local Secrets helper.
 */
class SecretsRedactionSpec extends Specification {

    @Shared Class secrets

    def setupSpec() {
        def scriptFile = ['../scripts/lib/DtcRestClient.groovy', 'scripts/lib/DtcRestClient.groovy']
            .collect { new File(it) }.find { it.exists() }
        assert scriptFile != null : 'could not locate scripts/lib/DtcRestClient.groovy'
        def gcl = new GroovyClassLoader(getClass().classLoader)
        gcl.parseClass(scriptFile)
        secrets = gcl.loadClass('Secrets')
    }

    def "redact() masks secret-shaped values in error output"() {
        given:
            def out = secrets.redact(input)

        expect:
            out.contains('***')
            !out.contains(secret)

        where:
            input                                              || secret
            'GET https://user:s3cretpw@wiki.example.com/rest'  || 's3cretpw'
            'Authorization: Bearer abcDEF123456'               || 'abcDEF123456'
            'confluence.credentials = bXk6c2VjcmV0Cg'          || 'bXk6c2VjcmV0Cg'
            'token: ABC123XYZ'                                 || 'ABC123XYZ'
    }

    def "redact() passes through plain text and null unchanged"() {
        expect:
            secrets.redact('network timeout after 60s') == 'network timeout after 60s'
            secrets.redact(null) == null
    }
}
