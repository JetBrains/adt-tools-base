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

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The PSD format encodes all the text styling information in a structured
 * text format. This format can describe:
 * <ul>
 * <li>Arrays</li>
 * <li>Maps</li>
 * <li>Named properties</li>
 * <li>Integers</li>
 * <li>Floats</li>
 * <li>Booleans</li>
 * <li>Strings</li>
 * </ul>
 *
 * Here is an example:
 * <pre>
 *     <<
 *       /ParagraphSheet
 *       <<
 *         /DefaultStyleSheet 0
 *         /Properties
 *         <<
 *           /WordSpacing [ .8 1.0 1.33 ]
 *           /EveryLineComposer false
 *           /PostHyphen 2
 *         >>
 *     >>
 * </pre>
 *
 * The << and >> markers define the beginning and the end of a map.
 * A line starting with a / describes a named property (which can be of
 * any type). Arrays are marked with [ and ] and can be multi-lines.
 *
 * To make things a bit tricky, strings are stored in UTF-16BE (big endian)
 * while the rest of the structured document is in simple ASCII. To avoid
 * encoding issues with the standard APIs, the structured text is parsed
 * by hand.
 *
 * This format is both fairly easy to read and quirky. Please refer to the
 * comments in the implementation below for more information.
 */
final class TextEngine {
    /**
     * A property has a type and a value.
     */
    public interface Property<T> {
        enum Type {
            LIST,
            MAP,
            STRING,
            INTEGER,
            FLOAT,
            BOOLEAN
        }

        Property.Type getType();

        T getValue();
    }

    /**
     * A list of properties.
     */
    public static final class ListProperty implements Property<List<Property<?>>> {
        final List<Property<?>> list = new ArrayList<>();

        @Override
        public Type getType() {
            return Type.LIST;
        }

        @Override
        public List<Property<?>> getValue() {
            return list;
        }

        public float[] toFloatArray() {
            float[] a = new float[list.size()];
            for (int i = 0; i < a.length; i++) {
                a[i] = ((FloatProperty) list.get(i)).getValue();
            }
            return a;
        }

        public int[] toIntArray() {
            int[] a = new int[list.size()];
            for (int i = 0; i < a.length; i++) {
                a[i] = ((IntProperty) list.get(i)).getValue();
            }
            return a;
        }
    }

    /**
     * A properties map. Each property stored in the map is named by the
     * associated key.
     */
    public static final class MapProperty implements Property<Map<String, Property<?>>> {
        private static final Pattern PATH_PATTERN = Pattern.compile("([a-zA-Z0-9]+)\\[([0-9]+)\\]");

        final Map<String, Property<?>> map = new HashMap<>();

        @Override
        public Type getType() {
            return Type.MAP;
        }

        @Override
        public Map<String, Property<?>> getValue() {
            return map;
        }

        /**
         * Finds a property in this map using a simple path expression language.
         * Paths have the following form:
         *
         * PropertyName1.ArrayName[2].PropertyName2
         *
         * This path will find the value of the property called PropertyName2 stored
         * in the map as the third element of the array named ArrayName in the map
         * called PropertyName1.
         *
         * @param path A path expression
         *
         * @return A property or null if no match is found
         */
        public Property<?> get(String path) {
            Property<?> property = null;
            Map<String, Property<?>> currentMap = map;
            // Simple regex to match indexing within arrays: name[INDEX]
            Pattern pattern = PATH_PATTERN;

            String[] elements = path.split("\\.");
            out:
            for (String element : elements) {
                int index = -1;
                Matcher matcher = pattern.matcher(element);
                if (matcher.matches()) {
                    element = matcher.group(1);
                    index = Integer.parseInt(matcher.group(2));
                }

                property = currentMap.get(element);
                if (property == null) break;

                switch (property.getType()) {
                    case LIST:
                        if (index >= 0) {
                            Property<?> item = ((ListProperty) property).list.get(index);
                            if (item instanceof MapProperty) {
                                currentMap = ((MapProperty) item).map;
                            } else {
                                property = item;
                                break out;
                            }
                        } else {
                            break out;
                        }
                        break;
                    case MAP:
                        currentMap = ((MapProperty) property).map;
                        break;
                    case STRING:
                    case INTEGER:
                    case FLOAT:
                    case BOOLEAN:
                        break out;
                }
            }

            return property;
        }
    }

