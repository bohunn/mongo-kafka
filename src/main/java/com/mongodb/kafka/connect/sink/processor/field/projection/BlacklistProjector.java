/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Original Work: Apache License, Version 2.0, Copyright 2017 Hans-Peter Grahsl.
 */

package com.mongodb.kafka.connect.sink.processor.field.projection;

import static com.mongodb.kafka.connect.sink.MongoSinkTopicConfig.ID_FIELD;
import static com.mongodb.kafka.connect.sink.MongoSinkTopicConfig.KEY_PROJECTION_TYPE_CONFIG;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import com.mongodb.kafka.connect.sink.MongoSinkTopicConfig;

public abstract class BlacklistProjector extends FieldProjector {

  public BlacklistProjector(final MongoSinkTopicConfig config, final Set<String> fields) {
    super(config, fields);
  }

  protected boolean isUsingBlacklistKeyProjection() {
    return getConfig()
        .getString(KEY_PROJECTION_TYPE_CONFIG)
        .equalsIgnoreCase(MongoSinkTopicConfig.FieldProjectionType.BLACKLIST.name());
  }

  protected boolean isUsingBlacklistValueProjection() {
    return getConfig()
        .getString(MongoSinkTopicConfig.VALUE_PROJECTION_TYPE_CONFIG)
        .equalsIgnoreCase(MongoSinkTopicConfig.FieldProjectionType.BLACKLIST.name());
  }

  @Override
  protected void doProjection(final String field, final BsonDocument doc) {
    if (!field.contains(FieldProjector.SUB_FIELD_DOT_SEPARATOR)) {
      if (field.equals(FieldProjector.SINGLE_WILDCARD)
          || field.equals(FieldProjector.DOUBLE_WILDCARD)) {
        handleWildcard(field, "", doc);
        return;
      }

      // NOTE: never try to remove the _id field
      if (!field.equals(ID_FIELD)) {
        doc.remove(field);
      }
      return;
    }

    int dotIdx = field.indexOf(FieldProjector.SUB_FIELD_DOT_SEPARATOR);
    String firstPart = field.substring(0, dotIdx);
    String otherParts = field.length() >= dotIdx ? field.substring(dotIdx + 1) : "";

    if (firstPart.equals(FieldProjector.SINGLE_WILDCARD)
        || firstPart.equals(FieldProjector.DOUBLE_WILDCARD)) {
      handleWildcard(firstPart, otherParts, doc);
      return;
    }

    BsonValue value = doc.get(firstPart);
    if (value != null) {
      if (value.isDocument()) {
        doProjection(otherParts, value.asDocument());
      }
      if (value.isArray()) {
        BsonArray values = value.asArray();
        for (BsonValue v : values.getValues()) {
          if (v != null && v.isDocument()) {
            doProjection(otherParts, v.asDocument());
          }
        }
      }
    }
  }

  private void handleWildcard(
      final String firstPart, final String otherParts, final BsonDocument doc) {
    Iterator<Map.Entry<String, BsonValue>> iter = doc.entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry<String, BsonValue> entry = iter.next();
      BsonValue value = entry.getValue();

      // NOTE: never try to remove the _id field
      if (entry.getKey().equals(ID_FIELD)) {
        continue;
      }

      if (firstPart.equals(FieldProjector.DOUBLE_WILDCARD)) {
        iter.remove();
      } else if (firstPart.equals(FieldProjector.SINGLE_WILDCARD)) {
        if (!value.isDocument()) {
          iter.remove();
        } else if (!otherParts.isEmpty()) {
          doProjection(otherParts, (BsonDocument) value);
        }
      }
    }
  }
}
