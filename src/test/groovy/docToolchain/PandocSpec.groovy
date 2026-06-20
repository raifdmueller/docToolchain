package docToolchain

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Ignore
import spock.lang.Requires
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

// v4: exercises the v3 Gradle-task path, which Gradle 9 breaks (project.exec{}
// was removed). 'pandoc' has no v4 direct-JVM script yet; re-enable once it is
// migrated. See #49.
@Ignore('v4: v3 Gradle-task path (project.exec{} removed in Gradle 9) — pending migration to a v4 direct-JVM script (#49)')
class PandocSpec extends Specification {

    void 'test pandoc verification on linux'() {
        when: 'the gradle task is invoked on linux'
        def result = GradleRunner.create()
            .withProjectDir(new File('.'))
            .withArguments([
                'verifyPandoc',
                '--info',
            ])
            .build()
        then: 'pandoc exists and the task succeeded'
            result.task(":verifyPandoc").outcome == SUCCESS
    }

    @Requires({ os.windows })
    void 'test pandoc verification on windows'() {
        when: 'the gradle task is invoked on windows'
        def result = GradleRunner.create()
            .withProjectDir(new File('.'))
            .withArguments([
                'verifyPandoc',
                '--info',
            ])
            .build()
        then: 'pandoc exists and the task succeeded'
        result.task(":verifyPandoc").outcome == SUCCESS
    }

    void 'test convert to docx with "docx" configuration'() {
        when: 'convertToDocx task is invoked with explicit docx configuration'
        def result = GradleRunner.create()
            .withProjectDir(new File('.'))
            .withArguments([
                'convertToDocx',
                '-PoutputPath=../../../build/test/docs/pandoc/explicit',
                '-PmainConfigFile=./src/test/testPandoc/config.groovy',
                '--info',
            ])
            .build()
        then: 'the task succeeded'
        result.task(":convertToDocx").outcome == SUCCESS
        and: 'the output does not contain the warning'
            !result.output.contains('WARNING: No source files defined for type "docx".')
    }

    void 'test convert to docx without "docx" but with "docbook" configuration'() {
        when: 'convertToDocx task is invoked without explicit docx configuration'
        def result = GradleRunner.create()
            .withProjectDir(new File('.'))
            .withArguments([
                'convertToDocx',
                '-PoutputPath=../../../build/test/docs/pandoc/implicit',
                '-PmainConfigFile=./src/test/testPandoc/implicit_config.groovy',
                '--info',
            ])
            .build()
        then: 'the task succeeded'
        result.task(":convertToDocx").outcome == SUCCESS
        and: 'the output does contain the warning'
            result.output.contains('WARNING: No source files defined for type "docx".')
    }

    void 'test convert to docx without required configuration'() {
        when: 'convertToDocx task is invoked with missing configuration'
        def result = GradleRunner.create()
            .withProjectDir(new File('.'))
            .withArguments([
                'convertToDocx',
                '-PmainConfigFile=./src/test/testPandoc/missing_config.groovy',
                '--info',
            ])
            .buildAndFail()
        then: 'the task failed'
        result.task(":convertToDocx").outcome == FAILED
        and: 'threw an exception'
        result.output.contains('No source files defined for type \'docx\'')
    }
}
