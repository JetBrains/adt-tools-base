/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.common.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.google.common.annotations.Beta;

import java.util.EnumSet;

/**
 * Information about an attribute as gathered from the attrs.xml file where
 * the attribute was declared. This must include a format (string, reference, float, etc.),
 * possible flag or enum values, whether it's deprecated and its javadoc.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
@Beta
public interface IAttributeInfo {

    /** An attribute format, e.g. string, reference, float, etc. */
    public enum Format {
        STRING,
        BOOLEAN,
        INTEGER,
        FLOAT,
        COLOR,
        DIMENSION,
        FRACTION,
        ENUM,
        FLAG,
        REFERENCE;

        public static final EnumSet<Format> NONE = EnumSet.noneOf(Format.class);
        public static final EnumSet<Format> FLAG_SET = EnumSet.of(FLAG);
        public static final EnumSet<Format> ENUM_SET = EnumSet.of(ENUM);
        public static final EnumSet<Format> COLOR_SET = EnumSet.of(COLOR);
        public static final EnumSet<Format> STRING_SET = EnumSet.of(STRING);
        public static final EnumSet<Format> BOOLEAN_SET = EnumSet.of(BOOLEAN);
        public static final EnumSet<Format> INTEGER_SET = EnumSet.of(INTEGER);
        public static final EnumSet<Format> FLOAT_SET = EnumSet.of(FLOAT);
        public static final EnumSet<Format> DIMENSION_SET = EnumSet.of(DIMENSION);
        public static final EnumSet<Format> REFERENCE_SET = EnumSet.of(REFERENCE);

        /**
         * Returns an EnumSet containing only this format (which should not be
         * modified by the client)
         *
         * @return a new enum set containing exactly this format
         */
        @NonNull
        public EnumSet<Format> asSet() {
            switch (this) {
                case BOOLEAN:
                    return BOOLEAN_SET;
                case COLOR:
                    return COLOR_SET;
                case DIMENSION:
                    return DIMENSION_SET;
                case ENUM:
                    return ENUM_SET;
                case FLAG:
                    return FLAG_SET;
                case FLOAT:
                    return FLOAT_SET;
                case INTEGER:
                    return INTEGER_SET;
                case STRING:
                    return STRING_SET;
                case REFERENCE:
                    return REFERENCE_SET;
                case FRACTION:
                default:
                    return EnumSet.of(this);
            }
        }

        /** Returns the corresponding resource type for this attribute info,
         * or null if there is no known or corresponding resource type (such as for
         * enums and flags)
         *
         * @return the corresponding resource type, or null
         */
        @Nullable
        public ResourceType getResourceType() {
            switch (this) {
                case STRING:
                    return ResourceType.STRING;
                case BOOLEAN:
                    return ResourceType.BOOL;
                case COLOR:
                    return ResourceType.COLOR;
                case DIMENSION:
                    return ResourceType.DIMEN;
                case FRACTION:
                    return ResourceType.FRACTION;
                case INTEGER:
                    return ResourceType.INTEGER;

                // No direct corresponding resource type
                case ENUM:
                case FLAG:
                case FLOAT:
                case REFERENCE:
                    return null;
            }

            return null;
        }
    }

    /** Returns the XML Name of the attribute */
    @NonNull
    public String getName();

    /** Returns the formats of the attribute. Cannot be null.
     *  Should have at least one format. */
    @NonNull
    public EnumSet<Format> getFormats();

    /** Returns the values for enums. null for other types. */
    @Nullable
    public String[] getEnumValues();

    /** Returns the values for flags. null for other types. */
    @Nullable
    public String[] getFlagValues();

    /** Returns a short javadoc, .i.e. the first sentence. */
    @NonNull
    public String getJavaDoc();

    /** Returns the documentation for deprecated attributes. Null if not deprecated. */
    @Nullable
    public String getDeprecatedDoc();

    /** Returns the fully qualified class name of the view defining this attribute */
    @NonNull
    public String getDefinedBy();
}
