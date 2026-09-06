#!/bin/sh
# CA 또는 truststore 준비 실패 시 비민감 오류만 남기고 JVM을 시작하지 않는다.
set -eu
umask 077

fail() {
    printf '%s\n' 'MySQL TLS bootstrap failed; JVM not started.' >&2
    exit 1
}

[ "$(id -u)" -ne 0 ] || fail
command -v keytool >/dev/null 2>&1 || fail
[ -r /etc/secrets/aiven-ca.pem ] && [ -s /etc/secrets/aiven-ca.pem ] || fail
[ -n "${MYSQL_TRUSTSTORE_PASSWORD:-}" ] || fail
[ "${MYSQL_TRUSTSTORE_URL:-}" = 'file:/tmp/nyam-mysql/aiven-truststore.p12' ] || fail

mkdir -p /tmp/nyam-mysql 2>/dev/null || fail
chmod 0700 /tmp/nyam-mysql 2>/dev/null || fail
# 이 고정 파일만 교체한다. 이전 실행의 CA를 재사용하지 않는다.
rm -f /tmp/nyam-mysql/aiven-truststore.p12 2>/dev/null || fail
keytool -importcert -noprompt -alias aiven-ca \
    -file /etc/secrets/aiven-ca.pem \
    -keystore /tmp/nyam-mysql/aiven-truststore.p12 \
    -storetype PKCS12 -storepass:env MYSQL_TRUSTSTORE_PASSWORD \
    >/dev/null 2>&1 || fail
chmod 0600 /tmp/nyam-mysql/aiven-truststore.p12 2>/dev/null || fail

exec java -XX:MaxRAMPercentage=70.0 -jar /app/nyam.jar "$@"
