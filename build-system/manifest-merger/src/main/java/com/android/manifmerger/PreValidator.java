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

import static com.android.SdkConstants.ANDROID_URI;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.xml.AndroidManifest;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Map;

/**
 * Validates a loaded {@link XmlDocument} and check for potential inconsistencies in the model due
 * to user error or omission.
 *
 * This is implemented as a separate class so it can be invoked by tools independently from the
 * merging process.
 *
 * This validator will check the state of the loaded xml document before any merging activity is
 * attempted. It verifies things like a "tools:replace="foo" attribute has a "android:foo"
 * attribute also declared on the same element (since we want to replace its value).
 */
public class PreValidator {

    private PreValidator(){
    }

    /**
     * Validates a loaded {@link com.android.manifmerger.XmlDocument} and return a status of the
     * merging model.
     *
     * Will return one the following status :
     * <ul>
     *     <li>{@link com.android.manifmerger.MergingReport.Result#SUCCESS} : the merging model is
     *     correct, merging should be attempted</li>
     *     <li>{@link com.android.manifmerger.MergingReport.Result#WARNING} : the merging model
     *     contains non fatal error, user should be notified, merging can be attempted</li>
     *     <li>{@link com.android.manifmerger.MergingReport.Result#ERROR} : the merging model
     *     contains errors, user must be notified, merging should not be attempted</li>
     * </ul>
     *
     * A successful validation does not mean that the merging will be successful, it only means
     * that the {@link com.android.SdkConstants#TOOLS_URI} instructions are correct and consistent.
     *
     * @param mergingReport report to log warnings and errors.
     * @param xmlDocument the loaded xml part.
     * @return one the {@link com.android.manifmerger.MergingReport.Result} value.
     */
    @NonNull
    public static MergingReport.Result validate(
            @NonNull MergingReport.Builder mergingReport,
            @NonNull XmlDocument xmlDocument) {

        MergingReport.Result validated = validate(mergingReport, xmlDocument.getRootNode());
        if (validated == MergingReport.Result.SUCCESS) {
            splitUsesFeatureDeclarations(mergingReport, xmlDocument.getRootNode());
        }
        return validated;
    }

    private static MergingReport.Result validate(MergingReport.Builder mergingReport,
            XmlElement xmlElement) {

        validateAttributeInstructions(mergingReport, xmlElement);

        validateAndroidAttributes(mergingReport, xmlElement);

        // create a temporary hash map of children indexed by key to ensure key uniqueness.
        Map<String, XmlElement> childrenKeys = new HashMap<String, XmlElement>();
        for (XmlElement childElement : xmlElement.getMergeableElements()) {

            if (checkKeyPresence(mergingReport, childElement)) {
                XmlElement twin = childrenKeys.get(childElement.getId());
                if (twin != null) {
                    // we have 2 elements with the same identity, if they are equals,
                    // issue a warning, if not, issue an error.
                    String message = String.format(
                            "Element %1$s at %2$s duplicated with element declared at %3$s",
                            childElement.getId(),
                            childElement.printPosition(),
                            childrenKeys.get(childElement.getId()).printPosition());
                    if (twin.compareTo(childElement).isPresent()) {
                        mergingReport.addError(message);
                    } else {
                        mergingReport.addWarning(message);
                    }
                }
                childrenKeys.put(childElement.getId(), childElement);
            }
            validate(mergingReport, childElement);
        }
        return mergingReport.hasErrors()
                ? MergingReport.Result.ERROR : MergingReport.Result.SUCCESS;
    }

    /**
     * Checks that an element which is supposed to have a key does have one.
     * @param mergingReport report to log warnings and errors.
     * @param xmlElement xml element to check for key presence.
     * @return true if the element has a valid key or false it does not need one or it is invalid.
     */
    private static boolean checkKeyPresence(
            MergingReport.Builder mergingReport,
            XmlElement xmlElement) {
        ManifestModel.NodeKeyResolver nodeKeyResolver = xmlElement.getType().getNodeKeyResolver();
        ImmutableList<String> keyAttributesNames = nodeKeyResolver.getKeyAttributesNames();
        if (keyAttributesNames.isEmpty()) {
            return false;
        }
        if (Strings.isNullOrEmpty(xmlElement.getKey())) {
            // we should have a key but we don't.
            String message = keyAttributesNames.size() > 1
                    ? String.format(
                            "Missing one of the key attributes '%1$s' on element %2$s at %3$s",
                            Joiner.on(',').join(keyAttributesNames),
                            xmlElement.getId(),
                            xmlElement.printPosition())
                    : String.format(
                            "Missing '%1$s' key attribute on element %2$s at %3$s",
                            keyAttributesNames.get(0),
                            xmlElement.getId(),
                            xmlElement.printPosition());
            mergingReport.addError(message);
            return false;
        }
        return true;
    }

