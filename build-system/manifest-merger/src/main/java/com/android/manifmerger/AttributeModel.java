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
import com.google.common.base.Joiner;
import com.google.common.base.Optional;

import java.util.regex.Pattern;

/**
 * Describes an attribute characteristics like if it supports smart package name replacement, has
 * a default value and a validator for its values.
 */
class AttributeModel {

    @NonNull private final String mName;
    private final boolean mIsPackageDependent;
    @Nullable private final String mDefaultValue;
    @Nullable private final Validator mOnReadValidator;
    @Nullable private final Validator mOnWriteValidator;

    /**
     * Define a new attribute with specific characteristics.
     *
     * @param name name of the attribute, so far assumed to be in the
     *             {@link com.android.SdkConstants#ANDROID_URI} namespace.
     * @param isPackageDependent true if the attribute support smart substitution of package name.
     * @param defaultValue an optional default value.
     * @param onReadValidator an optional validator to validate values against.
     */
    private AttributeModel(@NonNull String name,
            boolean isPackageDependent,
            @Nullable String defaultValue,
            @Nullable Validator onReadValidator,
            @Nullable Validator onWriteValidator) {
        mName = name;
        mIsPackageDependent = isPackageDependent;
        mDefaultValue = defaultValue;
        mOnReadValidator = onReadValidator;
        mOnWriteValidator = onWriteValidator;
    }

    @NonNull
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

    /**
     * Returns the attribute's default value or null if none.
     */
    @Nullable
    String getDefaultValue() {
        return mDefaultValue;
    }

    /**
     * Returns the attribute's {@link com.android.manifmerger.AttributeModel.Validator} to
     * validate its value when read from xml files or null if no validation is necessary.
     */
    @Nullable
    public Validator getOnReadValidator() {
        return mOnReadValidator;
    }

    /**
     * Returns the attribute's {@link com.android.manifmerger.AttributeModel.Validator} to
     * validate its value when the merged file is about to be persisted.
     */
    @Nullable
    public Validator getOnWriteValidator() {
        return mOnWriteValidator;
    }

    /**
     * Creates a new {@link Builder} to describe an attribute.
     * @param attributeName the to be described attribute name
     */
    static Builder newModel(String attributeName) {
        return new Builder(attributeName);
    }

    static class Builder {

        private final String mName;
        private boolean mIsPackageDependent = false;
        private String mDefaultValue;
        private Validator mOnReadValidator;
        private Validator mOnWriteValidator;

        Builder(String name) {
            this.mName = name;
        }

        /**
         * Sets the attribute support for smart substitution of partially fully qualified
         * class names with package settings as provided by the manifest node's package attribute
         * {@link <a href=http://developer.android.com/guide/topics/manifest/manifest-element.html>}
         */
        Builder setIsPackageDependent() {
            mIsPackageDependent = true;
            return this;
        }

        /**
         * Sets the attribute default value.
         */
        Builder setDefaultValue(String value) {
            mDefaultValue =  value;
            return this;
        }

        /**
         * Sets a {@link com.android.manifmerger.AttributeModel.Validator} to validate the
         * attribute's values coming from xml files.
         */
        Builder setOnReadValidator(Validator validator) {
            mOnReadValidator = validator;
            return this;
        }

        /**
         * Sets a {@link com.android.manifmerger.AttributeModel.Validator} to validate values
         * before they are written to the final merged document.
         */
        Builder setOnWriteValidator(Validator validator) {
            mOnWriteValidator = validator;
            return this;
        }

        /**
         * Build an immutable {@link com.android.manifmerger.AttributeModel}
         */
        AttributeModel build() {
            return new AttributeModel(
                    mName, mIsPackageDependent, mDefaultValue, mOnReadValidator, mOnWriteValidator);
        }
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

    /**
     * A {@link com.android.manifmerger.AttributeModel.Validator} that validates a reference.
     * The referenced item must be present in the same document for a successful validation.
     */
    static class ReferenceValidator implements Validator {

        private final ManifestModel.NodeTypes referencedType;

        ReferenceValidator(ManifestModel.NodeTypes referencedType) {
            this.referencedType = referencedType;
        }


        @Override
        public boolean validates(@NonNull MergingReport.Builder mergingReport,
                @NonNull XmlAttribute attribute, @NonNull String value) {

            Optional<XmlElement> referencedElement = attribute.getOwnerElement().getDocument()
                    .getNodeByTypeAndKey(referencedType, value);
            if (!referencedElement.isPresent()) {
                mergingReport.addError(String.format(
                        "Referenced element %1$s=%2$s, in element %3$s declared at %4$s "
                                + "does not exist",
                        attribute.getName(),
                        value,
                        attribute.getOwnerElement().getId(),
                        attribute.printPosition()));
                return false;
            }
            return true;
        }
    }

    /**
     * A {@link com.android.manifmerger.AttributeModel.Validator} for verifying that a proposed
     * value is part of the acceptable list of possible values.
     */
    static class MultiValueValidator implements Validator {

        private final String[] multiValues;
        private final String allValues;

        MultiValueValidator(String... multiValues) {
            this.multiValues = multiValues;
            allValues = Joiner.on(',').join(multiValues);
        }

        @Override
        public boolean validates(@NonNull MergingReport.Builder mergingReport,
                @NonNull XmlAttribute attribute, @NonNull String value) {
            for (String multiValue : multiValues) {
                if (multiValue.equals(value)) {
                    return true;
                }
            }
            mergingReport.addError(String.format(
                    "Invalid value for attribute %1$s at %2$s, value=(%3$s), "
                            + "acceptable values are (%4$s)",
                    attribute.getId(),
                    attribute.printPosition(),
                    value,
                    allValues));
            return false;
        }
    }
}
