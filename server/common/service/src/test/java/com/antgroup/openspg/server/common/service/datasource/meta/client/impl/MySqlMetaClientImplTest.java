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

import org.junit.Assert;
import org.junit.Test;

public class MySqlMetaClientImplTest {

  @Test
  public void escapeIdentifier_doublesBackticks() {
    Assert.assertEquals("db``name", MySqlMetaClientImpl.escapeIdentifier("db`name"));
  }

  @Test
  public void isPartitionTable_returnsFalse() {
    MySqlMetaClientImpl client = new MySqlMetaClientImpl();
    Assert.assertFalse(client.isPartitionTable(null, "db", "table"));
  }

  @Test
  public void getAllPartitions_returnsEmpty() {
    MySqlMetaClientImpl client = new MySqlMetaClientImpl();
    Assert.assertTrue(client.getAllPartitions(null, "db.table").isEmpty());
  }
}
