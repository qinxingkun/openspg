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

package com.antgroup.openspg.common.util.builder;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.antgroup.openspg.common.constants.BuilderConstant;
import com.antgroup.openspg.server.common.model.CommonEnum.DataSourceType;
import com.antgroup.openspg.server.common.model.datasource.DataSource;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class StructureBuilderExtensionHelperTest {

  @Test
  public void buildExtension_shouldBuildEntityMappingConfig() {
    DataSource dataSource = new DataSource();
    dataSource.setId(1L);
    dataSource.setType(DataSourceType.MySQL);
    dataSource.setDbUrl("jdbc:mysql://127.0.0.1:3306/gkx_local");
    dataSource.setDbUser("root");
    dataSource.setEncrypt("encrypted");

    Map<String, List<String>> propertyMapping =
        ImmutableMap.of(
            "id", Lists.newArrayList("company_id"),
            "name", Lists.newArrayList("company_name"));

    JSONObject extension =
        StructureBuilderExtensionHelper.buildExtension(
            dataSource,
            "gkx_local",
            "company",
            "EduKG.Company",
            propertyMapping,
            "status = 1",
            1000);

    JSONObject dataSourceConfig = extension.getJSONObject(BuilderConstant.DATASOURCE_CONFIG);
    Assert.assertTrue(dataSourceConfig.getBoolean(BuilderConstant.STRUCTURE));
    Assert.assertEquals("gkx_local", dataSourceConfig.getString(BuilderConstant.DATABASE));
    Assert.assertEquals("company", dataSourceConfig.getString(BuilderConstant.TABLE));
    Assert.assertEquals("status = 1", dataSourceConfig.getString("where"));
    Assert.assertEquals(Integer.valueOf(1000), dataSourceConfig.getInteger("batchSize"));

    JSONObject mappingConfig = extension.getJSONObject(BuilderConstant.MAPPING_CONFIG);
    Assert.assertEquals(
        BuilderConstant.ENTITY_MAPPING, mappingConfig.getString(BuilderConstant.MAPPING_TYPE));
    Assert.assertEquals(
        "EduKG.Company",
        mappingConfig
            .getJSONArray(BuilderConstant.FILTER)
            .getJSONObject(0)
            .getString(BuilderConstant.S));

    JSONObject inverted =
        JSON.parseArray(mappingConfig.getString(BuilderConstant.CONFIG)).getJSONObject(0);
    JSONObject mapping = inverted.getJSONObject(BuilderConstant.MAPPING);
    Assert.assertEquals(Lists.newArrayList("id"), mapping.getJSONArray("company_id"));
    Assert.assertEquals(Lists.newArrayList("name"), mapping.getJSONArray("company_name"));
  }

  @Test
  public void resolveEntityTypeFilter_shouldPrefixNamespace() {
    Assert.assertEquals(
        "EduKG.Company",
        StructureBuilderExtensionHelper.resolveEntityTypeFilter("EduKG", "Company"));
    Assert.assertEquals(
        "EduKG.Company",
        StructureBuilderExtensionHelper.resolveEntityTypeFilter("EduKG", "EduKG.Company"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void validatePropertyMapping_shouldRequireId() {
    StructureBuilderExtensionHelper.validatePropertyMapping(
        ImmutableMap.of("name", Lists.newArrayList("company_name")));
  }
}
