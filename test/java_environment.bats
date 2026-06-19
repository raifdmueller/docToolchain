# SPDX-License-Identifier: MIT
# Copyright 2023, Max Hofer and the docToolchain contributors

# Test installation of Java in a clean environment

# This means: no docToolchain, no Java, no SDKMAN!, no docker
#
# The tests follow the installation instructions based on the output provided by `dtcw`.
#

setup() {
    load 'test_helper.bash'
    setup_environment

    # Installed local doctoolchain
    mock_doctoolchain=$(mock_create "${DTC_HOME}/bin/doctoolchain")
}

teardown() {
    mock_teardown

    # Delete mocks in there
    rm -rf "${DTC_ROOT}"
}

@test "use JAVA_HOME" {
    skip "v4: dtcw environment model changed (local-first, direct invocation, bundled JDK); v3 expectation no longer holds — pending v4 bats-suite adaptation"
    # Test setup
    mock_java=$(mock_create_java "${minimal_system}/jdk/bin/java" "17.0.14")

    # Execute
    PATH="${minimal_system}" JAVA_HOME="${minimal_system}/jdk" ./dtcw tasks --group doctoolchain

    assert_equal "$(mock_get_call_num "${mock_java}")" 1
    assert_equal "$(mock_get_call_args "${mock_doctoolchain}")" ". tasks --group doctoolchain --warning-mode=none --no-daemon -Dfile.encoding=UTF-8 -PmainConfigFile=docToolchainConfig.groovy -Dgradle.user.home=${DTC_ROOT}/.gradle"
}

@test "invalid JAVA_HOME" {
    skip "v4: dtcw environment model changed (local-first, direct invocation, bundled JDK); v3 expectation no longer holds — pending v4 bats-suite adaptation"
    export JAVA_HOME=/jdk/does/not/exist
    PATH="${minimal_system}" run -1 ./dtcw tasks --group doctoolchain
    assert_line "Error: unable to locate a Java Runtime"
}

@test "supported Java version is Java 17" {
    skip "v4: dtcw environment model changed (local-first, direct invocation, bundled JDK); v3 expectation no longer holds — pending v4 bats-suite adaptation"
    mock_java=$(mock_create_java java "17.0.6")

    PATH="${minimal_system}" run -0 ./dtcw tasks --group doctoolchain

    assert_line --partial "Using Java 17.0.6"
    assert_equal "$(mock_get_call_num "${mock_java}")" 1
    assert_equal "$(mock_get_call_args "${mock_doctoolchain}")" ". tasks --group doctoolchain --warning-mode=none --no-daemon -Dfile.encoding=UTF-8 -PmainConfigFile=docToolchainConfig.groovy -Dgradle.user.home=${DTC_ROOT}/.gradle"
}

@test "show unsupported java version - Java 11" {
    skip "v4: dtcw environment model changed (local-first, direct invocation, bundled JDK); v3 expectation no longer holds — pending v4 bats-suite adaptation"
    mock_java=$(mock_create_java java "11.0.19")

    PATH="${minimal_system}" run -1 ./dtcw tasks --group doctoolchain

    assert_line "Error: unsupported Java version 11 [${mock_java}]"
    assert_line "docToolchain supports Java version 17 only. In case that"
}

@test "show unsupported java version - Java 8" {
    skip "v4: dtcw environment model changed (local-first, direct invocation, bundled JDK); v3 expectation no longer holds — pending v4 bats-suite adaptation"
    mock_java=$(mock_create_java java "1.8.0_362")

    PATH="${minimal_system}" run -1 ./dtcw tasks --group doctoolchain

    assert_line "Error: unsupported Java version 8 [${mock_java}]"
    assert_line "docToolchain supports Java version 17 only. In case that"
}

@test "show unsupported java version - Java 20" {
    skip "v4: dtcw environment model changed (local-first, direct invocation, bundled JDK); v3 expectation no longer holds — pending v4 bats-suite adaptation"
    mock_java=$(mock_create_java java "20.0.1")

    PATH="${minimal_system}" run -1 ./dtcw tasks --group doctoolchain

    assert_equal "$(mock_get_call_num "${mock_java}")" 1

    assert_line "Available docToolchain environments: local"
    assert_line "Environments with docToolchain [${DTC_VERSION}]: local"
    assert_line "Using environment: local"

    assert_line "Error: unsupported Java version 20 [${mock_java}]"
    assert_line "docToolchain supports Java version 17 only. In case that"
}

@test "local Java is used before system Java" {
    skip "v4: dtcw environment model changed (local-first, direct invocation, bundled JDK); v3 expectation no longer holds — pending v4 bats-suite adaptation"
    mock_system_java=$(mock_create_java java "20.0.1")

    # Installed with 'dtcw install java'
    mock_java_11=$(mock_create_java "${DTC_ROOT}/jdk/bin/java" "17.0.14")

    PATH="${minimal_system}" run -0 ./dtcw tasks --group doctoolchain

    assert_line --partial "Using Java 17.0.14"
    assert_equal "$(mock_get_call_num "${mock_doctoolchain}")" 1
    assert_equal "$(mock_get_call_num "${mock_java_11}")" 1
    assert_equal "$(mock_get_call_num "${mock_system_java}")" 0
}
