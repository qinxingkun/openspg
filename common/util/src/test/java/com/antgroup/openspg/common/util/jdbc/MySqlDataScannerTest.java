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

import org.junit.Assert;
import org.junit.Test;

public class MySqlDataScannerTest {

  @Test
  public void buildSelectSql_withoutWhere() {
    String sql = MySqlDataScanner.buildSelectSql("gkx_local", "company", null, 0, 5000);
    Assert.assertEquals("SELECT * FROM `gkx_local`.`company` LIMIT 0, 5000", sql);
  }

  @Test
  public void buildSelectSql_withWhereAndOffset() {
    String sql = MySqlDataScanner.buildSelectSql("gkx_local", "company", "status = 1", 5000, 1000);
    Assert.assertEquals(
        "SELECT * FROM `gkx_local`.`company` WHERE status = 1 LIMIT 5000, 1000", sql);
  }

  @Test
  public void escapeIdentifier_doublesBackticks() {
    Assert.assertEquals("a``b", MySqlDataScanner.escapeIdentifier("a`b"));
  }
}
