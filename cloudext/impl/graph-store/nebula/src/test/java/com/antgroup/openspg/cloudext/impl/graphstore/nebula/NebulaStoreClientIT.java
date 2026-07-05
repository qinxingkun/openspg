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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.antgroup.openspg.cloudext.interfaces.graphstore.cmd.OneHopLPGRecordQuery;
import com.antgroup.openspg.cloudext.interfaces.graphstore.cmd.VertexLPGRecordQuery;
import com.antgroup.openspg.cloudext.interfaces.graphstore.impl.DefaultLPGTypeNameConvertor;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.Direction;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.EdgeRecord;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.LPGPropertyRecord;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.VertexRecord;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.struct.GraphLPGRecordStruct;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.EdgeTypeName;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.LPGProperty;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.operation.CreateEdgeTypeOperation;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.operation.CreateVertexTypeOperation;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.operation.DropEdgeTypeOperation;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.operation.DropVertexTypeOperation;
import com.antgroup.openspg.core.schema.model.type.BasicTypeEnum;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration test against a live NebulaGraph / trsgraph instance. It is intentionally
 * <b>not</b> matched by the default surefire {@code *Test} pattern, so it is skipped during normal
 * builds. Run it explicitly and provide the connection url, e.g.:
 *
 * <pre>
 *   mvn -pl cloudext/impl/graph-store/nebula test -Dtest=NebulaStoreClientIT \
 *       -Dnebula.it.url="nebula://127.0.0.1:9677?user=root&password=nebula&space=spg_test"
 * </pre>
 *
 * The target {@code space} must already exist (create it once with {@code CREATE SPACE
 * spg_test(vid_type=FIXED_STRING(256))}).
 */
public class NebulaStoreClientIT {

  private static final String VERTEX_TYPE = "SmokeTest.Person";
  private static final EdgeTypeName EDGE_TYPE = new EdgeTypeName(VERTEX_TYPE, "knows", VERTEX_TYPE);

  private NebulaStoreClient client;

  @BeforeEach
  public void setUp() {
    String url = System.getProperty("nebula.it.url");
    assumeTrue(url != null && !url.isEmpty(), "skip nebula IT: -Dnebula.it.url not set");
    client = new NebulaStoreClient(url, new DefaultLPGTypeNameConvertor());
  }

  @Test
  public void testEndToEnd() throws Exception {
    dropQuietly();
    try {
      client.createVertexType(buildCreateVertexOperation());
      client.createEdgeType(buildCreateEdgeOperation());

      VertexRecord tom =
          new VertexRecord(
              "p1",
              VERTEX_TYPE,
              Lists.newArrayList(
                  new LPGPropertyRecord("name", "Tom"), new LPGPropertyRecord("age", 42L)));
      VertexRecord jerry =
          new VertexRecord(
              "p2",
              VERTEX_TYPE,
              Lists.newArrayList(
                  new LPGPropertyRecord("name", "Jerry"), new LPGPropertyRecord("age", 30L)));
      client.upsertVertex(VERTEX_TYPE, Lists.newArrayList(tom, jerry));

      EdgeRecord edge =
          new EdgeRecord(
              "p1", "p2", EDGE_TYPE, Lists.newArrayList(new LPGPropertyRecord("since", 2020L)));
      client.upsertEdge(EDGE_TYPE.toString(), Lists.newArrayList(edge), true);

      GraphLPGRecordStruct single =
          (GraphLPGRecordStruct) client.queryRecord(new VertexLPGRecordQuery("p1", VERTEX_TYPE));
      assertEquals(1, single.getVertices().size());
      assertEquals("p1", single.getVertices().get(0).getId());

      GraphLPGRecordStruct oneHop =
          (GraphLPGRecordStruct)
              client.queryRecord(new OneHopLPGRecordQuery("p1", VERTEX_TYPE, null, Direction.OUT));
      assertFalse(oneHop.getEdges().isEmpty());
      assertEquals("p1", oneHop.getEdges().get(0).getSrcId());
      assertEquals("p2", oneHop.getEdges().get(0).getDstId());
    } finally {
      dropQuietly();
      client.close();
    }
  }

  private CreateVertexTypeOperation buildCreateVertexOperation() {
    CreateVertexTypeOperation operation = new CreateVertexTypeOperation(VERTEX_TYPE);
    operation.addProperty(new LPGProperty("id", BasicTypeEnum.TEXT));
    operation.addProperty(new LPGProperty("name", BasicTypeEnum.TEXT));
    operation.addProperty(new LPGProperty("age", BasicTypeEnum.LONG));
    return operation;
  }

  private CreateEdgeTypeOperation buildCreateEdgeOperation() {
    CreateEdgeTypeOperation operation = new CreateEdgeTypeOperation(EDGE_TYPE);
    operation.addProperty(new LPGProperty("srcId", BasicTypeEnum.TEXT));
    operation.addProperty(new LPGProperty("dstId", BasicTypeEnum.TEXT));
    operation.addProperty(new LPGProperty("version", BasicTypeEnum.LONG));
    operation.addProperty(new LPGProperty("since", BasicTypeEnum.LONG));
    return operation;
  }

  private void dropQuietly() {
    try {
      client.dropEdgeType(new DropEdgeTypeOperation(EDGE_TYPE));
    } catch (Exception ignored) {
      // best-effort cleanup
    }
    try {
      client.dropVertexType(new DropVertexTypeOperation(VERTEX_TYPE));
    } catch (Exception ignored) {
      // best-effort cleanup
    }
  }
}
