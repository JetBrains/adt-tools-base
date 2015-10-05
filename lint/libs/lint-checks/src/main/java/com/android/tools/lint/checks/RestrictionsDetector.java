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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_TITLE;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.STRING_PREFIX;
import static com.android.SdkConstants.XMLNS_PREFIX;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector.JavaScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.collect.Maps;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.List;
import java.util.Map;

/**
 * Check which makes sure that an application restrictions file is correct.
 * The rules are specified in
 * https://developer.android.com/reference/android/content/RestrictionsManager.html
 */
public class RestrictionsDetector extends ResourceXmlDetector implements JavaScanner {

    // Copied from Google Play store's AppRestrictionBuilder
    @VisibleForTesting static final int MAX_NESTING_DEPTH = 20;
    // Copied from Google Play store's AppRestrictionBuilder
    @VisibleForTesting static final int MAX_NUMBER_OF_NESTED_RESTRICTIONS = 1000;
    /**
     * Validation of {@code <restrictions>} XML elements
     */
    public static final Issue ISSUE = Issue.create(
            "ValidRestrictions", //$NON-NLS-1$
            "Invalid Restrictions Descriptor",

            "Ensures that an applications restrictions XML file is properly formed",

            Category.CORRECTNESS,
            5,
            Severity.FATAL,
            new Implementation(
                    RestrictionsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE))
            .addMoreInfo("https://developer.android.com/reference/android/content/RestrictionsManager.html");

    static final String TAG_RESTRICTIONS = "restrictions";
    static final String TAG_RESTRICTION = "restriction";
    static final String ATTR_RESTRICTION_TYPE = "restrictionType";
    static final String ATTR_KEY = "key";
    static final String ATTR_DESCRIPTION = "description";
    static final String VALUE_BUNDLE = "bundle";
    static final String VALUE_BUNDLE_ARRAY = "bundle_array";
    static final String VALUE_CHOICE = "choice";
    static final String VALUE_MULTI_SELECT = "multi-select";
    static final String VALUE_ENTRIES = "entries";
    static final String VALUE_ENTRY_VALUES = "entryValues";
    static final String VALUE_HIDDEN = "hidden";
    static final String VALUE_DEFAULT_VALUE = "defaultValue";
    static final String VALUE_INTEGER = "integer";

    /**
     * Constructs a new {@link RestrictionsDetector}
     */
    public RestrictionsDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        if (!TAG_RESTRICTIONS.equals(root.getTagName())) {
            return;
        }

