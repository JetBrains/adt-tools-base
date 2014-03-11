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
import com.android.utils.ILogger;
import com.google.common.base.Optional;

import java.util.Map;

/**
 * Validates a loaded {@XmlDocument} and check for potential inconsistencies in the model due to
 * user error or omission.
 *
 * This is implemented as a separate class so it can be invoked by tools independently from the
 * merging process.
 *
 * This validator will check the state of the loaded xml document before any merging activity is
 * attempted. It verifies things like a "tools:replace="foo" attribute has a "android:foo"
 * attribute also declared on the same element (since we want to replace its value).
 */
public class PreValidator {

    private MergingReport.Result mResult = MergingReport.Result.SUCCESS;

    private PreValidator() {}

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
     * @param xmlDocument the loaded xml part.
     * @param logger the logger to notify the user of possible inconsistencies.
     * @return one the {@link com.android.manifmerger.MergingReport.Result} value.
     */
    @NonNull
    public static MergingReport.Result validate(
            @NonNull XmlDocument xmlDocument,
            @NonNull ILogger logger) {

        return new PreValidator().validate(xmlDocument.getRootNode(), logger);
    }

    private MergingReport.Result validate(XmlElement xmlElement, ILogger logger) {

        // so far there is no node level validation.
        updateResult(validateAttributeInstructions(xmlElement, logger));

        for (XmlElement childElement : xmlElement.getMergeableElements()) {
            updateResult(validate(childElement, logger));
        }
        return mResult;
    }

    private MergingReport.Result validateAttributeInstructions(XmlElement xmlElement,
            ILogger logger) {

        MergingReport.Result result = MergingReport.Result.SUCCESS;
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
                        logger.error(null /* throwable */,
                                "tools:remove specified at line:%d for attribute %s, but "
                                        + "attribute also declared at line:%d, "
                                        + "do you want to use tools:replace instead ?",
                                xmlElement.getPosition().getLine(),
                                attributeOperationTypeEntry.getKey(),
                                attribute.get().getPosition().getLine());
                        result = MergingReport.Result.ERROR;
                    }
                    break;
                case REPLACE:
                    // check we are provided a new value
                    if (!attribute.isPresent()) {
                        logger.error(null /* throwable */,
                                "tools:replace specified at line:%d for attribute %s, but "
                                        + "no new value specified",
                                xmlElement.getPosition().getLine(),
                                attributeOperationTypeEntry.getKey());
                        result = MergingReport.Result.ERROR;
                    }
                    break;
                default:
                    throw new IllegalStateException("Unhandled AttributeOperationType " +
                            attributeOperationTypeEntry.getValue());
            }
        }
        return result;
    }


    private void updateResult(MergingReport.Result result) {
        if (result == MergingReport.Result.ERROR
                || (result == MergingReport.Result.WARNING
                && mResult != MergingReport.Result.ERROR)) {
            mResult = result;
        }
    }

}
