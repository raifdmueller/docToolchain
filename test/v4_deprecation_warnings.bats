# SPDX-License-Identifier: MIT
# Test deprecation warnings for v3-era Gradle commands in v4 mode

setup() {
    load 'test_helper.bash'
    setup_environment

    export DTC_PROJECT_BRANCH=test

    # Create a v4 installation (lib/ directory present)
    mkdir -p "${DTC_HOME}/lib"
    mkdir -p "${DTC_HOME}/scripts"
    touch "${DTC_HOME}/lib/dummy.jar"

    # Create a task script with @task marker
    echo '// @task' > "${DTC_HOME}/scripts/generateHTML.groovy"

    # Installed local java
    _mock=$(mock_create_java "${DTC_ROOT}/jdk/bin/java" "17.0.14")
}

teardown() {
    mock_teardown
    rm -rf "${DTC_ROOT}"
}

# Scenario: User runs dtcw tasks --group doctoolchain
@test "v4: tasks with --group shows warning that --group is ignored" {
    run ./dtcw tasks --group doctoolchain
    assert_success
    assert_output --partial "Available tasks:"
    assert_output --partial "generateHTML"
    assert_output --partial "--group"
    assert_output --partial "ignored"
}

# Scenario: User runs dtcw tasks --group (without value)
@test "v4: tasks with --group but no value still lists tasks" {
    run ./dtcw tasks --group
    assert_success
    assert_output --partial "Available tasks:"
}
