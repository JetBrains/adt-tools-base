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

package com.android.util;


import com.android.annotations.NonNull;

import java.util.*;

/**
 * LayoutLib can return properties that a View asked for at the time of inflation. This map is from
 * the property name (XML attribute name) to the value - both pre and post resolution.
 */
public class PropertiesMap extends HashMap<String, PropertiesMap.Property> {

    public static final PropertiesMap EMPTY_MAP = new EmptyMap();

    public PropertiesMap() {
    }

    public PropertiesMap(int capacity) {
        super(capacity);
    }

    public static class Property {

        /**
         * Pre-resolution resource value
         */
        public final String resource;
        /**
         * Post-resolution value
         */
        public final String value;

        public Property(String resource, String value) {
            this.resource = resource;
            this.value = value;
        }

        public boolean equals(Object other) {
            if (!(other instanceof Property)) {
                return false;
            }
            Property otherProperty = (Property)other;
            return Objects.equals(resource, otherProperty.resource) &&
                   Objects.equals(value, otherProperty.value);
        }

        public int hashCode() {
            return Objects.hash(resource, value);
        }
    }

    private static class EmptyMap extends PropertiesMap {

        EmptyMap() {
            super(0);
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Property get(Object key) {
            return null;
        }

        @Override
        public boolean containsKey(Object key) {
            return false;
        }

        @Override
        public Property put(String key, Property value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void putAll(Map<? extends String, ? extends Property> m) {
            throw new UnsupportedOperationException();

        }

        @Override
        public Property remove(Object key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
        }

        @Override
        public boolean containsValue(Object value) {
            return false;
        }

        @NonNull
        @Override
        public Set<String> keySet() {
            return Collections.emptySet();
        }

        @NonNull
        @Override
        public Collection<Property> values() {
            return Collections.emptySet();
        }

        @NonNull
        @Override
        public Set<Entry<String, Property>> entrySet() {
            return Collections.emptySet();
        }

        @Override
        public Property getOrDefault(Object key, Property defaultValue) {
            return defaultValue;
        }

        @Override
        public Property putIfAbsent(String key, Property value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object key, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean replace(String key, Property oldValue, Property newValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Property replace(String key, Property value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Map) && ((Map<?, ?>) o).isEmpty();
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }
}
