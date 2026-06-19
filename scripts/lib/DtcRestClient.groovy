// v4: Script-based HTTP client (replaces core/http/BasicRestClient + RequestFailedException)
// No package, no compilation — loaded via GroovyClassLoader in task scripts.
// Requires Apache HttpClient 5.x JARs on classpath (from lib/).
//
// Usage in a task script:
//   def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile
//   def cl = new GroovyClassLoader(this.class.classLoader)
//   cl.parseClass(new File(scriptDir, 'lib/DtcRestClient.groovy'))
//   def client = new DtcRestClient()
//   // ... configure and use

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.core5.http.ClassicHttpRequest
import org.apache.hc.core5.http.Header
import org.apache.hc.core5.http.HttpHeaders
import org.apache.hc.core5.http.HttpHost
import org.apache.hc.core5.http.HttpResponse
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import org.apache.hc.core5.net.URIBuilder

/**
 * Exception for failed HTTP requests with actionable error messages (QS-17).
 */
class DtcRequestFailedException extends RuntimeException {

    DtcRequestFailedException(HttpResponse response, Exception reason) {
        super(buildMessage(response, reason), reason)
    }

    private static String buildMessage(HttpResponse response, Exception reason) {
        String responseLog = response != null ? "${response.code} ${response.reasonPhrase}" : "<none>"
        String reasonLog = reason != null ? reason.message : "<none>"
        String possibleSolution

        switch (response.code) {
            case 401:
                possibleSolution = "please check your credentials in config file or passed parameters"
                break
            case 400:
                possibleSolution = "please check your config file or passed parameters"
                break
            case 429:
                possibleSolution = "please check if you need to decrease the rate limit in your config file"
                break
            default:
                possibleSolution = "please check your config. If you are sure that everything is correct, " +
                    "please open an issue at https://github.com/docToolchain/docToolchain/issues"
        }

        return "something went wrong - request failed" + " (" +
            "\nresponse: ${responseLog}, " +
            "\nreason: ${reasonLog}, " +
            "\npossible solution: ${possibleSolution})"
    }
}

/**
 * Base HTTP client using Apache HttpClient 5.x.
 * Sets User-Agent and Host headers automatically.
 */
class DtcRestClient {

    protected HttpClientBuilder httpClientBuilder

    DtcRestClient() {
        this.httpClientBuilder = HttpClientBuilder.create()
        httpClientBuilder.addRequestInterceptorFirst { request, entityDetails, context ->
            request.setHeader(HttpHeaders.USER_AGENT, "docToolchain_v4")
            Header hostHeader = request.getHeader(HttpHeaders.HOST)
            if (hostHeader == null) {
                String host = new URIBuilder(request.uri.toString()).host
                request.setHeader(HttpHeaders.HOST, host)
            }
        }
    }

    /**
     * Execute an HTTP request and return the response body.
     * Returns Optional.empty() if response is null.
     */
    def doRequest(HttpHost targetHost, ClassicHttpRequest httpRequest, HttpClientResponseHandler<String> responseHandler) {
        try (CloseableHttpClient httpClient = httpClientBuilder.build()) {
            return Optional.ofNullable(httpClient.execute(targetHost, httpRequest, responseHandler))
        } catch (IOException e) {
            System.err.println("HTTP request failed: ${httpRequest.method} ${httpRequest.uri}")
            System.err.println("Target: ${targetHost.toURI()}")
            System.err.println("Reason: ${e.message}")
            throw new RuntimeException(e)
        }
    }

    protected getHttpClientBuilder() {
        return httpClientBuilder
    }
}