    public static final class StringProperty implements Property<String> {
        final String text;

        StringProperty(String text) {
            this.text = text;
        }

        @Override
        public Type getType() {
            return Type.STRING;
        }

        @Override
        public String getValue() {
            return text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public static final class BooleanProperty implements Property<Boolean> {
        final Boolean value;

        BooleanProperty(Boolean value) {
            this.value = value;
        }

        @Override
        public Type getType() {
            return Type.BOOLEAN;
        }

        @Override
        public Boolean getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    public static final class IntProperty implements Property<Integer> {
        final Integer value;

        IntProperty(Integer value) {
            this.value = value;
        }

        @Override
        public Type getType() {
            return Type.INTEGER;
        }

        @Override
        public Integer getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    public static final class FloatProperty implements Property<Float> {
        final Float value;

        FloatProperty(Float value) {
            this.value = value;
        }

        @Override
        public Type getType() {
            return Type.FLOAT;
        }

        @Override
        public Float getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    // Various tokens used to parse the structured data
    private static final byte TOKEN_STRING_START = 0x28;   // '('
    private static final byte TOKEN_STRING_END = 0x29;     // ')'
    private static final byte TOKEN_PROPERTY_START = 0x2F; // '/'
    private static final byte TOKEN_ARRAY_START = 0x5B;    // '['
    private static final byte TOKEN_ARRAY_END = 0x5D;      // ']'
    private static final byte TOKEN_SPACE = 0x20;          // ' '
    private static final byte TOKEN_TAB = 0x09;            // '\t'
    private static final byte TOKEN_LINE_END = 0x0A;       // '\n'
    private static final byte TOKEN_FLOAT = 0x2E;          // '.'
    private static final byte TOKEN_BOOLEAN_TRUE = 0x74;   // 't'
    private static final byte TOKEN_BOOLEAN_FALSE = 0x66;  // 'f'
    private static final byte[] TOKEN_MAP_START = new byte[] { 0x3C, 0x3C }; // "<<"
    private static final byte[] TOKEN_MAP_END = new byte[] { 0x3E, 0x3E };   // ">>"
    private static final byte[] TOKEN_BIG_ENDIAN = new byte[] { (byte) 0xFE, (byte) 0xFF };

    // Stores property names
    private Deque<String> nameStack = new LinkedList<>();
    // Stores maps and arrays
    private Deque<Property> stack = new LinkedList<>();

    /**
     * Checks whether all the bytes in the second array are present in the first
     * array, starting at the "start" offset.
     */
    private static boolean matches(byte[] l, byte[] r, int start) {
        for (int i = 0; i < r.length; i++) {
            if (l[start + i] != r[i]) return false;
        }
        return true;
    }

    /**
     * Decodes the specified structured text data as a MapProperty.
     * Photoshop always uses a map as the root element of the structured text.
     *
     * @param data The structured text as raw bytes
     *
     * @return A MapProperty instance, never null
     */
    public MapProperty parse(byte[] data) {
        MapProperty root = null;

        nameStack.clear();
        stack.clear();

        try {
            int pos = 0;
            while (pos < data.length) {
                // The data is stored indented in the PSD file
                // Photoshop always uses tabs as indents
                while (pos < data.length && data[pos] == TOKEN_TAB) pos++;
                int start = pos;
                // An '\n' marks the end of a line in the structured text
                // except when inside a String property
                while (pos < data.length && data[pos] != TOKEN_LINE_END) pos++;

                // Skip empty lines
                int length = pos - start;
                if (length == 0) {
                    pos++; // don't forget to advance over the line end marker
                    continue;
                }

                // Either << or >>, always marks a map
                if (length == 2) {
                    // If we start a map, push a new MapProperty on the stack
                    if (matches(data, TOKEN_MAP_START, start)) {
                        stack.offerFirst(new MapProperty());
                    } else if (matches(data, TOKEN_MAP_END, start)) {
                        // Found the end of a map, pop it off the stack and
                        // add it to wherever it belongs
                        root = (MapProperty) stack.pollFirst();
                        addProperty(root);
                    }
                } else if (length == 1 && data[start] == TOKEN_ARRAY_END) {
                    // Found the end of an array, pop it off the stack
                    addProperty(stack.pollFirst());
                } else if (data[start] == TOKEN_PROPERTY_START) {
                    // We found a named property, first read its name
                    // then figure out its type. The name is always followed by
                    // a space
                    int nameStart = start + 1;
                    while (start < pos && data[start] != TOKEN_SPACE) start++;
                    int nameEnd = start++;

                    // Push the name on the stack in case this property is
                    // a map or an array
                    String name = new String(data, nameStart, nameEnd - nameStart);
                    nameStack.offerFirst(name);

                    // If the last byte on this line is \n, then we have a map
                    // otherwise the property is a float, integer, boolean,
                    // array or string
                    if (data[nameEnd] != TOKEN_LINE_END) {
                        String value;
                        int valueLength = pos - start;

                        switch (data[start]) {
                            // The property is an array (single or multi line)
                            case TOKEN_ARRAY_START:
                                stack.offerFirst(new ListProperty());
                                // Single-line array
                                if (data[pos - 1] == TOKEN_ARRAY_END) {
                                    // All the elements have the same type and as far
                                    // as I can tell they are always numbers
                                    value = new String(data, start + 1, valueLength - 2);
                                    // Elements are space delimited
                                    for (String element : value.trim().split("\\s+")) {
                                        if (element.length() > 0) parseNumber(element);
                                    }
                                    addProperty(stack.pollFirst());
                                }
                                break;
                            // The property is a string
                            case TOKEN_STRING_START:
                                // A String starts with FEFF, which wrecks havoc
                                // with Java's encodings, let's be careful
                                if (matches(data, TOKEN_BIG_ENDIAN, start + 1)) {
                                    // Some strings can be multiline...
                                    while (data[pos - 1] != TOKEN_STRING_END) {
                                        pos++; // Advance over the last line end token
                                        while (pos < data.length && data[pos] != TOKEN_LINE_END) pos++;
                                    }

                                    // Decode the String as a big endian UTF-16 string
                                    value = new String(data, start + 3, pos - start - 4, "UTF-16BE");
                                    value = value.replace('\r', '\n');
                                    addProperty(new StringProperty(value));
                                }
                                break;
                            // The property is a float (starts with a '.')
                            case TOKEN_FLOAT:
                                value = new String(data, start, valueLength);
                                addProperty(new FloatProperty(Float.parseFloat(value)));
                                break;
                            // The property is a boolean
                            case TOKEN_BOOLEAN_TRUE:
                            case TOKEN_BOOLEAN_FALSE:
                                value = new String(data, start, valueLength);
                                addProperty(new BooleanProperty(Boolean.parseBoolean(value)));
                                break;
                            // The property is a number (floats start with either 0. or .)
                            default:
                                value = new String(data, start, valueLength);
                                parseNumber(value);
                                break;
                        }
                    }
                }
                pos++;
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Could not decode text engine data", e);
        }


        return root != null ? root : new MapProperty();
    }

    /**
     * Parse the specified String as a number and adds the resulting property
     * to the parent map or array. The number is either a float or an integer.
     */
    private void parseNumber(String value) {
        int decimalSeparator = value.indexOf('.');
        if (decimalSeparator == -1) {
            addProperty(new IntProperty(Integer.parseInt(value)));
        } else {
            addProperty(new FloatProperty(Float.parseFloat(value)));
        }
    }

    /**
     * Adds the specified property to the current parent map or array.
     */
    private void addProperty(Property property) {
        Property previous = stack.peekFirst();
        if (previous == null) return;

        switch (previous.getType()) {
            case LIST:
                ((ListProperty) previous).list.add(property);
                break;
            case MAP:
                String name = nameStack.pollFirst();
                ((MapProperty) previous).map.put(name, property);
                break;
            case STRING:
                break;
            case INTEGER:
                break;
            case FLOAT:
                break;
            case BOOLEAN:
                break;
        }
    }
}
