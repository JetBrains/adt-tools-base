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
package com.android.ide.common.blame.parser;

import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.blame.SourcePositionJsonTypeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import junit.framework.TestCase;

public class SourcePositionJsonTypeAdapterTest extends TestCase {

    private Gson gsonSerializer;

    private Gson gsonDeserializer;

    private SourcePosition[] mExamples = new SourcePosition[]{
            new SourcePosition(-1, -1, -1),
            new SourcePosition(11, 22, 34),
            new SourcePosition(11, 22, -1),
            new SourcePosition(11, -1, 34),
            new SourcePosition(-1, 22, 34),
            new SourcePosition(11, 22, 33, 66, 77, 88),
            new SourcePosition(11, 22, -1, 66, 77, -1),
            new SourcePosition(11, -1, -1, 66, -1, -1),
            new SourcePosition(11, -1, -1, 11, -1, -1),
            new SourcePosition(11, 22, 33, 66, 77, 88),
            new SourcePosition(-1, -1, 33, -1, -1, 88),
            new SourcePosition(-1, -1, 33, -1, -1, 33),
            new SourcePosition(11, 22, 33, 11, 22, 33)};

    @Override
    public void setUp() {
        gsonSerializer = new GsonBuilder()
                .registerTypeAdapter(
                        SourcePosition.class,
                        new SourcePositionJsonTypeAdapter())
                .create();
        gsonDeserializer = new GsonBuilder()
                .registerTypeAdapter(
                        SourcePosition.class,
                        new SourcePositionJsonTypeAdapter())
                .create();
    }


    public void testSerializeDeserializeRoundtrip() {
        for (SourcePosition range : mExamples) {
            testRoundTripExample(range);
        }
    }

    private void testRoundTripExample(SourcePosition m1) {
        String json = gsonSerializer.toJson(m1);
        SourcePosition m2 =
                gsonDeserializer.fromJson(json, SourcePosition.class);
        assertEquals(m1, m2);
    }


    public void testSimpleDeserialize() {
        String json2 = "{\"startLine\":245}";
        SourcePosition range2 =
                gsonDeserializer.fromJson(json2, SourcePosition.class);
        assertEquals(new SourcePosition(245, -1, -1), range2);
    }

    public void testDeserialize() {
        String json
                = "{\"startLine\":11,\"startColumn\":22,\"startOffset\":33,\"endLine\":66,\"endColumn\":77,\"endOffset\":88}";
        SourcePosition range =
                gsonDeserializer.fromJson(json, SourcePosition.class);
        assertEquals(range, new SourcePosition(11, 22, 33, 66, 77, 88));
    }
}
