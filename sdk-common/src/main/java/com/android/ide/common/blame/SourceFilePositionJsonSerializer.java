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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public class SourceFilePositionJsonSerializer implements JsonSerializer<SourceFilePosition>,
        JsonDeserializer<SourceFilePosition> {

    private static final String POSITION = "position";

    private static final String FILE = "file";

    @Override
    public SourceFilePosition deserialize(JsonElement json, Type typeOfT,
            JsonDeserializationContext context) {
        JsonObject object = json.getAsJsonObject();
        SourceFile file = object.has(FILE) ?
                context.<SourceFile>deserialize(object.get(FILE), SourceFile.class) :
                SourceFile.UNKNOWN;
        SourcePosition position = object.has(POSITION) ?
                context.<SourcePosition>deserialize(object.get(POSITION), SourcePosition.class) :
                SourcePosition.UNKNOWN;

        return new SourceFilePosition(file, position);
    }

    @Override
    public JsonElement serialize(SourceFilePosition src, Type typeOfSrc,
            JsonSerializationContext context) {
        JsonObject object = new JsonObject();
        SourceFile sourceFile = src.getFile();
        if (!sourceFile.equals(SourceFile.UNKNOWN)) {
            object.add(FILE, context.serialize(sourceFile));
        }
        SourcePosition position = src.getPosition();
        if (!position.equals(SourcePosition.UNKNOWN)) {
            object.add(POSITION, context.serialize(position));
        }
        return object;
    }
}
