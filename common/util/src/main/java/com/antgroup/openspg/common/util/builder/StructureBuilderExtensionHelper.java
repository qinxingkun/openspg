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

package com.antgroup.openspg.common.util.builder;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.antgroup.openspg.common.constants.BuilderConstant;
import com.antgroup.openspg.common.util.CommonUtils;
import com.antgroup.openspg.common.util.StringUtils;
import com.antgroup.openspg.common.util.constants.CommonConstant;
import com.antgroup.openspg.server.common.model.CommonEnum.DataSourceType;
import com.antgroup.openspg.server.common.model.datasource.DataSource;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.Assert;

public final class StructureBuilderExtensionHelper {

  private static final int DEFAULT_BATCH_SIZE = 5000;

  private StructureBuilderExtensionHelper() {}

  public static void validatePropertyMapping(Map<String, List<String>> propertyMapping) {
    Assert.notEmpty(propertyMapping, "propertyMapping is required");
    Assert.isTrue(propertyMapping.containsKey("id"), "propertyMapping must contain id property");
    List<String> idColumns = propertyMapping.get("id");
    Assert.notEmpty(idColumns, "propertyMapping id must map to at least one column");
    for (Map.Entry<String, List<String>> entry : propertyMapping.entrySet()) {
      Assert.hasText(entry.getKey(), "propertyMapping key cannot be blank");
      Assert.notEmpty(
          entry.getValue(), "propertyMapping value cannot be empty for " + entry.getKey());
      for (String column : entry.getValue()) {
        Assert.hasText(column, "propertyMapping column cannot be blank for " + entry.getKey());
      }
    }
  }

  public static void validateMySqlDataSource(DataSource dataSource) {
    Assert.notNull(dataSource, "dataSource not found");
    Assert.isTrue(DataSourceType.MySQL.equals(dataSource.getType()), "dataSource must be MySQL");
  }

  public static String resolveEntityTypeFilter(String namespace, String entityType) {
    Assert.hasText(entityType, "entityType is required");
    if (entityType.contains(BuilderConstant.DOT)) {
      return entityType;
    }
    Assert.hasText(namespace, "namespace is required when entityType has no prefix");
    return namespace + BuilderConstant.DOT + entityType;
  }

  public static JSONObject buildExtension(
      DataSource dataSource,
      String database,
      String table,
      String entityTypeFilter,
      Map<String, List<String>> propertyMapping,
      String where,
      Integer batchSize) {
    validateMySqlDataSource(dataSource);
    if (StringUtils.isBlank(dataSource.getEncrypt())
        && StringUtils.isNotBlank(dataSource.getDbPassword())
        && !CommonConstant.DEFAULT_PASSWORD.equals(dataSource.getDbPassword())) {
      dataSource.setEncrypt(dataSource.getDbPassword());
    }
    Assert.hasText(database, "database is required");
    Assert.hasText(table, "table is required");
    Assert.hasText(entityTypeFilter, "entityTypeFilter is required");
    validatePropertyMapping(propertyMapping);

    int resolvedBatchSize = batchSize == null || batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
    Assert.isTrue(
        resolvedBatchSize <= CommonUtils.INNER_QUERY_MAX_COUNT,
        "batchSize must be between 1 and " + CommonUtils.INNER_QUERY_MAX_COUNT);

    JSONObject extension = new JSONObject();

    JSONObject dataSourceConfig = new JSONObject();
    dataSourceConfig.put(BuilderConstant.STRUCTURE, Boolean.TRUE);
    dataSourceConfig.put(BuilderConstant.DATABASE, database);
    dataSourceConfig.put(BuilderConstant.TABLE, table);
    dataSourceConfig.put(BuilderConstant.DATASOURCE, JSON.toJSONString(dataSource));
    String encryptedPassword = dataSource.getEncrypt();
    if (StringUtils.isBlank(encryptedPassword)
        && StringUtils.isNotBlank(dataSource.getDbPassword())
        && !CommonConstant.DEFAULT_PASSWORD.equals(dataSource.getDbPassword())) {
      encryptedPassword = dataSource.getDbPassword();
    }
    if (StringUtils.isNotBlank(encryptedPassword)) {
      dataSourceConfig.put("encrypt", encryptedPassword);
    }
    if (StringUtils.isNotBlank(where)) {
      dataSourceConfig.put("where", where);
    }
    dataSourceConfig.put("batchSize", resolvedBatchSize);
    extension.put(BuilderConstant.DATASOURCE_CONFIG, dataSourceConfig);

    JSONObject mappingConfig = new JSONObject();
    mappingConfig.put(BuilderConstant.MAPPING_TYPE, BuilderConstant.ENTITY_MAPPING);

    JSONArray filter = new JSONArray();
    JSONObject filterItem = new JSONObject();
    filterItem.put(BuilderConstant.S, entityTypeFilter);
    filter.add(filterItem);
    mappingConfig.put(BuilderConstant.FILTER, filter);

    Map<String, List<String>> columnToProperties = invertPropertyMapping(propertyMapping);
    JSONObject configItem = new JSONObject();
    configItem.put(BuilderConstant.MAPPING, columnToProperties);
    mappingConfig.put(BuilderConstant.CONFIG, JSON.toJSONString(ImmutableList.of(configItem)));

    extension.put(BuilderConstant.MAPPING_CONFIG, mappingConfig);
    return extension;
  }

  static Map<String, List<String>> invertPropertyMapping(
      Map<String, List<String>> propertyMapping) {
    Map<String, List<String>> columnToProperties = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : propertyMapping.entrySet()) {
      String spgProperty = entry.getKey();
      for (String column : entry.getValue()) {
        columnToProperties.computeIfAbsent(column, key -> new ArrayList<>()).add(spgProperty);
      }
    }
    return columnToProperties;
  }
}
