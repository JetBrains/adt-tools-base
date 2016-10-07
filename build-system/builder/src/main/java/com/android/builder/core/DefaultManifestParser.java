/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.manifmerger.PlaceholderHandler;
import com.android.utils.XmlUtils;
import com.android.xml.AndroidManifest;
import com.android.xml.AndroidXPathFactory;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

/**
 * Implementation of the {@link ManifestAttributeSupplier}. The parser is build per manifest file,
 * and all values are extracted when the instance of this class is built.
 */
public class DefaultManifestParser implements ManifestAttributeSupplier {

    @NonNull
    private final Map<String, String> attributeValues;

    /**
     * Builds instance of the parser, and parses the supplied file.
     */
    public DefaultManifestParser(@NonNull File manifestFile) {
        if (!manifestFile.exists()) {
            attributeValues = Maps.newHashMap();
        } else {
            Set<String> xPaths =
                    Sets.newHashSet(
                            AndroidManifest.getPackageXPath(),
                            AndroidManifest.getVersionNameXPath(),
                            AndroidManifest.getVersionCodeXPath(),
                            AndroidManifest.getMinSdkVersionXPath(),
                            AndroidManifest.getTargetSdkVersionXPath(),
                            AndroidManifest.getInstrumentationRunnerXPath(),
                            AndroidManifest.getTestTargetPackageXPath(),
                            AndroidManifest.getTestFunctionalTestXPath(),
                            AndroidManifest.getTestHandleProfilingXPath(),
                            AndroidManifest.getTestLabelXPath(),
                            AndroidManifest.getExtractNativeLibsXPath());

            attributeValues = getStringValues(manifestFile, xPaths);
        }
    }

    /**
     * Gets the package name for the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public String getPackage() {
        return attributeValues.get(AndroidManifest.getPackageXPath());
    }

    /**
     * Gets the version name for the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public String getVersionName() {
        return attributeValues.get(AndroidManifest.getVersionNameXPath());
    }

    /**
     * Gets the version code for the manifest file processed by this parser.
     */
    @Override
    public int getVersionCode() {
        String versionCode = attributeValues.get(AndroidManifest.getVersionCodeXPath());
        return (int) parseIntValueOrDefault(versionCode, -1, -1);
    }

    /**
     * Gets the minimum sdk version for the manifest file processed by this parser.
     */
    @Override
    @NonNull
    public Object getMinSdkVersion() {
        String minSdkVersion = attributeValues.get(AndroidManifest.getMinSdkVersionXPath());
        return parseIntValueOrDefault(minSdkVersion, minSdkVersion, 1);
    }

    /**
     * Gets the target sdk version for the manifest file processed by this parser.
     */
    @Override
    @NonNull
    public Object getTargetSdkVersion() {
        String targetSdkVersion = attributeValues.get(AndroidManifest.getTargetSdkVersionXPath());
        return parseIntValueOrDefault(targetSdkVersion, targetSdkVersion, -1);
    }

    /**
     * Gets the instrumentation runner for the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public String getInstrumentationRunner() {
        return attributeValues.get(AndroidManifest.getInstrumentationRunnerXPath());
    }

    /**
     * Gets the target package for instrumentation in the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public String getTargetPackage() {
        return attributeValues.get(AndroidManifest.getTestTargetPackageXPath());
    }

    /**
     * Gets the functionalTest for instrumentation in the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public Boolean getFunctionalTest() {
        String functionalTest = attributeValues.get(AndroidManifest.getTestFunctionalTestXPath());
        return parseBoolean(functionalTest);
    }

    /**
     * Gets the handleProfiling for instrumentation in the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public Boolean getHandleProfiling() {
        String handleProfiling = attributeValues.get(AndroidManifest.getTestHandleProfilingXPath());
        return parseBoolean(handleProfiling);
    }

    /**
     * Gets the testLabel for instrumentation in the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public String getTestLabel() {
        return attributeValues.get(AndroidManifest.getTestLabelXPath());
    }

    @Nullable
    @Override
    public Boolean getExtractNativeLibs() {
        String extractNativeLibs = attributeValues.get(AndroidManifest.getExtractNativeLibsXPath());
        return parseBoolean(extractNativeLibs);
    }

    /**
     * If {@code value} is {@code null}, it returns {@code ifNull}. Otherwise it tries to parse the
     * {@code value} to {@link Integer}. If parsing the {@link Integer} fails, it will return {@code
     * ifNotInt} value.
     *
     * @param value    to be parsed
     * @param ifNotInt value returned if value is non {@code null} and it is not {@code int} value
     * @param ifNull   value returned if supplied value is {@code null}
     * @return final value according to the rules described above
     */
    private static Object parseIntValueOrDefault(String value, Object ifNotInt, Object ifNull) {
        if (value != null) {
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException ignored) {
                return ifNotInt;
            }
        } else {
            return ifNull;
        }
    }

    @Nullable
    private static Boolean parseBoolean(String value) {
        if (value != null) {
            return Boolean.parseBoolean(value);
        } else {
            return null;
        }
    }

    /**
     * Reads the file using the specified XPath expression values. It produces a map containing
     * XPath expressions as keys, and read value as the map entry value (in case the value is not
     * found, map entry value will be {@code null}).
     *
     * @param file   file to be read from
     * @param xPaths {@link Set} of XPath expressions that we are matching against in the file
     * @return map containing the supplied XPath expressions as keys, and read values as entry
     * values
     * @throws DefaultManifestParserException
     */
    @NonNull
    private static Map<String, String> getStringValues(
            @NonNull File file, @NonNull Set<String> xPaths) {

        try {
            Document document = XmlUtils.parseUtfXmlFile(file, true);

            Map<String, String> pathsToVals = Maps.newHashMap();

            for (String path : xPaths) {
                XPath xpath = AndroidXPathFactory.newXPath();
                Node node = (Node) xpath.evaluate(path, document, XPathConstants.NODE);

                String nodeValue = null;
                if (node != null && !Strings.isNullOrEmpty(node.getNodeValue())
                        && !PlaceholderHandler.isPlaceHolder(node.getNodeValue())) {
                    // if the node's value exists, and is not a placeholder, get the value
                    nodeValue = node.getNodeValue();
                }
                pathsToVals.put(path, nodeValue);
            }
            return pathsToVals;
        } catch (XPathExpressionException | SAXException | ParserConfigurationException
                | IOException e) {
            throw new DefaultManifestParserException(file, e);
        }
    }

    /**
     * Runtime exception thrown when something went bad with the manifest parsing
     */
    private static class DefaultManifestParserException extends RuntimeException {

        DefaultManifestParserException(@NonNull File file, @NonNull Throwable cause) {
            super("Exception while parsing the supplied manifest file " + file.getAbsolutePath(),
                    cause);
        }
    }
}
