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
package com.antgroup.openspg.server.common.service.datasource.meta.client.impl;

import com.alibaba.fastjson.JSON;
import com.antgroup.openspg.server.common.model.datasource.Column;
import com.antgroup.openspg.server.common.service.datasource.meta.client.CloudDataSource;
import com.antgroup.openspg.server.common.service.datasource.meta.client.DataSourceMetaClient;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class MySqlMetaClientImpl implements DataSourceMetaClient {

  private static final String MYSQL_DRIVER = "com.mysql.jdbc.Driver";

  @Override
  public List<Column> describeTable(CloudDataSource dataSource, String database, String tableName) {
    List<Column> columns = new ArrayList<>();
    Connection conn = null;
    PreparedStatement stmt = null;
    ResultSet res = null;
    try {
      conn = JdbcClient.getClient(dataSource, MYSQL_DRIVER);
      stmt =
          conn.prepareStatement(
              "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT "
                  + "FROM information_schema.COLUMNS "
                  + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
                  + "ORDER BY ORDINAL_POSITION");
      stmt.setString(1, database);
      stmt.setString(2, tableName);
      res = stmt.executeQuery();
      while (res.next()) {
        columns.add(
            new Column(res.getString(1), res.getString(2), StringUtils.defaultString(res.getString(3))));
      }
    } catch (Exception e) {
      log.warn(
          "mysql describeTable Exception: {} {} {}",
          JSON.toJSONString(dataSource),
          database,
          tableName,
          e);
      throw new RuntimeException("mysql describeTable Exception:" + e.getMessage(), e);
    } finally {
      JdbcClient.closeResultSet(res);
      JdbcClient.closeStatement(stmt);
      JdbcClient.closeConnection(conn);
    }
    return columns;
  }

  @Override
  public List<String> showDatabases(CloudDataSource dataSource) {
    List<String> dbs = new ArrayList<>();
    Connection conn = null;
    Statement stmt = null;
    ResultSet res = null;
    try {
      conn = JdbcClient.getClient(dataSource, MYSQL_DRIVER);
      stmt = conn.createStatement();
      res = stmt.executeQuery("SHOW DATABASES");
      while (res.next()) {
        dbs.add(res.getString(1));
      }
    } catch (Exception e) {
      log.warn("mysql showDatabases Exception: {}", JSON.toJSONString(dataSource), e);
      throw new RuntimeException("mysql showDatabases Exception:" + e.getMessage(), e);
    } finally {
      JdbcClient.closeResultSet(res);
      JdbcClient.closeStatement(stmt);
      JdbcClient.closeConnection(conn);
    }
    return dbs;
  }

  @Override
  public List<String> showTables(CloudDataSource dataSource, String database, String keyword) {
    List<String> tables = new ArrayList<>();
    Connection conn = null;
    Statement stmt = null;
    ResultSet res = null;
    try {
      conn = JdbcClient.getClient(dataSource, MYSQL_DRIVER);
      stmt = conn.createStatement();
      stmt.execute("USE `" + escapeIdentifier(database) + "`");
      res = stmt.executeQuery("SHOW TABLES");
      while (res.next()) {
        String name = res.getString(1);
        if (StringUtils.isBlank(keyword) || name.contains(keyword)) {
          tables.add(name);
        }
      }
    } catch (Exception e) {
      log.warn(
          "mysql showTables Exception: {} {}", JSON.toJSONString(dataSource), database, e);
      throw new RuntimeException("mysql showTables Exception:" + e.getMessage(), e);
    } finally {
      JdbcClient.closeResultSet(res);
      JdbcClient.closeStatement(stmt);
      JdbcClient.closeConnection(conn);
    }
    return tables;
  }

  @Override
  public Boolean isPartitionTable(CloudDataSource dataSource, String database, String tableName) {
    return Boolean.FALSE;
  }

  @Override
  public Boolean testConnect(CloudDataSource dataSource) {
    Connection conn = null;
    Statement stmt = null;
    try {
      conn = JdbcClient.getClient(dataSource, MYSQL_DRIVER);
      stmt = conn.createStatement();
      stmt.executeQuery("SELECT 1");
    } catch (Exception e) {
      log.warn("mysql testConnect Exception: {}", JSON.toJSONString(dataSource), e);
      throw new RuntimeException("mysql testConnect Exception:" + e.getMessage(), e);
    } finally {
      JdbcClient.closeStatement(stmt);
      JdbcClient.closeConnection(conn);
    }
    return Boolean.TRUE;
  }

  @Override
  public List<Map<String, Object>> sampleDateForPartition(
      CloudDataSource dataSource,
      String dataSourceId,
      String partitionStr,
      String bizDate,
      Integer limit) {
    throw new UnsupportedOperationException("MySQL data source does not support partition sampling");
  }

  @Override
  public Boolean hasPartition(
      CloudDataSource dataSource, String dataSourceId, String partitionStr, String bizDate) {
    return Boolean.FALSE;
  }

  @Override
  public Long getRecordCount(
      CloudDataSource dataSource, String dataSourceId, String partitionStr, String bizDate) {
    throw new UnsupportedOperationException("MySQL data source does not support partition count");
  }

  @Override
  public List<String> getAllPartitions(CloudDataSource dataSource, String dataSourceId) {
    return Collections.emptyList();
  }

  static String escapeIdentifier(String identifier) {
    return identifier == null ? "" : identifier.replace("`", "``");
  }
}
