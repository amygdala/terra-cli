#!/usr/bin/env bash
# Build the jar file from source
./gradlew clean build

# Make sure native-image is on the path. I use jenv to manage this.
native-image \
  --class-path 'build/libs/terra-cli-0.140.0.jar' \
  -H:+ReportExceptionStackTraces \
  bio.terra.cli.command.Main \
  terra
