/*
 * Copyright 2023 OpenSPG Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 */

package com.antgroup.openspg.cloudext.impl.graphstore.nebula;

import com.antgroup.openspg.cloudext.impl.graphstore.nebula.convertor.NebulaRecordConvertor;
import com.antgroup.openspg.cloudext.impl.graphstore.nebula.util.NebulaRecordUtils;
import com.antgroup.openspg.cloudext.impl.graphstore.nebula.util.NebulaSchemaUtils;
import com.antgroup.openspg.cloudext.impl.graphstore.nebula.util.NebulaValueUtils;
import com.antgroup.openspg.cloudext.interfaces.graphstore.BaseLPGGraphStoreClient;
import com.antgroup.openspg.cloudext.interfaces.graphstore.LPGInternalIdGenerator;
import com.antgroup.openspg.cloudext.interfaces.graphstore.LPGTypeNameConvertor;
import com.antgroup.openspg.cloudext.interfaces.graphstore.cmd.BaseLPGRecordQuery;
import com.antgroup.openspg.cloudext.interfaces.graphstore.cmd.BatchVertexLPGRecordQuery;
import com.antgroup.openspg.cloudext.interfaces.graphstore.cmd.OneHopLPGRecordQuery;
import com.antgroup.openspg.cloudext.interfaces.graphstore.cmd.PageRankCompete;
import com.antgroup.openspg.cloudext.interfaces.graphstore.cmd.ScanLPGRecordQuery;
import com.antgroup.openspg.cloudext.interfaces.graphstore.cmd.VertexLPGRecordQuery;
import com.antgroup.openspg.cloudext.interfaces.graphstore.impl.NoChangedIdGenerator;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.ComputeResultRow;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.Direction;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.EdgeRecord;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.VertexRecord;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.struct.BaseLPGRecordStruct;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.struct.GraphLPGRecordStruct;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.EdgeType;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.EdgeTypeName;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.LPGProperty;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.LPGSchema;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.VertexType;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.operation.AddPropertyOperation;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.operation.AlterEdgeTypeOperation;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.operation.AlterVertexTypeOperation;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.operation.BaseLPGSchemaOperation;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.operation.CreateEdgeTypeOperation;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.operation.CreateVertexTypeOperation;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.operation.DropEdgeTypeOperation;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.operation.DropPropertyOperation;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.operation.DropVertexTypeOperation;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.operation.SchemaAtomicOperationEnum;
import com.antgroup.openspg.cloudext.interfaces.graphstore.util.TypeNameUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vesoft.nebula.client.graph.NebulaPoolConfig;
import com.vesoft.nebula.client.graph.SessionPool;
import com.vesoft.nebula.client.graph.SessionPoolConfig;
import com.vesoft.nebula.client.graph.data.HostAddress;
import com.vesoft.nebula.client.graph.data.ResultSet;
import com.vesoft.nebula.client.graph.data.ValueWrapper;
import com.vesoft.nebula.client.graph.net.NebulaPool;
import com.vesoft.nebula.client.graph.net.Session;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * A {@link BaseLPGGraphStoreClient} implementation backed by NebulaGraph 3.x (including trsgraph).
 *
 * <p>Connection url: {@code
 * nebula://host:port?user=..&password=..&space=..&timeout=..&hosts=h1:p1,h2:p2}
 */
@Slf4j
public class NebulaStoreClient extends BaseLPGGraphStoreClient {

  private final String space;
  private final SessionPool sessionPool;

  @Getter private final LPGInternalIdGenerator internalIdGenerator;
  @Getter private final LPGTypeNameConvertor typeNameConvertor;
  @Getter private final String connUrl;

  public NebulaStoreClient(String connUrl, LPGTypeNameConvertor typeNameConvertor) {
    UriComponents uriComponents = UriComponentsBuilder.fromUriString(connUrl).build();
    this.connUrl = connUrl;
    this.space = resolveSpace(uriComponents, connUrl);
    this.typeNameConvertor = typeNameConvertor;
    this.internalIdGenerator = new NoChangedIdGenerator();
    ensureSpaceExists(uriComponents);
    this.sessionPool = initSessionPool(uriComponents);
  }

  /**
   * Resolve the Nebula space. Prefer an explicit {@code space} query param; otherwise fall back to
   * {@code namespace} then {@code database}, since the OpenSPG server rewrites graph-store urls in
   * a Neo4j style and only carries the project namespace.
   */
  private static String resolveSpace(UriComponents uriComponents, String connUrl) {
    String resolved = uriComponents.getQueryParams().getFirst(NebulaConstants.SPACE);
    if (StringUtils.isBlank(resolved)) {
      resolved = uriComponents.getQueryParams().getFirst(NebulaConstants.NAMESPACE);
    }
    if (StringUtils.isBlank(resolved)) {
      resolved = uriComponents.getQueryParams().getFirst(NebulaConstants.DATABASE);
    }
    if (StringUtils.isBlank(resolved)) {
      throw new IllegalArgumentException(
          "nebula connection url must specify a 'space' (or 'namespace'/'database') query param: "
              + connUrl);
    }
    return resolved;
  }

  /**
   * Ensure the target space exists before binding a {@link SessionPool} to it (the pool selects the
   * space on init and fails if it is missing). Uses a short-lived space-less {@link NebulaPool} to
   * run {@code CREATE SPACE IF NOT EXISTS} and waits until the space is ready. This makes a global
   * cutover work: each new OpenSPG project namespace gets its space created on first use.
   */
  private void ensureSpaceExists(UriComponents uriComponents) {
    String user =
        defaultIfBlank(
            uriComponents.getQueryParams().getFirst(NebulaConstants.USER),
            NebulaConstants.DEFAULT_USER);
    String password =
        defaultIfBlank(
            uriComponents.getQueryParams().getFirst(NebulaConstants.PASSWORD),
            NebulaConstants.DEFAULT_PASSWORD);
    List<HostAddress> addresses = parseHosts(uriComponents);

    NebulaPoolConfig poolConfig = new NebulaPoolConfig();
    poolConfig.setMaxConnSize(2);
    NebulaPool pool = new NebulaPool();
    Session session = null;
    try {
      if (!pool.init(addresses, poolConfig)) {
        throw new RuntimeException("nebula pool init failed while ensuring space=" + space);
      }
      session = pool.getSession(user, password, false);
      String createSpace =
          String.format(
              "CREATE SPACE IF NOT EXISTS %s (partition_num=%d, replica_factor=%d, "
                  + "vid_type=FIXED_STRING(%d))",
              NebulaValueUtils.quoteName(space),
              NebulaConstants.DEFAULT_PARTITION_NUM,
              NebulaConstants.DEFAULT_REPLICA_FACTOR,
              NebulaConstants.DEFAULT_VID_LENGTH);
      ResultSet created = session.execute(createSpace);
      if (!created.isSucceeded()) {
        throw new RuntimeException(
            "failed to create nebula space=" + space + ", error=" + created.getErrorMessage());
      }
      waitForSpaceReady(session, space);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("error ensuring nebula space=" + space, e);
    } finally {
      if (session != null) {
        session.release();
      }
      pool.close();
    }
  }

  private void waitForSpaceReady(Session session, String space) throws Exception {
    long deadline = System.currentTimeMillis() + NebulaConstants.SPACE_EFFECTIVE_TIMEOUT_MS;
    // `USE space` goes through graphd and only succeeds once the new space has propagated via a
    // heartbeat cycle; `DESC SPACE` (meta) would return too early and the SessionPool bind fails.
    String use = "USE " + NebulaValueUtils.quoteName(space);
    while (System.currentTimeMillis() < deadline) {
      ResultSet resultSet = session.execute(use);
      if (resultSet.isSucceeded()) {
        return;
      }
      sleep(NebulaConstants.SCHEMA_EFFECTIVE_INTERVAL_MS);
    }
    throw new RuntimeException("timeout waiting for nebula space to be ready: " + space);
  }

  private SessionPool initSessionPool(UriComponents uriComponents) {
    String user =
        defaultIfBlank(
            uriComponents.getQueryParams().getFirst(NebulaConstants.USER),
            NebulaConstants.DEFAULT_USER);
    String password =
        defaultIfBlank(
            uriComponents.getQueryParams().getFirst(NebulaConstants.PASSWORD),
            NebulaConstants.DEFAULT_PASSWORD);

    List<HostAddress> addresses = parseHosts(uriComponents);
    SessionPoolConfig config = new SessionPoolConfig(addresses, space, user, password);
    config.setRetryTimes(3);
    config.setIntervalTime(1000);

    String timeout = uriComponents.getQueryParams().getFirst(NebulaConstants.TIMEOUT);
    if (StringUtils.isNotBlank(timeout)) {
      config.setTimeout(Integer.parseInt(timeout));
    }

    SessionPool pool = new SessionPool(config);
    if (!pool.init()) {
      throw new RuntimeException("nebula session pool init failed for space=" + space);
    }
    return pool;
  }

  private List<HostAddress> parseHosts(UriComponents uriComponents) {
    String hosts = uriComponents.getQueryParams().getFirst(NebulaConstants.HOSTS);
    if (StringUtils.isNotBlank(hosts)) {
      return java.util.Arrays.stream(hosts.split(","))
          .map(String::trim)
          .filter(StringUtils::isNotBlank)
          .map(
              hostPort -> {
                String[] parts = hostPort.split(":");
                int port =
                    parts.length > 1
                        ? Integer.parseInt(parts[1].trim())
                        : NebulaConstants.DEFAULT_PORT;
                return new HostAddress(parts[0].trim(), port);
              })
          .collect(Collectors.toList());
    }
    int port = uriComponents.getPort() > 0 ? uriComponents.getPort() : NebulaConstants.DEFAULT_PORT;
    return Lists.newArrayList(new HostAddress(uriComponents.getHost(), port));
  }

  private static String defaultIfBlank(String value, String defaultValue) {
    return StringUtils.isBlank(value) ? defaultValue : value;
  }

  private ResultSet execute(String statement) {
    try {
      ResultSet resultSet = sessionPool.execute(statement);
      if (!resultSet.isSucceeded()) {
        throw new RuntimeException(
            String.format(
                "nebula statement failed, error=%s, statement=%s",
                resultSet.getErrorMessage(), statement));
      }
      return resultSet;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("nebula statement execution error, statement=" + statement, e);
    }
  }

  /** Poll a {@code DESCRIBE} statement until the DDL takes effect (Nebula schema sync is async). */
  private void waitForSchemaEffective(String describeStatement) {
    long deadline = System.currentTimeMillis() + NebulaConstants.SCHEMA_EFFECTIVE_TIMEOUT_MS;
    RuntimeException lastError = null;
    while (System.currentTimeMillis() < deadline) {
      try {
        ResultSet resultSet = sessionPool.execute(describeStatement);
        if (resultSet.isSucceeded()) {
          return;
        }
        lastError = new RuntimeException(resultSet.getErrorMessage());
      } catch (Exception e) {
        lastError = new RuntimeException(e);
      }
      sleep(NebulaConstants.SCHEMA_EFFECTIVE_INTERVAL_MS);
    }
    throw new RuntimeException(
        "timeout waiting for nebula schema to take effect: " + describeStatement, lastError);
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public boolean createVertexType(CreateVertexTypeOperation operation) {
    TypeNameUtils.convertTypeName(operation, typeNameConvertor);
    String tagName = operation.getVertexTypeName();
    List<LPGProperty> properties =
        collectProperties(operation, name -> !VertexType.ID.equals(name));
    execute(NebulaSchemaUtils.createTag(tagName, properties));
    waitForSchemaEffective(NebulaSchemaUtils.fetchProbeTag(tagName));
    createScanIndexQuietly(true, tagName);
    return true;
  }

  @Override
  public boolean createEdgeType(CreateEdgeTypeOperation operation) {
    TypeNameUtils.convertTypeName(operation, typeNameConvertor);
    String edgeName = operation.getEdgeTypeName().getEdgeLabel();
    List<LPGProperty> properties =
        collectProperties(
            operation, name -> !EdgeType.SRC_ID.equals(name) && !EdgeType.DST_ID.equals(name));
    execute(NebulaSchemaUtils.createEdge(edgeName, properties));
    waitForSchemaEffective(NebulaSchemaUtils.fetchProbeEdge(edgeName));
    createScanIndexQuietly(false, edgeName);
    return true;
  }

  private void createScanIndexQuietly(boolean isTag, String typeName) {
    try {
      if (isTag) {
        execute(NebulaSchemaUtils.createTagScanIndex(typeName));
        execute(NebulaSchemaUtils.rebuildTagIndex(typeName));
      } else {
        execute(NebulaSchemaUtils.createEdgeScanIndex(typeName));
        execute(NebulaSchemaUtils.rebuildEdgeIndex(typeName));
      }
    } catch (Exception e) {
      log.warn(
          "failed to create scan index for {} (scan queries may be unavailable): {}",
          typeName,
          e.getMessage());
    }
  }

  @Override
  public boolean alterVertexType(AlterVertexTypeOperation operation) {
    TypeNameUtils.convertTypeName(operation, typeNameConvertor);
    String tagName = operation.getVertexTypeName();
    List<LPGProperty> addProps = collectProperties(operation, name -> true);
    if (CollectionUtils.isNotEmpty(addProps)) {
      execute(NebulaSchemaUtils.alterTagAddProperties(tagName, addProps));
    }
    List<String> dropProps = collectDropProperties(operation);
    if (CollectionUtils.isNotEmpty(dropProps)) {
      execute(NebulaSchemaUtils.alterTagDropProperties(tagName, dropProps));
    }
    waitForSchemaEffective(NebulaSchemaUtils.describeTag(tagName));
    return true;
  }

  @Override
  public boolean alterEdgeType(AlterEdgeTypeOperation operation) {
    TypeNameUtils.convertTypeName(operation, typeNameConvertor);
    String edgeName = operation.getEdgeTypeName().getEdgeLabel();
    List<LPGProperty> addProps = collectProperties(operation, name -> true);
    if (CollectionUtils.isNotEmpty(addProps)) {
      execute(NebulaSchemaUtils.alterEdgeAddProperties(edgeName, addProps));
    }
    List<String> dropProps = collectDropProperties(operation);
    if (CollectionUtils.isNotEmpty(dropProps)) {
      execute(NebulaSchemaUtils.alterEdgeDropProperties(edgeName, dropProps));
    }
    waitForSchemaEffective(NebulaSchemaUtils.describeEdge(edgeName));
    return true;
  }

  @Override
  public boolean dropVertexType(DropVertexTypeOperation operation) {
    TypeNameUtils.convertTypeName(operation, typeNameConvertor);
    execute(NebulaSchemaUtils.dropTag(operation.getVertexTypeName()));
    return true;
  }

  @Override
  public boolean dropEdgeType(DropEdgeTypeOperation operation) {
    TypeNameUtils.convertTypeName(operation, typeNameConvertor);
    execute(NebulaSchemaUtils.dropEdge(operation.getEdgeTypeName().getEdgeLabel()));
    return true;
  }

  @Override
  public boolean batchTransactionalSchemaOperations(List<BaseLPGSchemaOperation> operations) {
    return false;
  }

  private List<LPGProperty> collectProperties(
      BaseLPGSchemaOperation operation, java.util.function.Predicate<String> nameFilter) {
    return operation.getAtomicOperations().stream()
        .filter(op -> SchemaAtomicOperationEnum.ADD_PROPERTY.equals(op.getOperationTypeEnum()))
        .map(op -> ((AddPropertyOperation) op).getProperty())
        .filter(property -> nameFilter.test(property.getName()))
        .collect(Collectors.toList());
  }

  private List<String> collectDropProperties(BaseLPGSchemaOperation operation) {
    return operation.getAtomicOperations().stream()
        .filter(op -> SchemaAtomicOperationEnum.DROP_PROPERTY.equals(op.getOperationTypeEnum()))
        .map(op -> ((DropPropertyOperation) op).getPropertyName())
        .collect(Collectors.toList());
  }

  @Override
  public void upsertVertex(String vertexTypeName, List<VertexRecord> vertexRecords)
      throws Exception {
    if (CollectionUtils.isEmpty(vertexRecords)) {
      return;
    }
    TypeNameUtils.convertTypeName(vertexRecords, typeNameConvertor);
    String tagName = vertexRecords.get(0).getVertexType();
    for (String statement : NebulaRecordUtils.upsertVertexStatements(tagName, vertexRecords)) {
      execute(statement);
    }
  }

  @Override
  public void deleteVertex(String vertexTypeName, List<VertexRecord> vertexRecords)
      throws Exception {
    if (CollectionUtils.isEmpty(vertexRecords)) {
      return;
    }
    TypeNameUtils.convertTypeName(vertexRecords, typeNameConvertor);
    for (String statement : NebulaRecordUtils.deleteVertexStatements(vertexRecords)) {
      execute(statement);
    }
  }

  @Override
  public void upsertEdge(String edgeTypeName, List<EdgeRecord> edgeRecords) throws Exception {
    upsertEdge(edgeTypeName, edgeRecords, false);
  }

  @Override
  public void upsertEdge(
      String edgeTypeName, List<EdgeRecord> edgeRecords, boolean upsertAdjacentVertices)
      throws Exception {
    if (CollectionUtils.isEmpty(edgeRecords)) {
      return;
    }
    TypeNameUtils.convertTypeName(edgeRecords, typeNameConvertor);
    if (upsertAdjacentVertices) {
      upsertAdjacentVertices(edgeRecords);
    }
    String edgeName = edgeRecords.get(0).getEdgeType().getEdgeLabel();
    for (String statement : NebulaRecordUtils.upsertEdgeStatements(edgeName, edgeRecords)) {
      execute(statement);
    }
  }

  /**
   * When requested, ensure both endpoints of each edge exist. The endpoints' tag comes from the
   * (already converted) edge type name.
   *
   * <p>Nebula's {@code INSERT VERTEX} overwrites all listed columns, so inserting an endpoint with
   * an empty property list would wipe a pre-existing vertex's properties. To mirror Neo4j's
   * non-destructive {@code MERGE} semantics we only insert endpoints that do not already exist.
   */
  private void upsertAdjacentVertices(List<EdgeRecord> edgeRecords) {
    EdgeTypeName edgeType = edgeRecords.get(0).getEdgeType();
    insertMissingVertices(
        edgeType.getStartVertexType(),
        edgeRecords.stream().map(EdgeRecord::getSrcId).collect(Collectors.toSet()));
    insertMissingVertices(
        edgeType.getEndVertexType(),
        edgeRecords.stream().map(EdgeRecord::getDstId).collect(Collectors.toSet()));
  }

  private void insertMissingVertices(String tag, Set<String> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return;
    }
    Set<String> existing = existingVertexIds(tag, ids);
    List<VertexRecord> missing =
        ids.stream()
            .filter(id -> !existing.contains(id))
            .map(id -> new VertexRecord(id, tag))
            .collect(Collectors.toList());
    for (String statement : NebulaRecordUtils.upsertVertexStatements(tag, missing)) {
      execute(statement);
    }
  }

  private Set<String> existingVertexIds(String tag, Set<String> ids) {
    String vids = ids.stream().map(NebulaValueUtils::vid).collect(Collectors.joining(", "));
    String statement =
        String.format(
            "FETCH PROP ON %s %s YIELD id(vertex) AS id", NebulaValueUtils.quoteName(tag), vids);
    ResultSet resultSet = execute(statement);
    Set<String> found = new HashSet<>();
    for (int i = 0; i < resultSet.rowsSize(); i++) {
      found.add(NebulaValueUtils.asString(resultSet.rowValues(i).get("id")));
    }
    return found;
  }

  @Override
  public void deleteEdge(String edgeTypeName, List<EdgeRecord> edgeRecords) throws Exception {
    if (CollectionUtils.isEmpty(edgeRecords)) {
      return;
    }
    TypeNameUtils.convertTypeName(edgeRecords, typeNameConvertor);
    String edgeName = edgeRecords.get(0).getEdgeType().getEdgeLabel();
    for (String statement : NebulaRecordUtils.deleteEdgeStatements(edgeName, edgeRecords)) {
      execute(statement);
    }
  }

  @Override
  public LPGSchema querySchema() {
    List<VertexType> vertexTypes = new ArrayList<>();
    for (String tagInStore : showNames("SHOW TAGS")) {
      vertexTypes.add(
          new VertexType(
              tagInStore, describeProperties(NebulaSchemaUtils.describeTag(tagInStore))));
    }

    List<EdgeType> edgeTypes = new ArrayList<>();
    for (String edgeInStore : showNames("SHOW EDGES")) {
      edgeTypes.add(
          new EdgeType(
              new EdgeTypeName(edgeInStore, edgeInStore, edgeInStore),
              describeProperties(NebulaSchemaUtils.describeEdge(edgeInStore))));
    }

    LPGSchema lpgSchema = new LPGSchema(vertexTypes, edgeTypes);
    TypeNameUtils.restoreTypeName(lpgSchema, typeNameConvertor);
    return lpgSchema;
  }

  @Override
  public List<String> queryAllVertexLabels() {
    return showNames("SHOW TAGS").stream()
        .map(typeNameConvertor::restoreVertexTypeName)
        .collect(Collectors.toList());
  }

  private List<String> showNames(String showStatement) {
    ResultSet resultSet = execute(showStatement);
    List<String> names = new ArrayList<>();
    for (int i = 0; i < resultSet.rowsSize(); i++) {
      names.add(NebulaValueUtils.asString(resultSet.rowValues(i).get(0)));
    }
    return names;
  }

  private List<LPGProperty> describeProperties(String describeStatement) {
    ResultSet resultSet = execute(describeStatement);
    List<LPGProperty> properties = new ArrayList<>();
    for (int i = 0; i < resultSet.rowsSize(); i++) {
      String field = NebulaValueUtils.asString(resultSet.rowValues(i).get("Field"));
      String type = NebulaValueUtils.asString(resultSet.rowValues(i).get("Type"));
      properties.add(new LPGProperty(field, NebulaRecordConvertor.toBasicType(type)));
    }
    return properties;
  }

  @Override
  public BaseLPGRecordStruct queryRecord(BaseLPGRecordQuery query) {
    switch (query.getQueryType()) {
      case VERTEX:
        VertexLPGRecordQuery vertexQuery = (VertexLPGRecordQuery) query;
        return querySingleVertex(vertexQuery.getVertexId(), vertexQuery.getVertexName());
      case BATCH_VERTEX:
        BatchVertexLPGRecordQuery batchQuery = (BatchVertexLPGRecordQuery) query;
        return batchQueryVertex(batchQuery.getVertexIds(), batchQuery.getVertexName());
      case SCAN:
        ScanLPGRecordQuery scanQuery = (ScanLPGRecordQuery) query;
        return scan(scanQuery.getTypeName(), scanQuery.getLimit());
      case ONE_HOP_SUBGRAPH:
        OneHopLPGRecordQuery oneHopQuery = (OneHopLPGRecordQuery) query;
        return queryOneHop(
            oneHopQuery.getSrcVertexId(),
            oneHopQuery.getSrcVertexName(),
            oneHopQuery.getDirection(),
            oneHopQuery.getEdgeNames());
      default:
        throw new NotImplementedException("unsupported query type: " + query.getQueryType());
    }
  }

  private GraphLPGRecordStruct querySingleVertex(String vertexId, String vertexName) {
    String tag = typeNameConvertor.convertVertexTypeName(vertexName);
    String statement =
        String.format(
            "FETCH PROP ON %s %s YIELD vertex AS v",
            NebulaValueUtils.quoteName(tag), NebulaValueUtils.vid(vertexId));
    return parseVertexResult(execute(statement));
  }

  private GraphLPGRecordStruct batchQueryVertex(Set<String> vertexIds, String vertexName) {
    GraphLPGRecordStruct result = new GraphLPGRecordStruct();
    if (CollectionUtils.isEmpty(vertexIds)) {
      return result;
    }
    String tag = typeNameConvertor.convertVertexTypeName(vertexName);
    String vids = vertexIds.stream().map(NebulaValueUtils::vid).collect(Collectors.joining(", "));
    String statement =
        String.format(
            "FETCH PROP ON %s %s YIELD vertex AS v", NebulaValueUtils.quoteName(tag), vids);
    return parseVertexResult(execute(statement));
  }

  private GraphLPGRecordStruct scan(Object typeName, Integer limit) {
    String limitClause = limit != null ? " | LIMIT " + limit : "";
    if (typeName instanceof EdgeTypeName) {
      EdgeTypeName edgeTypeName = (EdgeTypeName) typeName;
      String edgeName = typeNameConvertor.convertEdgeTypeName(edgeTypeName);
      String statement =
          String.format(
              "LOOKUP ON %s YIELD edge AS e%s", NebulaValueUtils.quoteName(edgeName), limitClause);
      return parseEdgeResult(execute(statement));
    }
    String tag = typeNameConvertor.convertVertexTypeName(typeName.toString());
    String statement =
        String.format(
            "LOOKUP ON %s YIELD vertex AS v%s", NebulaValueUtils.quoteName(tag), limitClause);
    return parseVertexResult(execute(statement));
  }

  private GraphLPGRecordStruct queryOneHop(
      String srcVertexId, String srcVertexName, Direction direction, Set<EdgeTypeName> edgeNames) {
    if (edgeNames != null && edgeNames.isEmpty()) {
      return querySingleVertex(srcVertexId, srcVertexName);
    }
    String edgeConstraint = "";
    if (edgeNames != null) {
      edgeConstraint =
          ":"
              + edgeNames.stream()
                  .map(typeNameConvertor::convertEdgeTypeName)
                  .map(NebulaValueUtils::quoteName)
                  .collect(Collectors.joining("|"));
    }

    String pattern;
    switch (direction) {
      case OUT:
        pattern = "MATCH (s)-[e%s]->(o) WHERE id(s) == %s RETURN s, e, o";
        break;
      case IN:
        pattern = "MATCH (s)<-[e%s]-(o) WHERE id(s) == %s RETURN s, e, o";
        break;
      case BOTH:
      default:
        pattern = "MATCH (s)-[e%s]-(o) WHERE id(s) == %s RETURN s, e, o";
        break;
    }
    String statement = String.format(pattern, edgeConstraint, NebulaValueUtils.vid(srcVertexId));
    return parseSubgraphResult(execute(statement));
  }

  private GraphLPGRecordStruct parseVertexResult(ResultSet resultSet) {
    GraphLPGRecordStruct result = new GraphLPGRecordStruct();
    for (int i = 0; i < resultSet.rowsSize(); i++) {
      ValueWrapper value = resultSet.rowValues(i).get("v");
      if (value != null && value.isVertex()) {
        try {
          result.addVertexRecord(
              NebulaRecordConvertor.toVertexRecord(value.asNode(), typeNameConvertor));
        } catch (Exception e) {
          throw new RuntimeException("failed to parse nebula vertex result", e);
        }
      }
    }
    return result;
  }

  private GraphLPGRecordStruct parseEdgeResult(ResultSet resultSet) {
    GraphLPGRecordStruct result = new GraphLPGRecordStruct();
    for (int i = 0; i < resultSet.rowsSize(); i++) {
      ValueWrapper value = resultSet.rowValues(i).get("e");
      if (value != null && value.isEdge()) {
        try {
          result.addEdgeRecord(
              NebulaRecordConvertor.toEdgeRecord(value.asRelationship(), typeNameConvertor));
        } catch (Exception e) {
          throw new RuntimeException("failed to parse nebula edge result", e);
        }
      }
    }
    return result;
  }

  private GraphLPGRecordStruct parseSubgraphResult(ResultSet resultSet) {
    Map<String, VertexRecord> vertexRecords = Maps.newLinkedHashMap();
    Map<String, EdgeRecord> edgeRecords = Maps.newLinkedHashMap();
    for (int i = 0; i < resultSet.rowsSize(); i++) {
      addVertexIfPresent(vertexRecords, resultSet.rowValues(i).get("s"));
      addVertexIfPresent(vertexRecords, resultSet.rowValues(i).get("o"));
      ValueWrapper edgeValue = resultSet.rowValues(i).get("e");
      if (edgeValue != null && edgeValue.isEdge()) {
        try {
          EdgeRecord edgeRecord =
              NebulaRecordConvertor.toEdgeRecord(edgeValue.asRelationship(), typeNameConvertor);
          edgeRecords.put(edgeRecord.generateUniqueString(), edgeRecord);
        } catch (Exception e) {
          throw new RuntimeException("failed to parse nebula subgraph edge", e);
        }
      }
    }
    return new GraphLPGRecordStruct(
        new ArrayList<>(vertexRecords.values()), new ArrayList<>(edgeRecords.values()));
  }

  private void addVertexIfPresent(Map<String, VertexRecord> vertexRecords, ValueWrapper value) {
    if (value == null || !value.isVertex()) {
      return;
    }
    try {
      VertexRecord vertexRecord =
          NebulaRecordConvertor.toVertexRecord(value.asNode(), typeNameConvertor);
      vertexRecords.put(vertexRecord.generateUniqueString(), vertexRecord);
    } catch (Exception e) {
      throw new RuntimeException("failed to parse nebula subgraph vertex", e);
    }
  }

  @Override
  public List<ComputeResultRow> runPageRank(PageRankCompete compete) {
    throw new NotImplementedException("page rank is not supported by nebula driver yet.");
  }

  @Override
  public void close() throws Exception {
    if (sessionPool != null) {
      sessionPool.close();
    }
  }
}
