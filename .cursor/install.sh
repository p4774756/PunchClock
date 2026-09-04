#!/usr/bin/env bash
# Idempotent Cloud Agent bootstrap for the PunchClock monorepo.
# Prepares the JDK 11 + Maven toolchain, builds all modules, and installs the
# Playwright browsers the desktop client needs. Safe to re-run.
set -euo pipefail

cd "$(dirname "$0")/.."

JDK11_HOME=/usr/lib/jvm/java-11-openjdk-amd64

# --- Toolchain: JDK 11 (matches the production temurin-11 image) + Maven ------
if ! dpkg -s openjdk-11-jdk >/dev/null 2>&1 || ! command -v mvn >/dev/null 2>&1; then
  sudo apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-11-jdk maven
fi

# Pin the default JVM to 11 so mvn/java target the version the project expects.
sudo update-java-alternatives -s java-1.11.0-openjdk-amd64 >/dev/null 2>&1 || true
export JAVA_HOME="$JDK11_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

# --- Build every module (warms ~/.m2 and produces the runnable jars) ----------
mvn -B -DskipTests package

# --- Playwright browsers for the desktop client's automation ------------------
# install-deps needs root for apt; the browser download runs as the agent user
# so the cache lands in ~/.cache/ms-playwright.
PLAYWRIGHT_CP="client/target/punchclock-client-standalone.jar"
sudo env "PATH=$PATH" java -cp "$PLAYWRIGHT_CP" com.microsoft.playwright.CLI install-deps chromium
java -cp "$PLAYWRIGHT_CP" com.microsoft.playwright.CLI install chromium

echo "PunchClock environment ready."
