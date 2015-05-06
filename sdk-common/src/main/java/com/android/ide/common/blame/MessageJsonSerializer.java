/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.common.blame;

import com.android.annotations.concurrency.Immutable;
import com.google.common.collect.BiMap;
import com.google.common.collect.EnumHashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.List;

public class MessageJsonSerializer
        implements JsonSerializer<Message>,
        JsonDeserializer<Message> {

    private static final String KIND = "kind";

    private static final String TEXT = "text";

    private static final String SOURCE_FILE_POSITIONS = "sources";

    private static final BiMap<Message.Kind, String> KIND_STRING_ENUM_MAP;

    static {
        EnumHashBiMap<Message.Kind, String> map = EnumHashBiMap.create(Message.Kind.class);
        map.put(Message.Kind.ERROR, "error");
        map.put(Message.Kind.WARNING, "warning");
        map.put(Message.Kind.INFO, "info");
        map.put(Message.Kind.STATISTICS, "statistics");
        map.put(Message.Kind.UNKNOWN, "unknown");
        KIND_STRING_ENUM_MAP = Maps.unmodifiableBiMap(map);
    }

    @Override
    public JsonElement serialize(Message position, Type type,
            JsonSerializationContext jsonSerializationContext) {
        JsonObject result = new JsonObject();
        result.addProperty(KIND, KIND_STRING_ENUM_MAP.get(position.getKind()));
        result.addProperty(TEXT, position.getText());
        result.add(SOURCE_FILE_POSITIONS,
                jsonSerializationContext.serialize(position.getSourceFilePositions()));
        return result;
    }

    @Override
    public Message deserialize(JsonElement jsonElement, Type type,
            JsonDeserializationContext context) {
        final JsonObject object = jsonElement.getAsJsonObject();
        final Message.Kind kind;
        if (object.has(KIND)) {
            //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
            Message.Kind theKind = KIND_STRING_ENUM_MAP.inverse()
                    .get(object.get(KIND).getAsString().toLowerCase());
            kind = (theKind != null) ? theKind : Message.Kind.UNKNOWN;
        } else {
            kind = Message.Kind.UNKNOWN;
        }

        final String text = object.has(TEXT) ? object.get(TEXT).getAsString() : "";
        final ImmutableList<SourceFilePosition> sourceFilePositions;
        if (object.has(SOURCE_FILE_POSITIONS)) {
            JsonElement e = object.get(SOURCE_FILE_POSITIONS);
            if (e.isJsonArray()) {
                sourceFilePositions = ImmutableList.copyOf(
                        context.<Iterable<? extends SourceFilePosition>>deserialize(
                                e, SourceFilePosition[].class));
            } else if (e.isJsonObject()) {
                sourceFilePositions = ImmutableList.of(
                        context.<SourceFilePosition>deserialize(e, SourceFilePosition.class));
            } else {
                sourceFilePositions = ImmutableList.of(SourceFilePosition.UNKNOWN);
            }
        } else {
            sourceFilePositions = ImmutableList.of(SourceFilePosition.UNKNOWN);
        }

        return new Message(kind, text, sourceFilePositions);
    }

    public static void registerTypeAdapters(GsonBuilder builder) {
        builder.registerTypeAdapter(SourceFile.class, new SourceFileJsonTypeAdapter());
        builder.registerTypeAdapter(SourcePosition.class, new SourcePositionJsonTypeAdapter());
        builder.registerTypeAdapter(SourceFilePosition.class,
                new SourceFilePositionJsonSerializer());
        builder.registerTypeAdapter(Message.class, new MessageJsonSerializer());
    }
}
