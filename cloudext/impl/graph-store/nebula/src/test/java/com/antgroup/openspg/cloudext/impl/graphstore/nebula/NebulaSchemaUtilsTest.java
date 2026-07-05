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

import com.antgroup.openspg.cloudext.impl.graphstore.nebula.util.NebulaSchemaUtils;
import com.antgroup.openspg.cloudext.interfaces.graphstore.model.lpg.schema.LPGProperty;
import com.antgroup.openspg.core.schema.model.type.BasicTypeEnum;
import com.google.common.collect.Lists;
import java.util.List;
import org.junit.jupiter.api.Test;

public class NebulaSchemaUtilsTest {

  private final List<LPGProperty> properties =
      Lists.newArrayList(
          new LPGProperty("name", BasicTypeEnum.TEXT), new LPGProperty("age", BasicTypeEnum.LONG));

  @Test
  public void testCreateTag() {
    assertEquals(
        "CREATE TAG IF NOT EXISTS `Person` (`name` string NULL, `age` int64 NULL)",
        NebulaSchemaUtils.createTag("Person", properties));
  }

  @Test
  public void testCreateTagWithoutProperties() {
    assertEquals(
        "CREATE TAG IF NOT EXISTS `Person` ()",
        NebulaSchemaUtils.createTag("Person", Lists.newArrayList()));
  }

  @Test
  public void testCreateEdge() {
    assertEquals(
        "CREATE EDGE IF NOT EXISTS `Person__knows__Person` (`name` string NULL, `age` int64 NULL)",
        NebulaSchemaUtils.createEdge("Person__knows__Person", properties));
  }

  @Test
  public void testCreateAndRebuildScanIndex() {
    assertEquals(
        "CREATE TAG INDEX IF NOT EXISTS `Person_scan_idx` ON `Person` ()",
        NebulaSchemaUtils.createTagScanIndex("Person"));
    assertEquals(
        "REBUILD TAG INDEX `Person_scan_idx`", NebulaSchemaUtils.rebuildTagIndex("Person"));
    assertEquals(
        "CREATE EDGE INDEX IF NOT EXISTS `knows_scan_idx` ON `knows` ()",
        NebulaSchemaUtils.createEdgeScanIndex("knows"));
  }

  @Test
  public void testAlterTag() {
    assertEquals(
        "ALTER TAG `Person` ADD (`name` string NULL, `age` int64 NULL)",
        NebulaSchemaUtils.alterTagAddProperties("Person", properties));
    assertEquals(
        "ALTER TAG `Person` DROP (`name`, `age`)",
        NebulaSchemaUtils.alterTagDropProperties("Person", Lists.newArrayList("name", "age")));
  }

  @Test
  public void testDropAndDescribe() {
    assertEquals("DROP TAG IF EXISTS `Person`", NebulaSchemaUtils.dropTag("Person"));
    assertEquals("DROP EDGE IF EXISTS `knows`", NebulaSchemaUtils.dropEdge("knows"));
    assertEquals("DESCRIBE TAG `Person`", NebulaSchemaUtils.describeTag("Person"));
    assertEquals("DESCRIBE EDGE `knows`", NebulaSchemaUtils.describeEdge("knows"));
  }
}
