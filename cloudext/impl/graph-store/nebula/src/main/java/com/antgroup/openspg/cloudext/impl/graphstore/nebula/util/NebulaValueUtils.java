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

package com.antgroup.openspg.cloudext.impl.graphstore.nebula.util;

import com.antgroup.openspg.core.schema.model.type.BasicTypeEnum;
import com.vesoft.nebula.client.graph.data.ValueWrapper;

/** Helpers for building nGQL literals / identifiers and unwrapping {@link ValueWrapper}. */
public class NebulaValueUtils {

  private NebulaValueUtils() {}

  /** Wrap an identifier (tag / edge / property name) with backticks so reserved words are safe. */
  public static String quoteName(String name) {
    return "`" + name + "`";
  }

  /** Format a vertex id (VID) as an nGQL string literal. */
  public static String vid(String id) {
    return stringLiteral(id);
  }

  /** Format a java value into an nGQL literal according to its runtime type. */
  public static String literal(Object value) {
    if (value == null) {
      return "NULL";
    }
    if (value instanceof Number) {
      return value.toString();
    }
    if (value instanceof Boolean) {
      return ((Boolean) value) ? "true" : "false";
    }
    return stringLiteral(value.toString());
  }

  private static String stringLiteral(String raw) {
    String escaped =
        raw.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    return "\"" + escaped + "\"";
  }

  /** Map an SPG basic type to the corresponding NebulaGraph property type keyword. */
  public static String nebulaType(BasicTypeEnum basicType) {
    switch (basicType) {
      case TEXT:
        return "string";
      case LONG:
        return "int64";
      case DOUBLE:
        return "double";
      default:
        throw new IllegalArgumentException("unsupported basic type for nebula: " + basicType);
    }
  }

  /** Unwrap a {@link ValueWrapper} into a plain java object. */
  public static Object unwrap(ValueWrapper wrapper) {
    if (wrapper == null || wrapper.isNull() || wrapper.isEmpty()) {
      return null;
    }
    try {
      if (wrapper.isString()) {
        return wrapper.asString();
      }
      if (wrapper.isLong()) {
        return wrapper.asLong();
      }
      if (wrapper.isDouble()) {
        return wrapper.asDouble();
      }
      if (wrapper.isBoolean()) {
        return wrapper.asBoolean();
      }
      return wrapper.toString();
    } catch (Exception e) {
      throw new RuntimeException("failed to unwrap nebula value", e);
    }
  }

  /** Unwrap a {@link ValueWrapper} that is expected to be a string (e.g. a VID). */
  public static String asString(ValueWrapper wrapper) {
    Object value = unwrap(wrapper);
    return value == null ? null : String.valueOf(value);
  }
}
