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

import com.antgroup.openspg.cloudext.impl.graphstore.nebula.util.NebulaValueUtils;
import com.antgroup.openspg.core.schema.model.type.BasicTypeEnum;
import org.junit.jupiter.api.Test;

public class NebulaValueUtilsTest {

  @Test
  public void testQuoteName() {
    assertEquals("`Person`", NebulaValueUtils.quoteName("Person"));
  }

  @Test
  public void testVidAndStringEscaping() {
    assertEquals("\"abc\"", NebulaValueUtils.vid("abc"));
    // both backslash and double-quote must be escaped
    assertEquals("\"a\\\"b\\\\c\"", NebulaValueUtils.vid("a\"b\\c"));
  }

  @Test
  public void testLiteralByType() {
    assertEquals("NULL", NebulaValueUtils.literal(null));
    assertEquals("1", NebulaValueUtils.literal(1L));
    assertEquals("1.5", NebulaValueUtils.literal(1.5d));
    assertEquals("true", NebulaValueUtils.literal(true));
    assertEquals("false", NebulaValueUtils.literal(false));
    assertEquals("\"hello\"", NebulaValueUtils.literal("hello"));
  }

  @Test
  public void testNebulaType() {
    assertEquals("string", NebulaValueUtils.nebulaType(BasicTypeEnum.TEXT));
    assertEquals("int64", NebulaValueUtils.nebulaType(BasicTypeEnum.LONG));
    assertEquals("double", NebulaValueUtils.nebulaType(BasicTypeEnum.DOUBLE));
  }
}
