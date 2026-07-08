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

package com.antgroup.openspg.cloudext.impl.searchengine.nebula;

import com.antgroup.openspg.cloudext.impl.graphstore.nebula.NebulaConstants;
import com.antgroup.openspg.cloudext.impl.graphstore.nebula.util.NebulaValueUtils;
import com.antgroup.openspg.cloudext.interfaces.graphstore.LPGTypeNameConvertor;
import com.antgroup.openspg.cloudext.interfaces.graphstore.impl.DefaultLPGTypeNameConvertor;
import com.antgroup.openspg.cloudext.interfaces.searchengine.BaseIdxSearchEngineClient;
import com.antgroup.openspg.cloudext.interfaces.searchengine.IdxNameConvertor;
import com.antgroup.openspg.cloudext.interfaces.searchengine.cmd.IdxGetQuery;
import com.antgroup.openspg.cloudext.interfaces.searchengine.cmd.IdxRecordManipulateCmd;
import com.antgroup.openspg.cloudext.interfaces.searchengine.cmd.IdxSchemaAlterCmd;
import com.antgroup.openspg.cloudext.interfaces.searchengine.model.idx.record.IdxRecord;
import com.antgroup.openspg.cloudext.interfaces.searchengine.model.idx.schema.IdxSchema;
import com.antgroup.openspg.cloudext.interfaces.searchengine.model.request.SearchRequest;
import com.antgroup.openspg.cloudext.interfaces.searchengine.model.request.query.BaseQuery;
import com.antgroup.openspg.cloudext.interfaces.searchengine.model.request.query.CustomSearchQuery;
import com.antgroup.openspg.cloudext.interfaces.searchengine.model.request.query.FullTextSearchQuery;
import com.antgroup.openspg.cloudext.interfaces.searchengine.model.request.query.MatchQuery;
import com.antgroup.openspg.cloudext.interfaces.searchengine.model.request.query.OperatorType;
import com.antgroup.openspg.cloudext.interfaces.searchengine.model.request.query.QueryGroup;
import com.antgroup.openspg.cloudext.interfaces.searchengine.model.request.query.VectorSearchQuery;
import com.antgroup.openspg.common.util.neo4j.Neo4jCommonUtils;
import com.antgroup.openspg.core.schema.model.SPGSchemaAlterCmd;
import com.google.common.collect.Lists;
import com.vesoft.nebula.client.graph.SessionPool;
import com.vesoft.nebula.client.graph.SessionPoolConfig;
import com.vesoft.nebula.client.graph.data.HostAddress;
import com.vesoft.nebula.client.graph.data.ResultSet;
import com.vesoft.nebula.client.graph.data.ValueWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class NebulaSearchClient extends BaseIdxSearchEngineClient {

  private final SessionPool sessionPool;
  private final LPGTypeNameConvertor typeNameConvertor;

  @Getter private final String connUrl;
  @Getter private final String space;

  public NebulaSearchClient(String connUrl) {
    UriComponents uriComponents = UriComponentsBuilder.fromUriString(connUrl).build();
    this.connUrl = connUrl;
    this.space = resolveSpace(uriComponents, connUrl);
    this.typeNameConvertor = new DefaultLPGTypeNameConvertor();
    this.sessionPool = initSessionPool(uriComponents);
  }

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
          "nebula search url must specify space/namespace/database: " + connUrl);
    }
    return resolved;
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
    config.setMaxSessionSize(NebulaConstants.DEFAULT_MAX_SESSION_SIZE);
    String timeout = uriComponents.getQueryParams().getFirst(NebulaConstants.TIMEOUT);
    if (StringUtils.isNotBlank(timeout)) {
      config.setTimeout(Integer.parseInt(timeout));
    }
    SessionPool pool = new SessionPool(config);
    if (!pool.init()) {
      throw new RuntimeException("nebula search session pool init failed for space=" + space);
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

  @Override
  public List<IdxSchema> querySchema() {
    return Collections.emptyList();
  }

  @Override
  public int alterSchema(IdxSchemaAlterCmd cmd) {
    return 0;
  }

  @Override
  public int alterSchema(SPGSchemaAlterCmd cmd) {
    return alterSchemaWithVectorDimensions(cmd, Neo4jCommonUtils.DEFAULT_VECTOR_DIMENSIONS);
  }

  public int alterSchemaWithVectorDimensions(SPGSchemaAlterCmd cmd, int vectorDimensions) {
    if (cmd == null || cmd.getSpgSchema() == null) {
      return 0;
    }
    NebulaSearchIndexUtils.initializeSchema(
        cmd.getSpgSchema().getSpgTypes(),
        vectorDimensions,
        typeNameConvertor,
        this::executeQuietly);
    return 1;
  }

  @Override
  public int manipulateRecord(IdxRecordManipulateCmd cmd) {
    return 0;
  }

  @Override
  public void close() throws Exception {
    if (sessionPool != null) {
      sessionPool.close();
    }
  }

  @Override
  public IdxNameConvertor getIdxNameConvertor() {
    throw new RuntimeException("NebulaSearchClient does not support getIdxNameConvertor.");
  }

  @Override
  public List<IdxRecord> mGet(IdxGetQuery query) {
    throw new RuntimeException("NebulaSearchClient does not support mGet.");
  }

  @Override
  public List<IdxRecord> search(SearchRequest request) {
    if (request == null || request.getQuery() == null) {
      return Collections.emptyList();
    }
    BaseQuery query = request.getQuery();
    if (query instanceof QueryGroup) {
      return searchQueryGroup((QueryGroup) query, request.getFrom(), request.getSize());
    }
    if (query instanceof FullTextSearchQuery) {
      return searchFullText((FullTextSearchQuery) query, request.getFrom(), request.getSize());
    }
    if (query instanceof VectorSearchQuery) {
      return searchVector((VectorSearchQuery) query, request.getSize());
    }
    if (query instanceof CustomSearchQuery) {
      return searchCustom(((CustomSearchQuery) query).getCustomQuery());
    }
    throw new RuntimeException(
        "NebulaSearchClient supports QueryGroup, FullTextSearchQuery, VectorSearchQuery and CustomSearchQuery only.");
  }

  private List<IdxRecord> searchQueryGroup(QueryGroup queryGroup, int from, int size) {
    if (queryGroup.getOperator() != OperatorType.OR) {
      throw new RuntimeException(
          "NebulaSearchClient only supports OR QueryGroup for spgType search.");
    }
    LinkedHashMap<String, IdxRecord> merged = new LinkedHashMap<>();
    for (BaseQuery subQuery : queryGroup.getQueries()) {
      if (!(subQuery instanceof MatchQuery)) {
        continue;
      }
      MatchQuery matchQuery = (MatchQuery) subQuery;
      for (IdxRecord record : searchMatchQuery(matchQuery)) {
        merged.putIfAbsent(record.getDocId(), record);
      }
    }
    return paginate(new ArrayList<>(merged.values()), from, size);
  }

  private List<IdxRecord> searchMatchQuery(MatchQuery matchQuery) {
    String propertyName = matchQuery.getName();
    Object rawValue = matchQuery.getValue();
    if (StringUtils.isBlank(propertyName) || rawValue == null) {
      return Collections.emptyList();
    }
    String valueLiteral = NebulaValueUtils.literal(rawValue);
    List<IdxRecord> results = new ArrayList<>();
    for (String tagName : NebulaSearchIndexUtils.listEntityTags(this::execute)) {
      String statement =
          String.format(
              "LOOKUP ON %s WHERE %s.%s == %s YIELD vertex AS v",
              NebulaValueUtils.quoteName(tagName),
              NebulaValueUtils.quoteName(tagName),
              NebulaValueUtils.quoteName(propertyName),
              valueLiteral);
      try {
        results.addAll(parseVertexRecords(execute(statement), 1.0));
      } catch (RuntimeException e) {
        log.debug(
            "match lookup failed for tag={}, property={}, reason={}",
            tagName,
            propertyName,
            e.getMessage());
      }
    }
    return results;
  }

  private List<IdxRecord> searchFullText(FullTextSearchQuery query, int from, int size) {
    String queryString = query.getQueryString();
    if (StringUtils.isBlank(queryString)) {
      return Collections.emptyList();
    }
    List<String> tags = resolveTags(query.getLabelConstraints());
    List<String> propertyKeys =
        StringUtils.isNotBlank(query.getPropertyKey())
            ? Lists.newArrayList(query.getPropertyKey())
            : Lists.newArrayList("name", "nameEn", "orgName");
    LinkedHashMap<String, IdxRecord> merged = new LinkedHashMap<>();
    for (String tagName : tags) {
      boolean matched = false;
      for (String propertyKey : propertyKeys) {
        List<IdxRecord> fullTextHits =
            searchFullTextOnProperty(tagName, propertyKey, queryString, true);
        if (!fullTextHits.isEmpty()) {
          mergeRecords(merged, fullTextHits);
          matched = true;
        }
      }
      if (matched) {
        continue;
      }
      for (String propertyKey : propertyKeys) {
        mergeRecords(merged, searchFullTextOnProperty(tagName, propertyKey, queryString, false));
      }
    }
    return paginate(new ArrayList<>(merged.values()), from, size);
  }

  private void mergeRecords(LinkedHashMap<String, IdxRecord> merged, List<IdxRecord> records) {
    for (IdxRecord record : records) {
      merged.putIfAbsent(record.getDocId(), record);
    }
  }

  /** Try native FULLTEXT index first; fall back to LOOKUP exact/prefix match when unavailable. */
  private List<IdxRecord> searchFullTextOnProperty(
      String tagName, String propertyKey, String queryString, boolean useFullTextIndex) {
    String statement;
    if (useFullTextIndex) {
      statement =
          String.format(
              "LOOKUP ON %s WHERE FULLTEXT(%s.%s, %s) YIELD vertex AS v",
              NebulaValueUtils.quoteName(tagName),
              NebulaValueUtils.quoteName(tagName),
              NebulaValueUtils.quoteName(propertyKey),
              NebulaValueUtils.literal(queryString));
    } else {
      statement =
          String.format(
              "LOOKUP ON %s WHERE %s.%s == %s OR %s.%s STARTS WITH %s YIELD vertex AS v",
              NebulaValueUtils.quoteName(tagName),
              NebulaValueUtils.quoteName(tagName),
              NebulaValueUtils.quoteName(propertyKey),
              NebulaValueUtils.literal(queryString),
              NebulaValueUtils.quoteName(tagName),
              NebulaValueUtils.quoteName(propertyKey),
              NebulaValueUtils.literal(queryString));
    }
    try {
      return parseVertexRecords(execute(statement), 1.0);
    } catch (RuntimeException e) {
      if (useFullTextIndex) {
        log.debug(
            "fulltext lookup unavailable for tag={}, property={}, reason={}",
            tagName,
            propertyKey,
            e.getMessage());
        return Collections.emptyList();
      }
      log.debug(
          "lookup text fallback failed for tag={}, property={}, reason={}",
          tagName,
          propertyKey,
          e.getMessage());
      return Collections.emptyList();
    }
  }

  private List<IdxRecord> searchVector(VectorSearchQuery query, int topk) {
    if (topk <= 0) {
      topk = 10;
    }
    if (query.getQueryVector() == null || query.getQueryVector().length == 0) {
      return Collections.emptyList();
    }
    if (StringUtils.isBlank(query.getLabel())) {
      log.warn("vector search requires label constraint");
      return Collections.emptyList();
    }
    String tagName = typeNameConvertor.convertVertexTypeName(query.getLabel());
    String vectorField = NebulaSearchIndexUtils.vectorFieldName(query.getPropertyKey());
    String vectorLiteral = vectorLiteral(query.getQueryVector());
    String distanceExpr =
        String.format(
            "cosine(%s, v.%s.%s)",
            vectorLiteral,
            NebulaValueUtils.quoteName(tagName),
            NebulaValueUtils.quoteName(vectorField));
    String statement =
        String.format(
            "MATCH (v:%s) RETURN v, %s AS score ORDER BY %s APPROXIMATE LIMIT %d "
                + "OPTIONS {ANNINDEX_TYPE: \"HNSW\", METRIC_TYPE: \"INNER_PRODUCT\"}",
            NebulaValueUtils.quoteName(tagName), distanceExpr, distanceExpr, topk);
    try {
      return parseScoredVertexRecords(execute(statement));
    } catch (RuntimeException e) {
      log.warn(
          "vector search failed for label={}, property={}, reason={}",
          query.getLabel(),
          query.getPropertyKey(),
          e.getMessage());
      return Collections.emptyList();
    }
  }

  private List<IdxRecord> searchCustom(String customQuery) {
    if (StringUtils.isBlank(customQuery)) {
      return Collections.emptyList();
    }
    ResultSet resultSet = execute(customQuery);
    return parseCustomRecords(resultSet);
  }

  private List<String> resolveTags(List<String> labelConstraints) {
    if (CollectionUtils.isEmpty(labelConstraints)) {
      return new ArrayList<>(NebulaSearchIndexUtils.listEntityTags(this::execute));
    }
    return labelConstraints.stream()
        .map(typeNameConvertor::convertVertexTypeName)
        .collect(Collectors.toList());
  }

  private List<IdxRecord> parseVertexRecords(ResultSet resultSet, double defaultScore) {
    List<IdxRecord> records = new ArrayList<>();
    for (int i = 0; i < resultSet.rowsSize(); i++) {
      ValueWrapper vertexValue = firstVertexValue(resultSet, i);
      if (vertexValue == null || !vertexValue.isVertex()) {
        continue;
      }
      try {
        records.add(toIdxRecord(vertexValue.asNode(), defaultScore));
      } catch (Exception e) {
        throw new RuntimeException("failed to parse nebula search vertex", e);
      }
    }
    return records;
  }

  private List<IdxRecord> parseScoredVertexRecords(ResultSet resultSet) {
    List<IdxRecord> records = new ArrayList<>();
    for (int i = 0; i < resultSet.rowsSize(); i++) {
      ValueWrapper vertexValue = firstVertexValue(resultSet, i);
      if (vertexValue == null || !vertexValue.isVertex()) {
        continue;
      }
      double score = 1.0;
      if (resultSet.rowValues(i).contains("score")) {
        score = toDouble(resultSet.rowValues(i).get("score"), 1.0);
      }
      try {
        records.add(toIdxRecord(vertexValue.asNode(), score));
      } catch (Exception e) {
        throw new RuntimeException("failed to parse nebula vector search vertex", e);
      }
    }
    return records;
  }

  private List<IdxRecord> parseCustomRecords(ResultSet resultSet) {
    List<String> columns = resultSet.keys();
    if (columns.isEmpty()) {
      return Collections.emptyList();
    }
    List<IdxRecord> records = new ArrayList<>();
    for (int i = 0; i < resultSet.rowsSize(); i++) {
      ValueWrapper vertexValue = firstVertexValue(resultSet, i);
      if (vertexValue != null && vertexValue.isVertex()) {
        double score = columnScore(resultSet, i, columns);
        try {
          records.add(toIdxRecord(vertexValue.asNode(), score));
          continue;
        } catch (Exception e) {
          throw new RuntimeException("failed to parse custom search vertex", e);
        }
      }
      records.add(toIdxRecordFromColumns(resultSet, i, columns));
    }
    return records;
  }

  private IdxRecord toIdxRecordFromColumns(
      ResultSet resultSet, int rowIndex, List<String> columns) {
    Map<String, Object> fields = new HashMap<>();
    double score = 1.0;
    String docId = String.valueOf(rowIndex);
    for (String column : columns) {
      ValueWrapper value = resultSet.rowValues(rowIndex).get(column);
      if (value == null || value.isEmpty()) {
        continue;
      }
      if ("score".equalsIgnoreCase(column)) {
        score = toDouble(value, 1.0);
        continue;
      }
      if ("id".equalsIgnoreCase(column) && value.isString()) {
        docId = NebulaValueUtils.asString(value);
      }
      fields.put(column, NebulaValueUtils.unwrap(value));
    }
    return new IdxRecord(null, docId, score, fields);
  }

  private double columnScore(ResultSet resultSet, int rowIndex, List<String> columns) {
    for (String column : columns) {
      if ("score".equalsIgnoreCase(column)) {
        return toDouble(resultSet.rowValues(rowIndex).get(column), 1.0);
      }
    }
    return 1.0;
  }

  private ValueWrapper firstVertexValue(ResultSet resultSet, int rowIndex) {
    List<String> preferredColumns = Lists.newArrayList("v", "node", "vertex");
    for (String column : preferredColumns) {
      if (resultSet.rowValues(rowIndex).contains(column)) {
        return resultSet.rowValues(rowIndex).get(column);
      }
    }
    for (String column : resultSet.keys()) {
      ValueWrapper value = resultSet.rowValues(rowIndex).get(column);
      if (value != null && value.isVertex()) {
        return value;
      }
    }
    return null;
  }

  private IdxRecord toIdxRecord(com.vesoft.nebula.client.graph.data.Node node, double score)
      throws Exception {
    String docId = NebulaValueUtils.asString(node.getId());
    String tagInStore = node.tagNames().get(0);
    String label = typeNameConvertor.restoreVertexTypeName(tagInStore);
    Map<String, Object> fields = new HashMap<>();
    Map<String, ValueWrapper> properties = node.properties(tagInStore);
    for (Map.Entry<String, ValueWrapper> entry : properties.entrySet()) {
      Object value = NebulaValueUtils.unwrap(entry.getValue());
      if (value != null) {
        fields.put(entry.getKey(), value);
      }
    }
    fields.put(Neo4jCommonUtils.LABELS_KEY, new String[] {label});
    IdxRecord record = new IdxRecord(null, docId, score, fields);
    record.setLabel(label);
    return record;
  }

  private static double toDouble(ValueWrapper wrapper, double defaultValue) {
    if (wrapper == null || wrapper.isEmpty()) {
      return defaultValue;
    }
    try {
      if (wrapper.isDouble()) {
        return wrapper.asDouble();
      }
      if (wrapper.isLong()) {
        return wrapper.asLong();
      }
      return Double.parseDouble(wrapper.asString());
    } catch (Exception e) {
      return defaultValue;
    }
  }

  private static String vectorLiteral(float[] queryVector) {
    StringBuilder sb = new StringBuilder("vector(");
    for (int i = 0; i < queryVector.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(String.format("%.6g", queryVector[i]));
    }
    sb.append(')');
    return sb.toString();
  }

  private static List<IdxRecord> paginate(List<IdxRecord> records, int from, int size) {
    if (size <= 0) {
      return records;
    }
    int start = Math.max(from, 0);
    if (start >= records.size()) {
      return Collections.emptyList();
    }
    int end = Math.min(start + size, records.size());
    return records.subList(start, end);
  }

  private ResultSet execute(String statement) {
    try {
      ResultSet resultSet = sessionPool.execute(statement);
      if (!resultSet.isSucceeded()) {
        throw new RuntimeException(
            String.format(
                "nebula search failed, error=%s, statement=%s",
                resultSet.getErrorMessage(), statement));
      }
      return resultSet;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("nebula search execution error, statement=" + statement, e);
    }
  }

  private boolean executeQuietly(String statement) {
    try {
      ResultSet resultSet = sessionPool.execute(statement);
      if (!resultSet.isSucceeded()) {
        log.warn("nebula index ddl failed: {} -> {}", statement, resultSet.getErrorMessage());
        return false;
      }
      return true;
    } catch (Exception e) {
      log.warn("nebula index ddl error: {} -> {}", statement, e.getMessage());
      return false;
    }
  }
}
