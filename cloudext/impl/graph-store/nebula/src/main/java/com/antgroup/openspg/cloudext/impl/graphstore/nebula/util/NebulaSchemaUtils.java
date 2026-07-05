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

package com.antgroup.openspg.cloudext.impl.graphstore.nebula.util;

import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.LPGProperty;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds nGQL DDL statements (CREATE / ALTER / DROP TAG / EDGE and TAG / EDGE INDEX) from the LPG
 * schema operation model.
 *
 * <p>NebulaGraph uses the vertex id (VID) as the identity of a vertex and the {@code src -> dst}
 * pair as the identity of an edge, so the built-in {@code id / srcId / dstId} properties are never
 * materialized as columns.
 *
 * <p>All columns are declared nullable on purpose: SPG properties are frequently sparse, and the
 * VID / endpoints already guarantee identity, so we avoid {@code NOT NULL} to keep upserts robust.
 */
public class NebulaSchemaUtils {

  private NebulaSchemaUtils() {}

  public static String createTag(String tagName, List<LPGProperty> properties) {
    return String.format(
        "CREATE TAG IF NOT EXISTS %s (%s)",
        NebulaValueUtils.quoteName(tagName), propertyDefs(properties));
  }

  public static String createEdge(String edgeName, List<LPGProperty> properties) {
    return String.format(
        "CREATE EDGE IF NOT EXISTS %s (%s)",
        NebulaValueUtils.quoteName(edgeName), propertyDefs(properties));
  }

  /** An empty (tag-existence) index so that {@code LOOKUP}-based scans over the tag can run. */
  public static String createTagScanIndex(String tagName) {
    return String.format(
        "CREATE TAG INDEX IF NOT EXISTS %s ON %s ()",
        NebulaValueUtils.quoteName(indexName(tagName)), NebulaValueUtils.quoteName(tagName));
  }

  public static String createEdgeScanIndex(String edgeName) {
    return String.format(
        "CREATE EDGE INDEX IF NOT EXISTS %s ON %s ()",
        NebulaValueUtils.quoteName(indexName(edgeName)), NebulaValueUtils.quoteName(edgeName));
  }

  public static String rebuildTagIndex(String tagName) {
    return String.format("REBUILD TAG INDEX %s", NebulaValueUtils.quoteName(indexName(tagName)));
  }

  public static String rebuildEdgeIndex(String edgeName) {
    return String.format("REBUILD EDGE INDEX %s", NebulaValueUtils.quoteName(indexName(edgeName)));
  }

  public static String indexName(String typeName) {
    return typeName + "_scan_idx";
  }

  public static String alterTagAddProperties(String tagName, List<LPGProperty> properties) {
    return String.format(
        "ALTER TAG %s ADD (%s)", NebulaValueUtils.quoteName(tagName), propertyDefs(properties));
  }

  public static String alterEdgeAddProperties(String edgeName, List<LPGProperty> properties) {
    return String.format(
        "ALTER EDGE %s ADD (%s)", NebulaValueUtils.quoteName(edgeName), propertyDefs(properties));
  }

  public static String alterTagDropProperties(String tagName, List<String> propertyNames) {
    return String.format(
        "ALTER TAG %s DROP (%s)", NebulaValueUtils.quoteName(tagName), quotedNames(propertyNames));
  }

  public static String alterEdgeDropProperties(String edgeName, List<String> propertyNames) {
    return String.format(
        "ALTER EDGE %s DROP (%s)",
        NebulaValueUtils.quoteName(edgeName), quotedNames(propertyNames));
  }

  public static String dropTag(String tagName) {
    return String.format("DROP TAG IF EXISTS %s", NebulaValueUtils.quoteName(tagName));
  }

  public static String dropEdge(String edgeName) {
    return String.format("DROP EDGE IF EXISTS %s", NebulaValueUtils.quoteName(edgeName));
  }

  public static String describeTag(String tagName) {
    return String.format("DESCRIBE TAG %s", NebulaValueUtils.quoteName(tagName));
  }

  public static String describeEdge(String edgeName) {
    return String.format("DESCRIBE EDGE %s", NebulaValueUtils.quoteName(edgeName));
  }

  /**
   * A no-op {@code FETCH} used to test schema readiness at the <em>graphd</em> level. {@code
   * DESCRIBE} reads from the meta service and succeeds as soon as the DDL is committed, but graphd
   * only learns about the new schema after a heartbeat cycle, so DML issued in between fails with
   * "No schema found". This probe goes through graphd and returns an empty result once the schema
   * is usable.
   */
  public static String fetchProbeTag(String tagName) {
    return String.format(
        "FETCH PROP ON %s %s YIELD vertex AS v",
        NebulaValueUtils.quoteName(tagName), NebulaValueUtils.vid("__schema_probe__"));
  }

  public static String fetchProbeEdge(String edgeName) {
    return String.format(
        "FETCH PROP ON %s %s->%s YIELD edge AS e",
        NebulaValueUtils.quoteName(edgeName),
        NebulaValueUtils.vid("__schema_probe_src__"),
        NebulaValueUtils.vid("__schema_probe_dst__"));
  }

  private static String propertyDefs(List<LPGProperty> properties) {
    if (properties == null || properties.isEmpty()) {
      return "";
    }
    return properties.stream()
        .map(
            property ->
                String.format(
                    "%s %s NULL",
                    NebulaValueUtils.quoteName(property.getName()),
                    NebulaValueUtils.nebulaType(property.getType())))
        .collect(Collectors.joining(", "));
  }

  private static String quotedNames(List<String> names) {
    return names.stream().map(NebulaValueUtils::quoteName).collect(Collectors.joining(", "));
  }
}
