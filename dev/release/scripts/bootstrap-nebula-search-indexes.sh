#!/usr/bin/env bash
# Create/rebuild Nebula search indexes for an entity tag in a project space.
# Usage:
#   ./scripts/bootstrap-nebula-search-indexes.sh [graphd_host:port] [space] [tag]
set -euo pipefail

GRAPH_HOST="${1:-127.0.0.1:9677}"
SPACE="${2:-SmokeTest}"
TAG="${3:-SmokeTest_Scholar}"
USER="${TRSGRAPH_USER:-root}"
PASSWORD="${TRSGRAPH_PASSWORD:-trsadmin}"
HOST="${GRAPH_HOST%:*}"
PORT="${GRAPH_HOST#*:}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK=/tmp/bootstrap-nebula-indexes-$$
mkdir -p "$WORK"
cat > "$WORK/Bootstrap.java" <<JAVA
import com.vesoft.nebula.client.graph.SessionPool;
import com.vesoft.nebula.client.graph.SessionPoolConfig;
import com.vesoft.nebula.client.graph.data.HostAddress;
import com.vesoft.nebula.client.graph.data.ResultSet;
import java.util.Collections;
public class Bootstrap {
  static void run(SessionPool pool, String stmt) throws Exception {
    ResultSet rs = pool.execute(stmt);
    System.out.println((rs.isSucceeded() ? "OK" : "FAIL") + " " + stmt);
    if (!rs.isSucceeded()) System.out.println("  " + rs.getErrorMessage());
  }
  public static void main(String[] args) throws Exception {
    String tag = args[0];
    SessionPool pool = new SessionPool(new SessionPoolConfig(
        Collections.singletonList(new HostAddress(args[1], Integer.parseInt(args[2]))),
        args[3], args[4], args[5]));
    if (!pool.init()) throw new RuntimeException("session pool init failed");
    run(pool, "CREATE TAG INDEX IF NOT EXISTS `" + tag + "_id_idx` ON `" + tag + "` (`id`(256))");
    run(pool, "CREATE TAG INDEX IF NOT EXISTS `" + tag + "_name_idx` ON `" + tag + "` (`name`(256))");
    run(pool, "REBUILD TAG INDEX `" + tag + "_id_idx`");
    run(pool, "REBUILD TAG INDEX `" + tag + "_name_idx`");
    run(pool, "REBUILD TAG INDEX `" + tag + "_scan_idx`");
    pool.close();
  }
}
JAVA

JAR="$(find ~/.m2/repository/com/vesoft/client -name 'client-*.jar' | sort -V | tail -1)"
SLF4J="$(find ~/.m2/repository/org/slf4j/slf4j-api -name 'slf4j-api-*.jar' | sort -V | tail -1)"
javac -cp "$JAR:$SLF4J" "$WORK/Bootstrap.java"
java -cp "$WORK:$JAR:$SLF4J" Bootstrap "$TAG" "$HOST" "$PORT" "$SPACE" "$USER" "$PASSWORD"
rm -rf "$WORK"
