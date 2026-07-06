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
package com.antgroup.openspg.common.util.jdbc;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.antgroup.openspg.common.constants.BuilderConstant;
import com.antgroup.openspg.common.util.CommonUtils;
import com.antgroup.openspg.common.util.ECBUtil;
import com.antgroup.openspg.common.util.constants.CommonConstant;
import com.antgroup.openspg.server.common.model.bulider.BuilderJob;
import com.antgroup.openspg.server.common.model.datasource.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

@Slf4j
public class MySqlDataScanner {

  private static final String MYSQL_DRIVER = "com.mysql.jdbc.Driver";
  private static final int DEFAULT_BATCH_SIZE = 5000;

  private MySqlDataScanner() {}

  public static List<Map<String, Object>> scan(BuilderJob builderJob) {
    JSONObject extension = JSON.parseObject(builderJob.getExtension());
    JSONObject dataSourceConfig = extension.getJSONObject(BuilderConstant.DATASOURCE_CONFIG);
    Assert.notNull(dataSourceConfig, "dataSourceConfig is required for MySQL scanner");

    DataSource dataSource =
        JSON.parseObject(dataSourceConfig.getString(BuilderConstant.DATASOURCE), DataSource.class);
    String database = dataSourceConfig.getString(BuilderConstant.DATABASE);
    String table = dataSourceConfig.getString(BuilderConstant.TABLE);
    String where = dataSourceConfig.getString("where");
    int batchSize =
        dataSourceConfig.containsKey("batchSize")
            ? dataSourceConfig.getIntValue("batchSize")
            : DEFAULT_BATCH_SIZE;

    Assert.notNull(dataSource, "dataSource is required for MySQL scanner");
    Assert.hasText(database, "database is required for MySQL scanner");
    Assert.hasText(table, "table is required for MySQL scanner");
    Assert.isTrue(
        batchSize > 0 && batchSize <= CommonUtils.INNER_QUERY_MAX_COUNT,
        "batchSize must be between 1 and " + CommonUtils.INNER_QUERY_MAX_COUNT);

    String password = ECBUtil.decrypt(dataSource.getEncrypt(), CommonConstant.ECB_PASSWORD_KEY);
    List<Map<String, Object>> results = new ArrayList<>();
    int offset = 0;

    while (true) {
      String sql = buildSelectSql(database, table, where, offset, batchSize);
      List<Map<String, Object>> batch =
          executeQuery(dataSource.getDbUrl(), dataSource.getDbUser(), password, sql);
      if (batch.isEmpty()) {
        break;
      }
      results.addAll(batch);
      if (batch.size() < batchSize) {
        break;
      }
      offset += batchSize;
    }

    log.info("MySQL scan complete. database:{} table:{} rows:{}", database, table, results.size());
    return results;
  }

  static String buildSelectSql(
      String database, String table, String where, int offset, int batchSize) {
    StringBuilder sql =
        new StringBuilder("SELECT * FROM `")
            .append(escapeIdentifier(database))
            .append("`.`")
            .append(escapeIdentifier(table))
            .append("`");
    if (StringUtils.isNotBlank(where)) {
      sql.append(" WHERE ").append(where.trim());
    }
    sql.append(" LIMIT ").append(offset).append(", ").append(batchSize);
    return sql.toString();
  }

  static List<Map<String, Object>> executeQuery(
      String jdbcUrl, String user, String password, String sql) {
    Connection conn = null;
    Statement stmt = null;
    ResultSet res = null;
    try {
      Class.forName(MYSQL_DRIVER);
      conn = DriverManager.getConnection(jdbcUrl, user, password);
      stmt = conn.createStatement();
      res = stmt.executeQuery(sql);
      ResultSetMetaData metaData = res.getMetaData();
      int columnCount = metaData.getColumnCount();
      List<Map<String, Object>> rows = new ArrayList<>();
      while (res.next()) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 1; i <= columnCount; i++) {
          row.put(metaData.getColumnLabel(i), res.getObject(i));
        }
        rows.add(row);
      }
      return rows;
    } catch (Exception e) {
      log.error("MySQL executeQuery failed. sql:{}", sql, e);
      throw new RuntimeException("MySQL executeQuery Exception:" + e.getMessage(), e);
    } finally {
      closeQuietly(res);
      closeQuietly(stmt);
      closeQuietly(conn);
    }
  }

  static String escapeIdentifier(String identifier) {
    return identifier == null ? "" : identifier.replace("`", "``");
  }

  private static void closeQuietly(AutoCloseable closeable) {
    if (closeable == null) {
      return;
    }
    try {
      closeable.close();
    } catch (Exception e) {
      log.warn("Failed to close resource", e);
    }
  }
}