        Map<String, Element> keys = Maps.newHashMap();
        validateNestedRestrictions(context, root, null, keys, 0);
    }

    /** Validates the {@code <restriction>} <b>children</b> of the given element */
    private static void validateNestedRestrictions(
            @NonNull XmlContext context,
            @NonNull  Element element,
            @Nullable String restrictionType,
            @NonNull  Map<String, Element> keys,
            int depth) {
        assert depth == 0 || restrictionType != null;

        List<Element> children = LintUtils.getChildren(element);

        // Only restrictions of type bundle and bundle_array can have one or multiple nested
        // restriction elements.
        if (depth == 0
                || restrictionType.equals(VALUE_BUNDLE)
                || restrictionType.equals(VALUE_BUNDLE_ARRAY)) {
            // Bundle and bundle array should not have a default value
            Attr defaultValue = element.getAttributeNodeNS(ANDROID_URI, VALUE_DEFAULT_VALUE);
            if (defaultValue != null) {
                context.report(ISSUE, element, context.getLocation(defaultValue),
                        String.format("Restriction type `%1$s` should not have a default value",
                                restrictionType));
            }
            for (Element child : children) {
                if (verifyRestrictionTagName(context, child)) {
                    validateRestriction(context, child, depth + 1, keys);
                }
            }

            //noinspection StatementWithEmptyBody
            if (depth == 0) {
                // It's okay to have <restrictions />
            } else if (restrictionType.equals(VALUE_BUNDLE_ARRAY)) {
                if (children.size() != 1) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "Expected exactly one child for restriction of type `bundle_array`");
                }
            } else {
                assert restrictionType.equals(VALUE_BUNDLE);
                if (children.isEmpty()) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "Restriction type `bundle` should have at least one nested "
                                    + "restriction");

                }
            }

            if (children.size() > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
                context.report(ISSUE, element, context.getLocation(element), String.format(
                        // TODO: Reference Google Play store restriction here in error message,
                        // e.g. that violating this will cause APK to be rejected?
                        "Invalid nested restriction: too many nested restrictions "
                                + "(was %1$d, max %2$d)",
                        children.size(), MAX_NUMBER_OF_NESTED_RESTRICTIONS));
            } else if (depth > MAX_NESTING_DEPTH) {
                // Same comment as for MAX_NUMBER_OF_NESTED_RESTRICTIONS: include source?
                context.report(ISSUE, element, context.getLocation(element), String.format(
                        "Invalid nested restriction: nesting depth %1$d too large (max %2$d",
                        depth, MAX_NESTING_DEPTH));
            }
        } else if (!children.isEmpty()) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "Only restrictions of type `bundle` and `bundle_array` can have "
                            + "one or multiple nested restriction elements");
        }
    }

    /** Validates a {@code <restriction>} element (and recurses to validate the children) */
    private static void validateRestriction(@NonNull XmlContext context, Node node, int depth,
            Map<String, Element> keys) {

        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        Element element = (Element)node;

        // key, title and restrictionType are mandatory.
        String restrictionType = checkRequiredAttribute(context, element, ATTR_RESTRICTION_TYPE);
        String key = checkRequiredAttribute(context, element, ATTR_KEY);
        String title = checkRequiredAttribute(context, element, ATTR_TITLE);
        if (restrictionType == null || key == null || title == null) {
            return;
        }

        // You use each restriction's android:key attribute to read its value from a
        // restrictions bundle. For this reason, each restriction must have a unique key string,
        // and the string cannot be localized. It must be specified with a string literal.
        if (key.startsWith(STRING_PREFIX)) {
            Attr attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY);
            Location valueLocation = context.getValueLocation(attribute);
            context.report(ISSUE, element, valueLocation,
                    "Keys cannot be localized, they should be specified with a string literal");
        } else if (keys.containsKey(key)) {
            Attr thisAttribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY);
            Location location = context.getValueLocation(
                    thisAttribute);
            Element prev = keys.get(key);
            Attr prevAttribute = prev.getAttributeNodeNS(ANDROID_URI, ATTR_KEY);
            Location previousLocation = context.getValueLocation(prevAttribute);
            previousLocation.setMessage("Previous use of key here");
            location.setSecondary(previousLocation);
            context.report(ISSUE, element, location, String.format("Duplicate key `%1$s`", key));
        } else {
            keys.put(key, element);
        }

        if (restrictionType.equals(VALUE_CHOICE) || restrictionType.equals(VALUE_MULTI_SELECT)) {
            // entries and entryValues are required if restrictionType is choice or multi-select.
            //noinspection unused
            boolean ok = // deliberate short circuit evaluation
                    checkRequiredAttribute(context, element, VALUE_ENTRIES) != null
                        || checkRequiredAttribute(context, element, VALUE_ENTRY_VALUES) != null;
        } else if (restrictionType.equals(VALUE_HIDDEN)) {
            // hidden type must have a defaultValue
            checkRequiredAttribute(context, element, VALUE_DEFAULT_VALUE);
        } else if (restrictionType.equals(VALUE_INTEGER)) {
            Attr defaultValue = element.getAttributeNodeNS(ANDROID_URI, VALUE_DEFAULT_VALUE);
            if (defaultValue != null && !defaultValue.getValue().startsWith(PREFIX_RESOURCE_REF)) {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    Integer.decode(defaultValue.getValue());
                } catch (NumberFormatException e) {
                    context.report(ISSUE, element, context.getValueLocation(defaultValue),
                            "Invalid number");
                }
            }
        }

        validateNestedRestrictions(context, element, restrictionType, keys, depth);
    }

    /**
     * Makes sure that the given element corresponds to a restriction tag, and if not, reports
     * it and return false */
    private static boolean verifyRestrictionTagName(@NonNull XmlContext context, Element element) {
        String tagName = element.getTagName();
        if (!tagName.equals(TAG_RESTRICTION)) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    String.format("Unexpected tag `<%1$s>`, expected `<%2$s>`",
                            tagName, TAG_RESTRICTION));
            return false;
        }
        return true;
    }

    private static String checkRequiredAttribute(@NonNull XmlContext context, Element element,
            String attribute) {
        if (!element.hasAttributeNS(ANDROID_URI, attribute)) {
            String prefix = element.getOwnerDocument().lookupNamespaceURI(ANDROID_URI);
            if (prefix == null) {
                Element root = element.getOwnerDocument().getDocumentElement();
                NamedNodeMap attributes = root.getAttributes();
                for (int i = 0, n = attributes.getLength(); i < n; i++) {
                    Attr a = (Attr)attributes.item(i);
                    if (a.getName().startsWith(XMLNS_PREFIX) &&
                            ANDROID_URI.equals(a.getValue())) {
                        prefix = a.getName().substring(XMLNS_PREFIX.length());
                        break;
                    }
                }
            }
            if (prefix != null) {
                attribute = prefix + ':' + attribute;
            }
            context.report(ISSUE, element, context.getLocation(element),
                    // TODO: Include namespace prefix?
                    String.format("Missing required attribute `%1$s`", attribute));
            return null;
        }
        return element.getAttributeNS(ANDROID_URI, attribute);
    }
}
