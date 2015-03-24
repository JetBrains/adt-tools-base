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

import com.android.ide.common.blame.SourceFragmentPositionRange;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import junit.framework.TestCase;

public class SourceFragmentPositionRangeJsonTest extends TestCase {

    private Gson gsonSerializer;

    private Gson gsonDeserializer;

    private SourceFragmentPositionRange[] mExamples = new SourceFragmentPositionRange[]{
            new SourceFragmentPositionRange(-1, -1, -1),
            new SourceFragmentPositionRange(11, 22, 34),
            new SourceFragmentPositionRange(11, 22, -1),
            new SourceFragmentPositionRange(11, -1, 34),
            new SourceFragmentPositionRange(-1, 22, 34),
            new SourceFragmentPositionRange(11, 22, 33, 66, 77, 88),
            new SourceFragmentPositionRange(11, 22, -1, 66, 77, -1),
            new SourceFragmentPositionRange(11, -1, -1, 66, -1, -1),
            new SourceFragmentPositionRange(11, -1, -1, 11, -1, -1),
            new SourceFragmentPositionRange(11, 22, 33, 66, 77, 88),
            new SourceFragmentPositionRange(-1, -1, 33, -1, -1, 88),
            new SourceFragmentPositionRange(-1, -1, 33, -1, -1, 33),
            new SourceFragmentPositionRange(11, 22, 33, 11, 22, 33)};

    @Override
    public void setUp() {
        gsonSerializer = new GsonBuilder()
                .registerTypeAdapter(
                        SourceFragmentPositionRange.class,
                        new SourceFragmentPositionRange.Serializer())
                .create();
        gsonDeserializer = new GsonBuilder()
                .registerTypeAdapter(
                        SourceFragmentPositionRange.class,
                        new SourceFragmentPositionRange.Deserializer())
                .create();
    }


    public void testSerializeDeserializeRoundtrip() {
        for (SourceFragmentPositionRange range : mExamples) {
            testRoundTripExample(range);
        }
    }

    private void testRoundTripExample(SourceFragmentPositionRange m1) {
        String json = gsonSerializer.toJson(m1);
        SourceFragmentPositionRange m2 =
                gsonDeserializer.fromJson(json, SourceFragmentPositionRange.class);
        assertEquals(m1, m2);
    }


    public void testSimpleDeserialize() {
        String json2 = "{\"startLine\":245}";
        SourceFragmentPositionRange range2 =
                gsonDeserializer.fromJson(json2, SourceFragmentPositionRange.class);
        assertEquals(new SourceFragmentPositionRange(245, -1, -1), range2);
    }

    public void testDeserialize() {
        String json
                = "{\"startLine\":11,\"startColumn\":22,\"startOffset\":33,\"endLine\":66,\"endColumn\":77,\"endOffset\":88}";
        SourceFragmentPositionRange range =
                gsonDeserializer.fromJson(json, SourceFragmentPositionRange.class);
        assertEquals(range, new SourceFragmentPositionRange(11, 22, 33, 66, 77, 88));
    }
}
