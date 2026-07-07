# OpenSPG 本地 Release（trsgraph 图存储）

图存储（`cloudext.graphstore`）使用 **trsgraph（NebulaGraph）**；搜索引擎仍用 compose 内 **Neo4j**。

## 前置

- Docker、JDK 11（Maven）、JDK 21（推荐，用于 patch 编译）
- trsgraph 已运行，且存在外部网络 `trs-graph-service_default`（compose 中服务名 `trsgraph:9669`）

## 构建与启动

```bash
cd openspg/dev/release

# 1. 构建带 Nebula 驱动的 server fat jar → nebula-runtime/
export MAVEN_JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export JAVAC_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./scripts/build-openspg-server-nebula.sh

# 2. 可选：密码与镜像 tag
cp .env.example .env   # 编辑 TRSGRAPH_PASSWORD 等

# 3. 构建镜像并启动
docker compose -f docker-compose.local.yml build server
docker compose -f docker-compose.local.yml up -d
```

## 要点

|     项      |                               说明                               |
|------------|----------------------------------------------------------------|
| 全局图库 URL   | `nebula://trsgraph:9669?user=…&password=…`（见 compose / `.env`） |
| 项目 ↔ space | 每个 OpenSPG 项目 `namespace` 对应一个 Nebula space（不存在则自动创建）          |
| API 类型名    | 使用完整名 `{namespace}.Person`，不要用短名 `Person`                      |
| 产物         | `nebula-runtime/*.jar` 由脚本生成，已 `.gitignore`，勿提交                |

## 相关文件

- `scripts/build-openspg-server-nebula.sh` — 从官方 server 镜像提取 jar 并注入 Nebula 驱动与 server patch
- `server/Dockerfile.nebula` — 基于官方 `openspg-server` 镜像替换 jar
- `docker-compose.local.yml` — 本地栈（端口 8887 / 13306 / 17474 等）

