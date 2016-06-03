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

import java.util.List;

/**
 * A descriptor item has a name and a value.
 * The value itself is typed.
 */
@Chunked
final class DescriptorItem {
    /**
     * Enum reference (see Reference descriptor item)
     */
    @Chunked
    static final class Enumerated {
        @Chunk
        MinimumString type;
        @Chunk
        MinimumString value;

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    /**
     * A double value with a unit.
     */
    @Chunked
    static final class UnitDouble {
        static final String POINTS = "#Pnt";
        static final String MILLIMETERS = "#Mlm";
        static final String ANGLE_DEGREES = "#Ang";
        static final String DENSITY = "#Rsl";
        static final String RELATIVE = "#Rlt";
        static final String COERCED = "#Nne";
        static final String PERCENT = "#Prc";
        static final String PIXELS = "#Pxl";

        @Chunk(byteCount = 4)
        String unit;

        @Chunk
        double value;

        @Override
        public String toString() {
            return Double.toString(value);
        }

        double toPixel( float resolutionScale) {
            if (PIXELS.equals(unit)) {
                return value;
            } else if (POINTS.equals(unit)) {
                return value * resolutionScale;
            }
            throw new RuntimeException("Cannot convert from unit: " + unit, null);
        }
    }

    /**
     * A float value with a unit. Possible units:
     * #Pnt, points
     * #Mlm, millimeters
     * #Ang, angle in degrees
     * #Rsl, density in inch
     * #Rlt, distance base 72ppi
     * #Nne, coerced
     * #Prc, percent
     * #Pxl, pixels
     */
    @Chunked
    static final class UnitFloat {
        @Chunk(byteCount = 4)
        String unit;

        @Chunk
        float value;

        @Override
        public String toString() {
            return Float.toString(value);
        }
    }

    /**
     * A class name + class ID. Used in references.
     */
    @Chunked
    static final class ClassType {
        @Chunk
        UnicodeString name;
        @Chunk
        MinimumString classId;
    }

    /**
     * A property reference.
     */
    @Chunked
    static final class Property {
        @Chunk
        ClassType classType;
        @Chunk
        MinimumString keyId;
    }

    /**
     * A reference is actually a list of items.
     * Hard to say why.
     */
    @Chunked
    static final class Reference {
        @Chunked
        static final class Item {
            @Chunk(byteCount = 4)
            String type;

            @Chunk(switchType = {
                @Chunk.Case(test = "item.type.equals(\"Enmr\")", type = Enumerated.class),
                @Chunk.Case(test = "item.type.equals(\"Clss\")", type = ClassType.class),
                @Chunk.Case(test = "item.type.equals(\"Idnt\")", type = int.class),
                @Chunk.Case(test = "item.type.equals(\"indx\")", type = int.class),
                @Chunk.Case(test = "item.type.equals(\"name\")", type = UnicodeString.class),
                @Chunk.Case(test = "item.type.equals(\"prop\")", type = Property.class),
                @Chunk.Case(test = "item.type.equals(\"rele\")", type = int.class)
            })
            Object data;
        }

        @Chunk
        int count;

        @Chunk(dynamicSize = "reference.count")
        List<Reference.Item> items;
    }

    /**
     * Holds a list of descriptor values.
     */
    @Chunked
    static final class ValueList {
        @Chunk
        int count;

        @Chunk(dynamicSize = "valueList.count")
        List<Value> items;

        @Override
        public String toString() {
            return "ValueList{" +
                   "items=" + Strings.join(items, ", ") +
                   '}';
        }
    }

    /**
     * The value of a descriptor. A value can be one of many types,
     * see the annotation below to find out the exact list.
     */
    @Chunked
    static final class Value {
        @Chunk(byteCount = 4)
        String type;

        @Chunk(switchType = {
            @Chunk.Case(test = "value.type.equals(\"alis\")", type = FixedString.class),
            @Chunk.Case(test = "value.type.equals(\"bool\")", type = boolean.class),
            @Chunk.Case(test = "value.type.equals(\"comp\")", type = long.class),
            @Chunk.Case(test = "value.type.equals(\"doub\")", type = double.class),
            @Chunk.Case(test = "value.type.equals(\"enum\")", type = Enumerated.class),
            @Chunk.Case(test = "value.type.equals(\"GlbC\")", type = ClassType.class),
            @Chunk.Case(test = "value.type.equals(\"GlbO\")", type = Descriptor.class),
            @Chunk.Case(test = "value.type.equals(\"long\")", type = int.class),
            @Chunk.Case(test = "value.type.equals(\"obj\" )", type = Reference.class),
            @Chunk.Case(test = "value.type.equals(\"Objc\")", type = Descriptor.class),
            @Chunk.Case(test = "value.type.equals(\"TEXT\")", type = UnicodeString.class),
            @Chunk.Case(test = "value.type.equals(\"tdta\")", type = FixedByteArray.class),
            @Chunk.Case(test = "value.type.equals(\"type\")", type = ClassType.class),
            @Chunk.Case(test = "value.type.equals(\"UnFl\")", type = UnitFloat.class),
            @Chunk.Case(test = "value.type.equals(\"UntF\")", type = UnitDouble.class),
            @Chunk.Case(test = "value.type.equals(\"VlLs\")", type = ValueList.class)
        })
        Object data;

        @Override
        public String toString() {
            if (data == null) return null;
            return data.toString();
        }
    }

    // The name of the descriptor
    @Chunk
    MinimumString key;

    @Chunk
    Value value;

    Object getData() {
        return value.data;
    }

    @Override
    public String toString() {
        return "DescriptorItem{" +
                "key=" + key +
                ", value=" + value +
                '}';
    }
}
