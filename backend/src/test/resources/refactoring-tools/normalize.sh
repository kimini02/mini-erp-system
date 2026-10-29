#!/usr/bin/env bash
set -euo pipefail
classpath_file=${REFACTORING_CLASSPATH:-target/refactoring-run-1/classpath.txt}
[[ -f $classpath_file ]] || { printf '%s\n' 'Set REFACTORING_CLASSPATH to a recording run/classpath.txt file.' >&2;exit 2; }
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$(cat "$classpath_file")" \
  com.minierp.backend.support.refactoring.SnapshotNormalizer "$@"
