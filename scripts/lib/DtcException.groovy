// v4: Actionable error carrier (ADR-8). Script-loaded (no package, no compilation)
// — parsed at runtime by task scripts, exactly like DtcConfig.
//
// Usage in a task script:
//   def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile
//   def gcl = new GroovyClassLoader(this.class.classLoader)
//   gcl.parseClass(new File(scriptDir, 'lib/DtcException.groovy'))
//   def DtcError = gcl.loadClass('DtcError')
//   def DtcConfigException = gcl.loadClass('DtcConfigException')
//   try {
//       ... task body; at a user-recoverable error: throw DtcConfigException.newInstance("do X") ...
//   } catch (Throwable t) {
//       System.exit(DtcError.report(t))
//   }

/**
 * A user-recoverable error carrying actionable guidance (what to DO, not just what
 * broke) and a differentiated exit code. Scripts throw this instead of calling
 * System.exit, so the host JVM is not killed mid-task — which keeps tasks testable
 * and usable in-process (ADR-5, LLM-native).
 */
class DtcException extends RuntimeException {
    final int exitCode
    DtcException(String guidance, int exitCode = 1, Throwable cause = null) {
        super(guidance, cause)
        this.exitCode = exitCode
    }
}

/** Configuration problem (missing/invalid config). Exit code 2. */
class DtcConfigException extends DtcException {
    DtcConfigException(String guidance, Throwable cause = null) { super(guidance, 2, cause) }
}

/** API / network problem (Confluence, Jira, downloads). Exit code 3. */
class DtcApiException extends DtcException {
    DtcApiException(String guidance, Throwable cause = null) { super(guidance, 3, cause) }
}

/**
 * The top-level error handler. Maps an exception to a differentiated exit code and
 * prints only the guidance (no stack trace) for user-recoverable errors; a genuine
 * bug (anything not a DtcException) prints a redacted stack trace and exit code 99.
 *
 * Exit codes: 0 success, 1 user-fixable, 2 config, 3 API/network, 99 bug.
 */
class DtcError {

    /**
     * Mask secret-shaped values so guidance and error messages never leak
     * credentials into console output or public CI logs (closes T-002 for
     * script-level error output). Conservative: only well-known shapes.
     */
    static String redact(String text) {
        if (text == null) return null
        String out = text
        // Authorization header values: "Bearer <token>" / "Basic <base64>"
        out = out.replaceAll(/(?i)\b(Bearer|Basic)\s+[A-Za-z0-9._=\/+-]{6,}/, '$1 ***')
        // key=value / key: value where the key name looks like a secret
        out = out.replaceAll(
            /(?i)\b(pass(?:word)?|token|credentials?|secret|api[_-]?key|bearer[_-]?token)\b(\s*[:=]\s*)\S+/,
            '$1$2***')
        return out
    }

    /**
     * Report a throwable and return the exit code the caller should exit with.
     * Does NOT call System.exit itself, so it stays testable.
     */
    static int report(Throwable t) {
        if (t instanceof DtcException) {
            String prefix = (t.exitCode == 2) ? '⚙' : (t.exitCode == 3) ? '🌐' : '→'
            System.err.println "${prefix} ${redact(t.message ?: 'error')}"
            return t.exitCode
        }
        System.err.println "BUG: ${redact(t.message ?: t.class.name)}"
        t.printStackTrace(System.err)
        return 99
    }
}
