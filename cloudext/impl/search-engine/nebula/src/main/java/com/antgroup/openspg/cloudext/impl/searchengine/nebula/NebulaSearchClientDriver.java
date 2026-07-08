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

package com.antgroup.openspg.cloudext.impl.searchengine.nebula;

import com.antgroup.openspg.cloudext.interfaces.searchengine.SearchEngineClient;
import com.antgroup.openspg.cloudext.interfaces.searchengine.SearchEngineClientDriver;
import com.antgroup.openspg.cloudext.interfaces.searchengine.SearchEngineClientDriverManager;
import com.antgroup.openspg.common.util.cloudext.CachedCloudExtClientDriver;

public class NebulaSearchClientDriver extends CachedCloudExtClientDriver<SearchEngineClient>
    implements SearchEngineClientDriver {

  static {
    SearchEngineClientDriverManager.registerDriver(new NebulaSearchClientDriver());
  }

  @Override
  public String driverScheme() {
    return "nebula";
  }

  @Override
  protected SearchEngineClient innerConnect(String connInfo) {
    return new NebulaSearchClient(connInfo);
  }
}
