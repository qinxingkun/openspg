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

import com.antgroup.openspg.cloudext.impl.graphstore.nebula.util.NebulaSchemaUtils;
import com.antgroup.openspg.cloudext.impl.graphstore.nebula.util.NebulaValueUtils;
import com.antgroup.openspg.cloudext.interfaces.graphstore.LPGTypeNameConvertor;
import com.antgroup.openspg.core.schema.model.predicate.IndexTypeEnum;
import com.antgroup.openspg.core.schema.model.predicate.Property;
import com.antgroup.openspg.core.schema.model.type.BaseSPGType;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

/** Creates Nebula search indexes aligned with {@code Neo4jGraphUtils.initializeSchema}. */
@Slf4j
public class NebulaSearchIndexUtils {

  private static final int DEFAULT_STRING_INDEX_LENGTH = 256;
  private static final String ID_PROPERTY = "id";
  private static final String NAME_PROPERTY = "name";
  private static final String ENTITY_TYPE = "Entity";

  private NebulaSearchIndexUtils() {}

  public static void initializeSchema(
      List<BaseSPGType> schemaTypes,
      int vectorDimensions,
      LPGTypeNameConvertor typeNameConvertor,
      Function<String, Boolean> executeQuietly) {
    if (CollectionUtils.isEmpty(schemaTypes)) {
      return;
    }
    for (BaseSPGType schemaType : schemaTypes) {
      if (schemaType.isBasicType()) {
        continue;
      }
      initializeTypeIndexes(schemaType, vectorDimensions, typeNameConvertor, executeQuietly);
    }
    initializeEntityDefaults(vectorDimensions, typeNameConvertor, executeQuietly);
  }

  private static void initializeTypeIndexes(
      BaseSPGType schemaType,
      int vectorDimensions,
      LPGTypeNameConvertor typeNameConvertor,
      Function<String, Boolean> executeQuietly) {
    String tagName = typeNameConvertor.convertVertexTypeName(schemaType.getName());
    createPropertyIndexQuietly(tagName, ID_PROPERTY, executeQuietly);
    createPropertyIndexQuietly(tagName, NAME_PROPERTY, executeQuietly);

    List<String> fullTextProperties = collectFullTextProperties(schemaType.getProperties());
    if (!fullTextProperties.isEmpty()) {
      String statement = NebulaSchemaUtils.createFullTextTagIndex(tagName, fullTextProperties);
      if (executeQuietly.apply(statement)) {
        executeQuietly.apply(
            NebulaSchemaUtils.rebuildNamedTagIndex(NebulaSchemaUtils.fullTextIndexName(tagName)));
      }
    }

    List<Property> properties = schemaType.getProperties();
    if (properties == null) {
      return;
    }
    for (Property property : properties) {
      if (NAME_PROPERTY.equals(property.getName())) {
        createVectorIndexQuietly(tagName, property.getName(), vectorDimensions, executeQuietly);
      }
      IndexTypeEnum indexType = property.getIndexType();
      if (indexType == null) {
        continue;
      }
      switch (indexType) {
        case VECTOR:
        case TEXT_AND_VECTOR:
          createVectorIndexQuietly(tagName, property.getName(), vectorDimensions, executeQuietly);
          break;
        case SPARSE_VECTOR:
        case TEXT_AND_SPARSE_VECTOR:
          log.info("Nebula search does not support sparse vector index: {}", indexType);
          break;
        default:
          break;
      }
    }
  }

  private static void initializeEntityDefaults(
      int vectorDimensions,
      LPGTypeNameConvertor typeNameConvertor,
      Function<String, Boolean> executeQuietly) {
    String entityTag = typeNameConvertor.convertVertexTypeName(ENTITY_TYPE);
    createVectorIndexQuietly(entityTag, NAME_PROPERTY, vectorDimensions, executeQuietly);
    createVectorIndexQuietly(entityTag, "desc", vectorDimensions, executeQuietly);
  }

  private static List<String> collectFullTextProperties(List<Property> properties) {
    Set<String> propertyNames = new HashSet<>();
    if (properties == null) {
      return new ArrayList<>();
    }
    for (Property property : properties) {
      if (shouldCreateTextIndex(property)) {
        propertyNames.add(property.getName());
      }
    }
    return new ArrayList<>(propertyNames);
  }

  private static boolean shouldCreateTextIndex(Property property) {
    if (NAME_PROPERTY.equals(property.getName())) {
      return true;
    }
    IndexTypeEnum indexType = property.getIndexType();
    if (indexType == null) {
      return false;
    }
    switch (indexType) {
      case TEXT:
      case TEXT_AND_VECTOR:
      case TEXT_AND_SPARSE_VECTOR:
        return true;
      default:
        return false;
    }
  }

  private static void createPropertyIndexQuietly(
      String tagName, String propertyName, Function<String, Boolean> executeQuietly) {
    String statement =
        NebulaSchemaUtils.createTagPropertyIndex(
            tagName, propertyName, DEFAULT_STRING_INDEX_LENGTH);
    if (executeQuietly.apply(statement)) {
      executeQuietly.apply(
          NebulaSchemaUtils.rebuildNamedTagIndex(
              NebulaSchemaUtils.propertyIndexName(tagName, propertyName)));
    }
  }

  private static void createVectorIndexQuietly(
      String tagName,
      String propertyKey,
      int vectorDimensions,
      Function<String, Boolean> executeQuietly) {
    String vectorField = vectorFieldName(propertyKey);
    String indexName = NebulaSchemaUtils.annIndexName(tagName, vectorField);
    String statement =
        NebulaSchemaUtils.createTagAnnIndex(indexName, tagName, vectorField, vectorDimensions);
    if (!executeQuietly.apply(statement)) {
      log.warn("failed to create nebula ANN index for tag={}, field={}", tagName, vectorField);
    }
  }

  public static String vectorFieldName(String propertyKey) {
    return "_" + propertyKey + "_vector";
  }

  public static Set<String> listEntityTags(
      Function<String, com.vesoft.nebula.client.graph.data.ResultSet> execute) {
    Set<String> tags = Sets.newLinkedHashSet();
    com.vesoft.nebula.client.graph.data.ResultSet resultSet = execute.apply("SHOW TAGS");
    if (resultSet == null || !resultSet.isSucceeded()) {
      return tags;
    }
    for (int i = 0; i < resultSet.rowsSize(); i++) {
      String tagName = NebulaValueUtils.asString(resultSet.rowValues(i).get(0));
      if (tagName != null && !tagName.isEmpty()) {
        tags.add(tagName);
      }
    }
    return tags;
  }
}
