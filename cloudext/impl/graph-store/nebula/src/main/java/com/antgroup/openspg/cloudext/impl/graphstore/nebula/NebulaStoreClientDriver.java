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

import com.antgroup.openspg.cloudext.interfaces.graphstore.GraphStoreClient;
import com.antgroup.openspg.cloudext.interfaces.graphstore.GraphStoreClientDriver;
import com.antgroup.openspg.cloudext.interfaces.graphstore.GraphStoreClientDriverManager;
import com.antgroup.openspg.cloudext.interfaces.graphstore.impl.DefaultLPGTypeNameConvertor;
import com.antgroup.openspg.common.util.cloudext.CachedCloudExtClientDriver;

/**
 * Driver for the {@code nebula://} scheme, targeting NebulaGraph 3.x kernels (including trsgraph).
 *
 * <p>Registered both via SPI (see {@code META-INF/services} in the graph-store interface module)
 * and via the {@code cloudext.graphstore.drivers} system property.
 */
public class NebulaStoreClientDriver extends CachedCloudExtClientDriver<GraphStoreClient>
    implements GraphStoreClientDriver {

  static {
    GraphStoreClientDriverManager.registerDriver(new NebulaStoreClientDriver());
  }

  @Override
  public String driverScheme() {
    return NebulaConstants.SCHEME;
  }

  @Override
  protected GraphStoreClient innerConnect(String connInfo) {
    return new NebulaStoreClient(connInfo, new DefaultLPGTypeNameConvertor());
  }
}
