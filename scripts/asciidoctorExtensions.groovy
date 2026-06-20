// docToolchain AsciidoctorJ extensions, kept in a separate file because
// Gradle 8.4+ instrumentation rejects inline closures in the build script.
// Loaded from AsciiDocBasics.gradle via: docExtensions(file(...).text)
//
// Note: the build-time docToolchain `config` is not available here, so the
// Jira base URL is passed in as the document attribute 'jira-base-url'.

inline_macro(name: 'jira') { parent, target, attributes ->
    def jiraBaseUrl = parent.document.getAttribute('jira-base-url')
    def options = [
        'type'  : ':link',
        'target': "${jiraBaseUrl}/browse/${target}".toString(),
        'id'    : "${target}".toString()
    ]
    if (!jiraBaseUrl) {
        println('>>> WARN: No Jira API URL found in config, the Jira extension may not work as expected.')
    }
    // Create the link to the issue.
    createPhraseNode(parent, 'anchor', target, attributes, options).render()
}

// Needed to convert AsciiDoc when not in an Antora context but with the
// Antora integration enabled.
include_processor(filter: { it.contains('example$') }) { document, reader, target, attributes ->
    def baseDir = new File(reader.getDir()).parentFile
    def rawContent = new File(reader.getFile()).getText('UTF-8').replace('example$', "${baseDir}/examples/")
    java.util.regex.Matcher matcher = (rawContent =~ /include::[^\[]+/)

    if (matcher.find()) {
        def content = matcher.group().replace('example$', "${baseDir}/examples/") + '[]'
        reader.pushInclude(content, target, target, 1, attributes)
    }
}
