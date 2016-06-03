/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.Chunk;
import com.android.tools.chunkio.Chunked;
import com.android.tools.pixelprobe.util.Strings;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A descriptor is a generic way to represent typed and named
 * data. Photoshop seems to abuse descriptor quite a bit in
 * few places, particularly for text layers.
 */
@Chunked
final class Descriptor {
    static final String CLASS_ID_COLOR_RGB  = "RGBC";
    static final String CLASS_ID_COLOR_HSB  = "HSBC";
    static final String CLASS_ID_COLOR_CMYK = "CMYC";
    static final String CLASS_ID_COLOR_LAB  = "LbCl";
    static final String CLASS_ID_COLOR_GRAY = "Grsc";

    // Name from classID, usually not set
    @Chunk
    UnicodeString name;

    // ClassID
    @Chunk
    MinimumString classId;

    // Number of items in the descriptor
    @Chunk
    int count;

    @Chunk(dynamicSize = "descriptor.count", key = "String.valueOf(descriptorItem.key)")
    Map<String, DescriptorItem> items;

    @Override
    public String toString() {
        return "Descriptor{" +
               "name=" + name +
               ", items=" + Strings.join(items.values(), ", ") +
               '}';
    }

    // Used to parse descriptor paths
    private static final Pattern PATH_PATTERN = Pattern.compile("([a-zA-Z0-9]+)\\[([0-9]+)\\]");

    @SuppressWarnings("unchecked")
    <T> T get(String path) {
        Object result = null;
        Descriptor currentDescriptor = this;

        String[] elements = path.split("\\.");
        for (String element : elements) {
            int index = -1;
            Matcher matcher = PATH_PATTERN.matcher(element);
            if (matcher.matches()) {
                element = matcher.group(1);
                index = Integer.parseInt(matcher.group(2));
            }

            DescriptorItem item = currentDescriptor.items.get(element);
            if (item == null) break;

            Object data = item.value.data;
            if (data == null) break;

            if (data instanceof DescriptorItem.ValueList) {
                if (index >= 0) {
                    data = ((DescriptorItem.ValueList) data).items.get(index).data;
                }
            } else if (data instanceof DescriptorItem.Reference) {
                if (index >= 0) {
                    data = ((DescriptorItem.Reference) data).items.get(index).data;
                }
            }

            if (data instanceof Descriptor) {
                result = currentDescriptor = (Descriptor) data;
            } else if (data instanceof FixedString || data instanceof UnicodeString ||
                       data instanceof DescriptorItem.Enumerated) {
                result = data.toString();
            } else if (data instanceof FixedByteArray) {
                result = ((FixedByteArray) data).value;
            } else {
                result = data;
            }
        }

        return (T) result;
    }

    float getFloat(String path) {
        double v = get(path);
        return (float) v;
    }

    float getUnitFloat(String path, float scale) {
        DescriptorItem.UnitDouble value = get(path);
        if (DescriptorItem.UnitDouble.PERCENT.equals(value.unit)) {
            return (float) (value.value / 100.0);
        } else if (DescriptorItem.UnitDouble.POINTS.equals(value.unit)) {
            return (float) (value.value * scale);
        }

        return (float) value.value;
    }
}
