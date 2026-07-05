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

import com.antgroup.openspg.cloudext.impl.graphstore.nebula.util.NebulaRecordUtils;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.EdgeRecord;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.LPGPropertyRecord;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.record.VertexRecord;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.EdgeTypeName;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class NebulaRecordUtilsTest {

  @Test
  public void testUpsertSingleVertexWithOneProperty() {
    VertexRecord record =
        new VertexRecord("1", "Person", Lists.newArrayList(new LPGPropertyRecord("name", "Tom")));
    List<String> statements =
        NebulaRecordUtils.upsertVertexStatements("Person", Lists.newArrayList(record));
    assertEquals(1, statements.size());
    assertEquals("INSERT VERTEX `Person` (`name`) VALUES \"1\":(\"Tom\")", statements.get(0));
  }

  @Test
  public void testUpsertVertexWithoutProperties() {
    VertexRecord record = new VertexRecord("1", "Person");
    List<String> statements =
        NebulaRecordUtils.upsertVertexStatements("Person", Lists.newArrayList(record));
    assertEquals(1, statements.size());
    assertEquals("INSERT VERTEX `Person` () VALUES \"1\":()", statements.get(0));
  }

  @Test
  public void testUpsertVertexBatching() {
    List<VertexRecord> records = new ArrayList<>();
    for (int i = 0; i < 250; i++) {
      records.add(
          new VertexRecord(
              String.valueOf(i),
              "Person",
              Lists.newArrayList(new LPGPropertyRecord("name", "n" + i))));
    }
    List<String> statements = NebulaRecordUtils.upsertVertexStatements("Person", records);
    // 250 records / batch size 200 => 2 statements (same property signature)
    assertEquals(2, statements.size());
  }

  @Test
  public void testDeleteVertex() {
    List<VertexRecord> records =
        Lists.newArrayList(new VertexRecord("1", "Person"), new VertexRecord("2", "Person"));
    List<String> statements = NebulaRecordUtils.deleteVertexStatements(records);
    assertEquals(1, statements.size());
    assertEquals("DELETE VERTEX \"1\", \"2\" WITH EDGE", statements.get(0));
  }

  @Test
  public void testUpsertEdgeWithVersionOnly() {
    EdgeTypeName edgeTypeName = new EdgeTypeName("Person", "knows", "Person");
    EdgeRecord record = new EdgeRecord("s", "d", edgeTypeName, new ArrayList<>());
    List<String> statements =
        NebulaRecordUtils.upsertEdgeStatements("Person__knows__Person", Lists.newArrayList(record));
    assertEquals(1, statements.size());
    // version defaults to 0 and is always materialized as a column
    assertEquals(
        "INSERT EDGE `Person__knows__Person` (`version`) VALUES \"s\"->\"d\":(0)",
        statements.get(0));
  }

  @Test
  public void testDeleteEdge() {
    EdgeTypeName edgeTypeName = new EdgeTypeName("Person", "knows", "Person");
    EdgeRecord record = new EdgeRecord("s", "d", edgeTypeName, new ArrayList<>());
    List<String> statements =
        NebulaRecordUtils.deleteEdgeStatements("Person__knows__Person", Lists.newArrayList(record));
    assertEquals(1, statements.size());
    assertEquals("DELETE EDGE `Person__knows__Person` \"s\"->\"d\"", statements.get(0));
  }
}
