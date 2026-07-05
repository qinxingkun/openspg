#!/usr/bin/env bash
# Build an OpenSPG server fat jar with the Nebula (trsgraph) graph-store driver baked in.
#
# Steps:
#   1. mvn package cloudext-impl-graph-store-nebula (+ runtime deps)
#   2. Extract the official openspg-server fat jar from a Docker image
#   3. Inject Nebula driver jars into BOOT-INF/lib (STORED, Spring Boot requirement)
#   4. Patch nested lib jars with trsgraph-related server fixes (Java 8 bytecode)
#   5. Write dev/release/nebula-runtime/arks-sofaboot-0.0.1-SNAPSHOT-executable.jar
#
# Usage:
#   ./scripts/build-openspg-server-nebula.sh
#   docker compose -f docker-compose.local.yml build server
#   docker compose -f docker-compose.local.yml up -d server
#
# Env overrides:
#   OPENSPG_BASE_IMAGE   official server image (default: spg-registry.../openspg-server:latest)
#   MAVEN_JAVA_HOME      JDK for Maven (default: auto-detect 11/17/21)
#   JAVAC_JAVA_HOME      JDK for patch compiles (default: same as MAVEN_JAVA_HOME)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RELEASE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
OPENSPG_ROOT="$(cd "${RELEASE_DIR}/../.." && pwd)"
OUTPUT_DIR="${RELEASE_DIR}/nebula-runtime"
OUTPUT_JAR="${OUTPUT_DIR}/arks-sofaboot-0.0.1-SNAPSHOT-executable.jar"
BASE_IMAGE="${OPENSPG_BASE_IMAGE:-spg-registry.cn-hangzhou.cr.aliyuncs.com/spg/openspg-server:latest}"

NEBULA_MODULE="cloudext/impl/graph-store/nebula"
NEBULA_ARTIFACT="cloudext-impl-graph-store-nebula-0.0.1-SNAPSHOT.jar"
SCHEMA_SERVICE_LIB="BOOT-INF/lib/com.antgroup.openspg.server-core-schema-service-0.0.1-SNAPSHOT.jar"
COMMON_SERVICE_LIB="BOOT-INF/lib/com.antgroup.openspg.server-common-service-0.0.1-SNAPSHOT.jar"

log() { printf '[build-nebula-server] %s\n' "$*" >&2; }
die() { printf '[build-nebula-server] ERROR: %s\n' "$*" >&2; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "missing command: $1"
}

detect_java_home() {
  if [[ -n "${MAVEN_JAVA_HOME:-}" && -x "${MAVEN_JAVA_HOME}/bin/java" ]]; then
    echo "${MAVEN_JAVA_HOME}"
    return
  fi
  for candidate in \
    /usr/lib/jvm/java-11-openjdk-amd64 \
    /usr/lib/jvm/java-17-openjdk-amd64 \
    /usr/lib/jvm/java-21-openjdk-amd64; do
    if [[ -x "${candidate}/bin/java" ]]; then
      echo "${candidate}"
      return
    fi
  done
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    echo "${JAVA_HOME}"
    return
  fi
  die "could not find a JDK; set MAVEN_JAVA_HOME"
}

build_nebula_module() {
  local java_home="$1"
  log "mvn package ${NEBULA_MODULE} (JDK ${java_home})"
  (
    export JAVA_HOME="${java_home}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
    cd "${OPENSPG_ROOT}"
    mvn -q -pl "${NEBULA_MODULE}" -am package -Dmaven.test.skip=true
  )
}

extract_base_jar() {
  local workdir="$1"
  local base_jar="${workdir}/base.jar"
  if [[ -f "${base_jar}" ]]; then
    log "reuse existing base jar at ${base_jar}"
    printf '%s\n' "${base_jar}"
    return
  fi
  require_cmd docker
  log "pulling/extracting base jar from ${BASE_IMAGE}"
  local cid
  cid="$(docker create "${BASE_IMAGE}")"
  docker cp "${cid}:/arks-sofaboot-0.0.1-SNAPSHOT-executable.jar" "${base_jar}"
  docker rm "${cid}" >/dev/null
  printf '%s\n' "${base_jar}"
}

