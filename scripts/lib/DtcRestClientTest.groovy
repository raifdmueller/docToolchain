// v4: Self-contained test for DtcRestClient (runs without Spock/JUnit)
// Usage: groovy scripts/lib/DtcRestClientTest.groovy
//
// Tests class loading, construction, and error messages.
// Full HTTP tests require a running server (covered in S6/S7).

def cl = new GroovyClassLoader(this.class.classLoader)
cl.parseClass(new File('scripts/lib/DtcRestClient.groovy'))
def RestClientClass = cl.loadClass('DtcRestClient')
def ExceptionClass = cl.loadClass('DtcRequestFailedException')

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

println "=== DtcRestClient construction ==="
def client = RestClientClass.newInstance()
if (assertEq('client created', client != null, true)) passed++ else failed++
if (assertEq('has httpClientBuilder', client.httpClientBuilder != null, true)) passed++ else failed++

println "\n=== DtcRequestFailedException ==="
// Simulate a 401 response
def mockResponse = [getCode: { 401 }, getReasonPhrase: { 'Unauthorized' }] as org.apache.hc.core5.http.HttpResponse
def ex = ExceptionClass.newInstance(mockResponse, new Exception("test error"))
if (assertEq('exception message contains 401', ex.message.contains('401'), true)) passed++ else failed++
if (assertEq('exception suggests credentials', ex.message.contains('credentials'), true)) passed++ else failed++

// Simulate a 429 response
def mock429 = [getCode: { 429 }, getReasonPhrase: { 'Too Many Requests' }] as org.apache.hc.core5.http.HttpResponse
def ex429 = ExceptionClass.newInstance(mock429, new Exception("rate limited"))
if (assertEq('429 suggests rate limit', ex429.message.contains('rate limit'), true)) passed++ else failed++

// Simulate a 400 response
def mock400 = [getCode: { 400 }, getReasonPhrase: { 'Bad Request' }] as org.apache.hc.core5.http.HttpResponse
def ex400 = ExceptionClass.newInstance(mock400, new Exception("bad request"))
if (assertEq('400 suggests config', ex400.message.contains('config'), true)) passed++ else failed++

// Simulate a 500 response
def mock500 = [getCode: { 500 }, getReasonPhrase: { 'Internal Server Error' }] as org.apache.hc.core5.http.HttpResponse
def ex500 = ExceptionClass.newInstance(mock500, new Exception("server error"))
if (assertEq('500 suggests github issue', ex500.message.contains('github.com'), true)) passed++ else failed++

println "\n========================================="
println "Results: ${passed} passed, ${failed} failed"
if (failed > 0) {
    System.exit(1)
}