    /**
     * Validate attributes part of the {@link com.android.SdkConstants#ANDROID_URI}
     * @param mergingReport report to log warnings and errors.
     * @param xmlElement xml element to check its attributes.
     */
    private static void validateAndroidAttributes(MergingReport.Builder mergingReport,
            XmlElement xmlElement) {
        for (XmlAttribute xmlAttribute : xmlElement.getAttributes()) {
            AttributeModel model = xmlAttribute.getModel();
            if (model != null && model.getOnReadValidator() != null) {
                model.getOnReadValidator().validates(
                        mergingReport, xmlAttribute, xmlAttribute.getValue());
            }
        }
    }

    /**
     * Validates attributes part of the {@link com.android.SdkConstants#TOOLS_URI}
     * @param mergingReport report to log warnings and errors.
     * @param xmlElement xml element to check its attributes.
     */
    private static void validateAttributeInstructions(
            MergingReport.Builder mergingReport,
            XmlElement xmlElement) {

        for (Map.Entry<XmlNode.NodeName, AttributeOperationType> attributeOperationTypeEntry :
                xmlElement.getAttributeOperations()) {

            Optional<XmlAttribute> attribute = xmlElement
                    .getAttribute(attributeOperationTypeEntry.getKey());
            switch(attributeOperationTypeEntry.getValue()) {
                case STRICT:
                    break;
                case REMOVE:
                    // check we are not provided a new value.
                    if (attribute.isPresent()) {
                        mergingReport.addError(String.format(
                                "tools:remove specified at line:%d for attribute %s, but "
                                        + "attribute also declared at line:%d, "
                                        + "do you want to use tools:replace instead ?",
                                xmlElement.getPosition().getLine(),
                                attributeOperationTypeEntry.getKey(),
                                attribute.get().getPosition().getLine()));
                    }
                    break;
                case REPLACE:
                    // check we are provided a new value
                    if (!attribute.isPresent()) {
                        mergingReport.addError(String.format(
                                "tools:replace specified at line:%d for attribute %s, but "
                                        + "no new value specified",
                                xmlElement.getPosition().getLine(),
                                attributeOperationTypeEntry.getKey()
                        ));
                    }
                    break;
                default:
                    throw new IllegalStateException("Unhandled AttributeOperationType " +
                            attributeOperationTypeEntry.getValue());
            }
        }
    }

    /**
     * Finds {@link com.android.manifmerger.ManifestModel.NodeTypes#USES_FEATURE} elements which
     * have both android:name and android:glEsVersion attributes set and split such element into
     * two elements, one with each attribute value.
     *
     * @param mergingReport report to log warnings and errors.
     * @param xmlElement the {@link com.android.manifmerger.XmlElement} to check for such elements
     *                   presence.
     */
    private static void splitUsesFeatureDeclarations(MergingReport.Builder mergingReport,
            XmlElement xmlElement) {

        for (XmlElement childElement : xmlElement.getMergeableElements()) {

            if (childElement.getType() == ManifestModel.NodeTypes.USES_FEATURE) {
                // check if has name AND glEsVersion attributes.
                Element childXml = childElement.getXml();
                Attr name = childXml.getAttributeNodeNS(ANDROID_URI, SdkConstants.ATTR_NAME);
                Attr glEsVersion = childXml.getAttributeNodeNS(
                        ANDROID_URI, AndroidManifest.ATTRIBUTE_GLESVERSION);
                if (name != null && glEsVersion != null) {
                    // spit these declarations into 2.
                    Element sibling = (Element) childXml.cloneNode(true);
                    sibling.removeAttributeNS(ANDROID_URI, "name");
                    xmlElement.getXml().appendChild(sibling);
                    childXml.removeAttributeNS(ANDROID_URI, AndroidManifest.ATTRIBUTE_GLESVERSION);
                }
            }
        }

    }
}
