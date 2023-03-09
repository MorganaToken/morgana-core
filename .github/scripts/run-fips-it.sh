#!/bin/bash

dnf install -y java-17-openjdk-devel
fips-mode-setup --enable --no-bootcfg
fips-mode-setup --is-enabled
if [ $? -ne 0 ]; then
  exit 1
fi
STRICT_OPTIONS=""
if [ "$1" = "strict" ]; then
  STRICT_OPTIONS="-Dauth.server.fips.mode=strict -Dauth.server.supported.keystore.types=BCFKS -Dauth.server.keystore.type=bcfks -Dauth.server.supported.rsa.key.sizes=2048,4096"
fi
echo "STRICT_OPTIONS: $STRICT_OPTIONS"
TESTS=`testsuite/integration-arquillian/tests/base/testsuites/suite.sh fips`
echo "Tests: $TESTS"
export JAVA_HOME=/etc/alternatives/java_sdk_17
set -o pipefail
./mvnw test -Dsurefire.rerunFailingTestsCount=$SUREFIRE_RERUN_FAILING_COUNT -nsu -B -Pauth-server-quarkus,auth-server-fips140-2 -Dcom.redhat.fips=false $STRICT_OPTIONS -Dtest=$TESTS -pl testsuite/integration-arquillian/tests/base | misc/log/trimmer.sh