inject_nebula_libs() {
  local fat_jar="$1"
  local nebula_target="${OPENSPG_ROOT}/${NEBULA_MODULE}/target"
  local deploy_dir="${nebula_target}/nebula-deploy-libs"
  local driver_jar="${nebula_target}/${NEBULA_ARTIFACT}"

  [[ -f "${driver_jar}" ]] || die "missing ${driver_jar}; run mvn package first"
  [[ -d "${deploy_dir}" ]] || die "missing ${deploy_dir}"

  local workdir
  workdir="$(mktemp -d)"

  cp "${fat_jar}" "${workdir}/fat.jar"
  cd "${workdir}"
  jar xf fat.jar BOOT-INF/classpath.idx

  mkdir -p BOOT-INF/lib
  cp "${driver_jar}" "BOOT-INF/lib/${NEBULA_ARTIFACT}"
  cp "${deploy_dir}/client-"*.jar BOOT-INF/lib/
  cp "${deploy_dir}/bcpkix-jdk15on-"*.jar BOOT-INF/lib/
  cp "${deploy_dir}/bcutil-jdk15on-"*.jar BOOT-INF/lib/

  for lib in BOOT-INF/lib/${NEBULA_ARTIFACT} BOOT-INF/lib/client-*.jar \
    BOOT-INF/lib/bcpkix-jdk15on-*.jar BOOT-INF/lib/bcutil-jdk15on-*.jar; do
    local name
    name="$(basename "${lib}")"
    if ! grep -Fq "BOOT-INF/lib/${name}" BOOT-INF/classpath.idx; then
      echo "- \"BOOT-INF/lib/${name}\"" >> BOOT-INF/classpath.idx
    fi
  done

  zip -0 -X -q -g fat.jar \
    BOOT-INF/lib/"${NEBULA_ARTIFACT}" \
    BOOT-INF/lib/client-*.jar \
    BOOT-INF/lib/bcpkix-jdk15on-*.jar \
    BOOT-INF/lib/bcutil-jdk15on-*.jar
  zip -X -q -g fat.jar BOOT-INF/classpath.idx

  cp fat.jar "${fat_jar}"
  log "injected Nebula driver jars into fat jar"
  rm -rf "${workdir}"
}

patch_nested_lib_jars() {
  local fat_jar="$1"
  local javac_home="$2"

  local workdir
  workdir="$(mktemp -d)"

  cd "${workdir}"
  jar xf "${fat_jar}" BOOT-INF/classes BOOT-INF/lib

  local cp="BOOT-INF/classes:$(echo BOOT-INF/lib/*.jar | tr ' ' ':')"
  local patch_out="${workdir}/patch-out"
  mkdir -p "${patch_out}/spring"

  log "compiling GraphStorageSyncer + ProjectServiceImpl (Java 8 target)"
  "${javac_home}/bin/javac" -proc:none --release 8 -cp "${cp}" -d "${patch_out}" \
    "${OPENSPG_ROOT}/server/core/schema/service/src/main/java/com/antgroup/openspg/server/core/schema/service/alter/sync/GraphStorageSyncer.java" \
    "${OPENSPG_ROOT}/server/common/service/src/main/java/com/antgroup/openspg/server/common/service/project/impl/ProjectServiceImpl.java"

  cp "${OPENSPG_ROOT}/server/core/schema/service/src/main/resources/spring/spring-schema.xml" \
    "${patch_out}/spring/spring-schema.xml"

  jar uf "${SCHEMA_SERVICE_LIB}" \
    -C "${patch_out}" com/antgroup/openspg/server/core/schema/service/alter/sync/GraphStorageSyncer.class \
    -C "${patch_out}" spring/spring-schema.xml

  jar uf "${COMMON_SERVICE_LIB}" \
    -C "${patch_out}" com/antgroup/openspg/server/common/service/project/impl/ProjectServiceImpl.class

  zip -0 -X -q -g "${fat_jar}" "${SCHEMA_SERVICE_LIB}" "${COMMON_SERVICE_LIB}"
  log "patched schema-service and common-service nested jars"
  rm -rf "${workdir}"
}

main() {
  require_cmd mvn
  require_cmd jar
  require_cmd zip
  require_cmd javac

  local maven_home javac_home
  maven_home="$(detect_java_home)"
  javac_home="${JAVAC_JAVA_HOME:-${maven_home}}"

  local workdir
  workdir="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '${workdir}'" EXIT

  build_nebula_module "${maven_home}"

  local base_jar fat_jar
  base_jar="$(extract_base_jar "${workdir}")"
  fat_jar="${workdir}/arks-sofaboot-0.0.1-SNAPSHOT-executable.jar"
  cp "${base_jar}" "${fat_jar}"

  inject_nebula_libs "${fat_jar}"
  patch_nested_lib_jars "${fat_jar}" "${javac_home}"

  mkdir -p "${OUTPUT_DIR}"
  cp "${fat_jar}" "${OUTPUT_JAR}"
  log "done: ${OUTPUT_JAR} ($(du -h "${OUTPUT_JAR}" | awk '{print $1}'))"
  log "next: cd ${RELEASE_DIR} && docker compose -f docker-compose.local.yml build server"
}

main "$@"
