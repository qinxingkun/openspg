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

package com.antgroup.openspg.cloudext.impl.graphstore.nebula.convertor;

import com.antgroup.openspg.cloudext.impl.graphstore.nebula.util.NebulaValueUtils;
import com.antgroup.openspg.cloudext.interfaces.graphstore.LPGTypeNameConvertor;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.EdgeRecord;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.LPGPropertyRecord;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.VertexRecord;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.EdgeType;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.EdgeTypeName;
import com.antgroup.openspg.core.schema.model.type.BasicTypeEnum;
import com.vesoft.nebula.client.graph.data.Node;
import com.vesoft.nebula.client.graph.data.Relationship;
import com.vesoft.nebula.client.graph.data.ValueWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts NebulaGraph {@link Node} / {@link Relationship} results into LPG records. */
public class NebulaRecordConvertor {

  private NebulaRecordConvertor() {}

  public static VertexRecord toVertexRecord(Node node, LPGTypeNameConvertor convertor) {
    try {
      String tagInStore = node.tagNames().get(0);
      String vertexType = convertor.restoreVertexTypeName(tagInStore);
      String id = NebulaValueUtils.asString(node.getId());

      List<LPGPropertyRecord> propertyRecords = new ArrayList<>();
      Map<String, ValueWrapper> properties = node.properties(tagInStore);
      for (Map.Entry<String, ValueWrapper> entry : properties.entrySet()) {
        Object value = NebulaValueUtils.unwrap(entry.getValue());
        if (value != null) {
          propertyRecords.add(new LPGPropertyRecord(entry.getKey(), value));
        }
      }
      return new VertexRecord(id, vertexType, propertyRecords);
    } catch (Exception e) {
      throw new RuntimeException("failed to convert nebula node to vertex record", e);
    }
  }

  public static EdgeRecord toEdgeRecord(Relationship relationship, LPGTypeNameConvertor convertor) {
    try {
      String edgeNameInStore = relationship.edgeName();
      EdgeTypeName edgeTypeName = convertor.restoreEdgeTypeName(edgeNameInStore);
      String srcId = NebulaValueUtils.asString(relationship.srcId());
      String dstId = NebulaValueUtils.asString(relationship.dstId());

      Long version = null;
      List<LPGPropertyRecord> propertyRecords = new ArrayList<>();
      Map<String, ValueWrapper> properties = relationship.properties();
      for (Map.Entry<String, ValueWrapper> entry : properties.entrySet()) {
        Object value = NebulaValueUtils.unwrap(entry.getValue());
        if (value == null) {
          continue;
        }
        if (EdgeType.VERSION.equals(entry.getKey())) {
          version = value instanceof Number ? ((Number) value).longValue() : null;
          continue;
        }
        propertyRecords.add(new LPGPropertyRecord(entry.getKey(), value));
      }
      return new EdgeRecord(srcId, dstId, edgeTypeName, propertyRecords, version);
    } catch (Exception e) {
      throw new RuntimeException("failed to convert nebula relationship to edge record", e);
    }
  }

  /** Map a NebulaGraph property type keyword (from {@code DESCRIBE}) to an SPG basic type. */
  public static BasicTypeEnum toBasicType(String nebulaType) {
    if (nebulaType == null) {
      return BasicTypeEnum.TEXT;
    }
    String type = nebulaType.toLowerCase();
    if (type.startsWith("int") || type.equals("timestamp")) {
      return BasicTypeEnum.LONG;
    }
    if (type.startsWith("float") || type.startsWith("double")) {
      return BasicTypeEnum.DOUBLE;
    }
    return BasicTypeEnum.TEXT;
  }
}
