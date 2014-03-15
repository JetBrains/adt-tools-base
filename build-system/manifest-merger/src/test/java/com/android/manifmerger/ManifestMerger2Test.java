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

import static com.android.manifmerger.ManifestMerger2.SystemProperty;

import com.android.annotations.Nullable;
import com.android.utils.StdLogger;
import com.google.common.base.Optional;
import com.google.common.base.Strings;

import junit.framework.Test;
import junit.framework.TestSuite;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Tests for the {@link com.android.manifmerger.ManifestMerger2} class
 */
public class ManifestMerger2Test extends ManifestMergerTest {

    // so far, I only support 3 original tests.
    private static String[] sDataFiles = new String[]{
            "00_noop",
            "03_inject_attributes.xml",
            "05_inject_package.xml",
            "06_inject_attributes_with_specific_prefix.xml",
            "10_activity_merge",
            "11_activity_dup",
    };

    @Override
    protected String getTestDataDirectory() {
        return "data2";
    }

    /**
     * This overrides the default test suite created by junit. The test suite is a bland TestSuite
     * with a dedicated name. We inject as many instances of {@link ManifestMergerTest} in the suite
     * as we have declared data files above.
     *
     * @return A new {@link junit.framework.TestSuite}.
     */
    public static Test suite() {
        TestSuite suite = new TestSuite();
        // Give a non-generic name to our test suite, for better unit reports.
        suite.setName("ManifestMergerTestSuite");

        for (String fileName : sDataFiles) {
            suite.addTest(TestSuite.createTest(ManifestMerger2Test.class, fileName));
        }

        return suite;
    }

    public ManifestMerger2Test(String testName) {
        super(testName);
    }

    /**
     * Processes the data from the given
     * {@link com.android.manifmerger.ManifestMergerTest.TestFiles} by invoking {@link
     * ManifestMerger#process(java.io.File, java.io.File, java.io.File[], java.util.Map, String)}:
     * the given library files are applied consecutively to the main XML document and the output is
     * generated. <p/> Then the expected and actual outputs are loaded into a DOM, dumped again to a
     * String using an XML transform and compared. This makes sure only the structure is checked and
     * that any formatting is ignored in the comparison.
     *
     * @param testFiles The test files to process. Must not be null.
     * @throws Exception when this go wrong.
     */
    @Override
    void processTestFiles(TestFiles testFiles) throws Exception {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        StdLogger stdLogger = new StdLogger(StdLogger.Level.VERBOSE);
        ManifestMerger2.Invoker invoker = ManifestMerger2.newInvoker(testFiles.getMain(),
                stdLogger)
                .addLibraryManifests(testFiles.getLibs());

        if (!Strings.isNullOrEmpty(testFiles.getPackageOverride())) {
            invoker.setOverride(SystemProperty.PACKAGE, testFiles.getPackageOverride());
        }

        for (Map.Entry<String, String> injectable : testFiles.getInjectAttributes().entrySet()) {
            SystemProperty systemProperty = getSystemProperty(injectable.getKey());
            if (systemProperty != null) {
                invoker.setOverride(systemProperty, injectable.getValue());
            } else {
                invoker.setPlaceHolderValue(injectable.getKey(), injectable.getValue());
            }
        }

        MergingReport mergeReport = invoker.merge();

        XmlDocument expectedResult = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), testFiles.getMain().getName()),
                testFiles.getExpectedResult());

        // this is obviously quite hacky, refine once merge output is better defined.
        boolean notExpectingError =
                testFiles.getExpectedErrors().isEmpty() ||
                        testFiles.getExpectedErrors().charAt(0) != 'E';
        assertEquals(notExpectingError, mergeReport.getMergedDocument().isPresent());
        if (notExpectingError) {

            XmlDocument actualResult = mergeReport.getMergedDocument().get();
            actualResult.write(byteArrayOutputStream);

            mergeReport.log(stdLogger);

            // saves the result to the external file for easier human parsing.
            OutputStream fos = null;
            try {
                fos = new BufferedOutputStream(new FileOutputStream(testFiles.getActualResult()));
                actualResult.write(fos);
            } finally {
                if (fos != null)
                    fos.close();
            }

            stdLogger.info(byteArrayOutputStream.toString());
            stdLogger.info(testFiles.getExpectedErrors());

            Optional<String> comparingMessage =
                    expectedResult.compareTo(actualResult);

            if (comparingMessage.isPresent()) {
                Logger.getAnonymousLogger().severe(comparingMessage.get());
                fail(comparingMessage.get());
            }
        } else {
            for (MergingReport.Record record : mergeReport.getLoggingRecords()) {
                Logger.getAnonymousLogger().info("Expected test error: " + record);
            }
        }

    }

    @Nullable
    private SystemProperty getSystemProperty(String name) {
        for (SystemProperty systemProperty : SystemProperty.values()) {
            if (systemProperty.toCamelCase().equals(name)) {
                return systemProperty;
            }
        }
        return null;
    }
}
