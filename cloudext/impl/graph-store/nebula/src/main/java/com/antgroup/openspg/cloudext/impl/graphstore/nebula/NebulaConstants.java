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

public class NebulaConstants {

  /** Connection scheme handled by {@code NebulaStoreClientDriver}. */
  public static final String SCHEME = "nebula";

  /** Query params in the connection url. */
  public static final String USER = "user";

  public static final String PASSWORD = "password";
  public static final String SPACE = "space";
  public static final String TIMEOUT = "timeout";

  /**
   * Fallback space aliases. The OpenSPG server composes graph-store urls in a Neo4j style and drops
   * unknown query params, only emitting {@code database} and {@code namespace}. When {@code space}
   * is absent we resolve the Nebula space from {@code namespace} (preferred) then {@code database},
   * so that one OpenSPG project (namespace) maps to one Nebula space.
   */
  public static final String NAMESPACE = "namespace";

  public static final String DATABASE = "database";

  /** Optional comma-separated list of {@code host:port} graphd addresses. */
  public static final String HOSTS = "hosts";

  /** Default values. */
  public static final String DEFAULT_USER = "root";

  public static final String DEFAULT_PASSWORD = "nebula";
  public static final int DEFAULT_PORT = 9669;

  /**
   * When the target space does not exist it is auto-created with this vid length / partition /
   * replica config. Business ids are used as {@code FIXED_STRING} VIDs.
   */
  public static final int DEFAULT_VID_LENGTH = 256;

  public static final int DEFAULT_PARTITION_NUM = 10;
  public static final int DEFAULT_REPLICA_FACTOR = 1;

  /** Timeout (in ms) used when waiting for asynchronous schema DDL to take effect. */
  public static final long SCHEMA_EFFECTIVE_TIMEOUT_MS = 30_000L;

  public static final long SCHEMA_EFFECTIVE_INTERVAL_MS = 1_000L;

  /** Timeout (in ms) used when waiting for an auto-created space to become ready. */
  public static final long SPACE_EFFECTIVE_TIMEOUT_MS = 60_000L;

  /** Batch size for INSERT / DELETE statements. */
  public static final int DML_BATCH_SIZE = 200;

  private NebulaConstants() {}
}
