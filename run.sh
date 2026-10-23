#!/usr/bin/env sh
set -eu
mkdir -p build/classes
javac -d build/classes $(find src/main/java src/test/java -name '*.java')
java -ea -cp build/classes example.infrai.TenantFieldServiceTest
java -cp build/classes example.infrai.FieldServiceApplication "${1:-demo-tenant}"
