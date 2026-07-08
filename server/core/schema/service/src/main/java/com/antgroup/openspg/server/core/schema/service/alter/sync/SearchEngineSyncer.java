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

package com.antgroup.openspg.server.core.schema.service.alter.sync;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.antgroup.openspg.cloudext.impl.searchengine.nebula.NebulaSearchClient;
import com.antgroup.openspg.cloudext.impl.graphstore.nebula.NebulaConstants;
import com.antgroup.openspg.cloudext.interfaces.searchengine.SearchEngineClient;
import com.antgroup.openspg.cloudext.interfaces.searchengine.SearchEngineClientDriverManager;
import com.antgroup.openspg.common.util.StringUtils;
import com.antgroup.openspg.common.util.neo4j.Neo4jCommonUtils;
import com.antgroup.openspg.core.schema.model.SPGSchemaAlterCmd;
import com.antgroup.openspg.server.common.model.CommonConstants;
import com.antgroup.openspg.server.common.model.project.Project;
import com.antgroup.openspg.server.common.service.config.AppEnvConfig;
import com.antgroup.openspg.server.common.service.project.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class SearchEngineSyncer extends BaseSchemaSyncer {

  @Autowired private AppEnvConfig appEnvConfig;
  @Autowired private ProjectService projectService;

  @Override
  public void syncSchema(Long projectId, SPGSchemaAlterCmd schemaEditCmd) {
    String searchEngineUrl = resolveSearchEngineUrl(projectId);
    SearchEngineClient searchEngineClient =
        SearchEngineClientDriverManager.getClient(searchEngineUrl);
    if (searchEngineClient instanceof NebulaSearchClient) {
      ((NebulaSearchClient) searchEngineClient)
          .alterSchemaWithVectorDimensions(schemaEditCmd, getVectorDimensions(projectId));
      return;
    }
    searchEngineClient.alterSchema(schemaEditCmd);
  }

  private String resolveSearchEngineUrl(Long projectId) {
    String configuredUrl = appEnvConfig.getSearchEngineUrl();
    UriComponents configured = UriComponentsBuilder.fromUriString(configuredUrl).build();
    if (NebulaConstants.SCHEME.equals(configured.getScheme())) {
      return projectService.getGraphStoreUrl(projectId);
    }
    return configuredUrl;
  }

  private int getVectorDimensions(Long projectId) {
    Project project = projectService.queryById(projectId);
    if (project == null || StringUtils.isBlank(project.getConfig())) {
      return Neo4jCommonUtils.DEFAULT_VECTOR_DIMENSIONS;
    }
    JSONObject vectorizerConfig =
        JSON.parseObject(project.getConfig()).getJSONObject(CommonConstants.VECTORIZER);
    if (vectorizerConfig == null) {
      return Neo4jCommonUtils.DEFAULT_VECTOR_DIMENSIONS;
    }
    Integer vectorDimensions = vectorizerConfig.getInteger(CommonConstants.VECTOR_DIMENSIONS);
    return vectorDimensions != null ? vectorDimensions : Neo4jCommonUtils.DEFAULT_VECTOR_DIMENSIONS;
  }
}
