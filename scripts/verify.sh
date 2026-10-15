#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
tool_dir="${BIDSMART_TOOLCHAIN_DIR:-$HOME/.bidsmart-toolchains}"
export JAVA_HOME="$tool_dir/jdk-11"
export PATH="$JAVA_HOME/bin:$tool_dir/maven-3.6.1/bin:$PATH"
java -version
mvn -version
maven_args=(-B -ntp -C)
if [[ -n "${BIDSMART_MAVEN_SETTINGS:-}" ]]; then
  maven_args+=(-s "$BIDSMART_MAVEN_SETTINGS")
fi
if [[ -n "${BIDSMART_MAVEN_REPO:-}" ]]; then
  maven_args+=("-Dmaven.repo.local=$BIDSMART_MAVEN_REPO")
fi
mvn "${maven_args[@]}" clean verify
mvn "${maven_args[@]}" help:effective-pom -Doutput=target/effective-pom.xml dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=target/dependency-classpath.txt dependency:tree -DoutputFile=target/dependency-tree.txt
python3 scripts/check-bytecode.py
