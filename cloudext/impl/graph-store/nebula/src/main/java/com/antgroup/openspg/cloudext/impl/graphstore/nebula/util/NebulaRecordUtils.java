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

import com.antgroup.openspg.cloudext.impl.graphstore.nebula.NebulaConstants;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.EdgeRecord;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.VertexRecord;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.EdgeType;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Builds batched nGQL DML statements ({@code INSERT / DELETE VERTEX / EDGE}) from LPG records.
 *
 * <p>NebulaGraph's {@code INSERT} has upsert-like semantics (overwrites the same VID / edge key),
 * so it is reused for both create and update.
 */
public class NebulaRecordUtils {

  private NebulaRecordUtils() {}

  public static List<String> upsertVertexStatements(String tagName, List<VertexRecord> records) {
    if (CollectionUtils.isEmpty(records)) {
      return Lists.newArrayList();
    }
    // Group by the (ordered) property name list so a single statement shares one column clause.
    Map<List<String>, List<VertexRecord>> grouped = new LinkedHashMap<>();
    for (VertexRecord record : records) {
      List<String> names = new ArrayList<>(record.toPropertyMap().keySet());
      grouped.computeIfAbsent(names, k -> new ArrayList<>()).add(record);
    }

    List<String> statements = new ArrayList<>();
    for (Map.Entry<List<String>, List<VertexRecord>> entry : grouped.entrySet()) {
      List<String> propNames = entry.getKey();
      String columnClause =
          propNames.stream().map(NebulaValueUtils::quoteName).collect(Collectors.joining(", "));
      for (List<VertexRecord> batch :
          Lists.partition(entry.getValue(), NebulaConstants.DML_BATCH_SIZE)) {
        String valueClause =
            batch.stream()
                .map(
                    record -> {
                      Map<String, Object> props = record.toPropertyMap();
                      String values =
                          propNames.stream()
                              .map(name -> NebulaValueUtils.literal(props.get(name)))
                              .collect(Collectors.joining(", "));
                      return String.format("%s:(%s)", NebulaValueUtils.vid(record.getId()), values);
                    })
                .collect(Collectors.joining(", "));
        statements.add(
            String.format(
                "INSERT VERTEX %s (%s) VALUES %s",
                NebulaValueUtils.quoteName(tagName), columnClause, valueClause));
      }
    }
    return statements;
  }

  public static List<String> deleteVertexStatements(List<VertexRecord> records) {
    if (CollectionUtils.isEmpty(records)) {
      return Lists.newArrayList();
    }
    List<String> statements = new ArrayList<>();
    for (List<VertexRecord> batch : Lists.partition(records, NebulaConstants.DML_BATCH_SIZE)) {
      String vids =
          batch.stream()
              .map(record -> NebulaValueUtils.vid(record.getId()))
              .collect(Collectors.joining(", "));
      statements.add(String.format("DELETE VERTEX %s WITH EDGE", vids));
    }
    return statements;
  }

  public static List<String> upsertEdgeStatements(String edgeName, List<EdgeRecord> records) {
    if (CollectionUtils.isEmpty(records)) {
      return Lists.newArrayList();
    }
    // version is stored as a regular column; srcId / dstId are the endpoints, not columns.
    Map<List<String>, List<EdgeRecord>> grouped = new LinkedHashMap<>();
    for (EdgeRecord record : records) {
      List<String> names = new ArrayList<>(record.toPropertyMap().keySet());
      names.add(EdgeType.VERSION);
      grouped.computeIfAbsent(names, k -> new ArrayList<>()).add(record);
    }

    List<String> statements = new ArrayList<>();
    for (Map.Entry<List<String>, List<EdgeRecord>> entry : grouped.entrySet()) {
      List<String> propNames = entry.getKey();
      String columnClause =
          propNames.stream().map(NebulaValueUtils::quoteName).collect(Collectors.joining(", "));
      for (List<EdgeRecord> batch :
          Lists.partition(entry.getValue(), NebulaConstants.DML_BATCH_SIZE)) {
        String valueClause =
            batch.stream()
                .map(
                    record -> {
                      Map<String, Object> props = record.toPropertyMap();
                      String values =
                          propNames.stream()
                              .map(
                                  name ->
                                      EdgeType.VERSION.equals(name)
                                          ? NebulaValueUtils.literal(record.getVersion())
                                          : NebulaValueUtils.literal(props.get(name)))
                              .collect(Collectors.joining(", "));
                      return String.format(
                          "%s->%s:(%s)",
                          NebulaValueUtils.vid(record.getSrcId()),
                          NebulaValueUtils.vid(record.getDstId()),
                          values);
                    })
                .collect(Collectors.joining(", "));
        statements.add(
            String.format(
                "INSERT EDGE %s (%s) VALUES %s",
                NebulaValueUtils.quoteName(edgeName), columnClause, valueClause));
      }
    }
    return statements;
  }

  public static List<String> deleteEdgeStatements(String edgeName, List<EdgeRecord> records) {
    if (CollectionUtils.isEmpty(records)) {
      return Lists.newArrayList();
    }
    List<String> statements = new ArrayList<>();
    for (List<EdgeRecord> batch : Lists.partition(records, NebulaConstants.DML_BATCH_SIZE)) {
      String edgeKeys =
          batch.stream()
              .map(
                  record ->
                      String.format(
                          "%s->%s",
                          NebulaValueUtils.vid(record.getSrcId()),
                          NebulaValueUtils.vid(record.getDstId())))
              .collect(Collectors.joining(", "));
      statements.add(
          String.format("DELETE EDGE %s %s", NebulaValueUtils.quoteName(edgeName), edgeKeys));
    }
    return statements;
  }
}
