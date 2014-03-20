/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.manifmerger;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Describes an attribute characteristics like if it supports smart package name replacement, has
 * a default value and a validator for its values.
 */
class AttributeModel {

    @NonNull private final String mName;
    private final boolean mIsPackageDependent;
    @Nullable private final String mDefaultValue;
    @Nullable private final Validator mValidator;

    /**
     * Define a new attribute with specific characteristics.
     *
     * @param name name of the attribute, so far assumed to be in the
     *             {@link com.android.SdkConstants#ANDROID_URI} namespace.
     * @param isPackageDependent true if the attribute support smart substitution of package name.
     * @param defaultValue an optional default value.
     * @param validator an optional validator to validate values against.
     */
    AttributeModel(@NonNull String name,
            boolean isPackageDependent,
            @Nullable String defaultValue,
            @Nullable Validator validator) {
        mName = name;
        mIsPackageDependent = isPackageDependent;
        mDefaultValue = defaultValue;
        mValidator = validator;
    }

    String getName() {
        return mName;
    }

    /**
     * Return true if the attribute support smart substitution of partially fully qualified
     * class names with package settings as provided by the manifest node's package attribute
     * {@link <a href=http://developer.android.com/guide/topics/manifest/manifest-element.html>}
     *
     * @return true if this attribute supports smart substitution or false if not.
     */
    boolean isPackageDependent() {
        return mIsPackageDependent;
    }

    @Nullable
    String getDefaultValue() {
        return mDefaultValue;
    }

    /**
     * Validates an attribute value.
     *
     * The validator can be called when xml documents are read to ensure the xml file contains
     * valid statements.
     *
     * This is a poor-mans replacement for not having a proper XML Schema do perform such
     * validations.
     */
    static interface Validator {

        /**
         * Validates a value, issuing a warning or error in case of failure through the passed
         * merging report.
         * @param mergingReport to report validation warnings or error
         * @param attribute the attribute to validate.
         * @param value the proposed or existing attribute value.
         * @return true if the value is legal for this attribute.
         */
        boolean validates(@NonNull MergingReport.Builder mergingReport,
                @NonNull XmlAttribute attribute,
                @NonNull String value);
    }

    /**
     * Validates a boolean attribute type.
     */
    static class BooleanValidator implements Validator {

        // TODO: check with @xav where to find the acceptable values by runtime.
        private static final Pattern PATTERN = Pattern.compile("true|false|TRUE|FALSE|True|False");

        @Override
        public boolean validates(@NonNull MergingReport.Builder mergingReport,
                @NonNull XmlAttribute attribute,
                @NonNull String value) {
            boolean matches = PATTERN.matcher(value).matches();
            if (!matches) {
                mergingReport.addError(
                        String.format(
                                "Attribute %1$s at %2$s has an illegal value=(%3$s), "
                                        + "expected 'true' or 'false'",
                                attribute.getId(),
                                attribute.printPosition(),
                                value));
            }
            return matches;
        }
    }

}
