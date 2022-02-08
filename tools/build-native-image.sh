#!/usr/bin/env bash

# Build a GraalVM Native Image based on the terra-cli project.

if [ $(basename "$PWD") != 'terra-cli' ]; then
  echo "Script must be run from top-level directory 'terra-cli/'"
  exit 1
fi

# Build the jar file from source
./gradlew clean build

# Make sure native-image is on the path. I use jenv to manage this.
native-image \
  --verbose \
  --class-path 'build/libs/terra-cli-0.140.0.jar:/Users/jaycarlton/.gradle/caches/modules-2/files-2.1/info.picocli/picocli/4.6.1/49a67ee4b4d9722fa60f3f9ffaffa72861c32966/picocli-4.6.1.jar:/Users/jaycarlton/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/1.7.30/b5a4b6d16ab13e34a88fae84c35cd5d68cac922c/slf4j-api-1.7.30.jar' \
  --allow-incomplete-classpath \
  --no-fallback \
  -H:+ReportExceptionStackTraces \
  bio.terra.cli.command.Main \
  terra

# TODO:
#   * list all classes for reflection in reflection config
#   * Set up a resource config file to pull in JSON resource files



#  --initialize-at-build-time=bio.terra.cli.command.shared.options.Format \
#  --initialize-at-run-time=picocli.CommandLine.IExecutionStrategy \
