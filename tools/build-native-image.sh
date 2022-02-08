#!/usr/bin/env bash
#GRAALVM_HOME=${GRAALVM_HOME:-/usr/lib/graalvm}
native-image \
  --class-path ../../build/libs/terra-cli-*.jar:build/libs/nativecompile-classpath-*.jar \
  -H:Name=terra \
  bio.terra.cli.command.Main
