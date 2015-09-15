/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.manifmerger;

import static com.android.manifmerger.ManifestMergerTestUtil.loadTestData;
import static com.android.manifmerger.ManifestMergerTestUtil.transformParameters;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.sdklib.mock.MockLog;
import com.google.common.base.Preconditions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.w3c.dom.Document;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Some utilities to reduce repetitions in the {@link ManifestMergerTest} and
 * {@link ManifestMerger2Test}. <p/> See {@link
 * ManifestMergerTestUtil#loadTestData(String, String, String)} for an explanation of the data
 * file format.
 */

@RunWith(Parameterized.class)
public class ManifestMergerTest {

    private static final String TEST_DATA_DIRECTORY = "data";

    @SuppressWarnings("SpellCheckingInspection")
    private static final String[] DATA_FILES = new String[]{
            "00_noop",
            "01_ignore_app_attr",
            "02_ignore_instrumentation",
            "03_inject_attributes",
            "04_inject_attributes",
            "05_inject_package",
            "10_activity_merge",
            "11_activity_dup",
            "12_alias_dup",
            "13_service_dup",
            "14_receiver_dup",
            "15_provider_dup",
            "16_fqcn_merge",
            "17_fqcn_conflict",
            "20_uses_lib_merge",
            "21_uses_lib_errors",
            "25_permission_merge",
            "26_permission_dup",
            "28_uses_perm_merge",
            "30_uses_sdk_ok",
            "32_uses_sdk_minsdk_ok",
            "33_uses_sdk_minsdk_conflict",
            "36_uses_sdk_targetsdk_warning",
            "40_uses_feat_merge",
            "41_uses_feat_errors",
            "45_uses_feat_gles_once",
            "47_uses_feat_gles_conflict",
            "50_uses_conf_warning",
            "52_support_screens_warning",
            "54_compat_screens_warning",
            "56_support_gltext_warning",
            "60_merge_order",
            "65_override_app",
            "66_remove_app",
            "67_override_activities",
            "68_override_uses",
            "69_remove_uses",
            "70_expand_fqcns",
            "71_extract_package_prefix",
            "75_app_metadata_merge",
            "76_app_metadata_ignore",
            "77_app_metadata_conflict",
    };


    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> getParameters() {
        return transformParameters(DATA_FILES);
    }

    public ManifestMergerTest(String fileName) {
        this.fileName = fileName;
    }

    private final String fileName;


    @Test
    public void processTestFiles() throws Exception {
        ManifestMergerTestUtil.TestFiles testFiles =
                loadTestData(TEST_DATA_DIRECTORY, fileName, getClass().getSimpleName());

        MockLog log = new MockLog();
        IMergerLog mergerLog = MergerLog.wrapSdkLog(log);
        ManifestMerger merger = new ManifestMerger(mergerLog, new ICallback() {
            @Override
            public int queryCodenameApiLevel(@NonNull String codename) {
                if ("ApiCodename1".equals(codename)) {
                    return 1;
                } else if ("ApiCodename10".equals(codename)) {
                    return 10;
                }
                return ICallback.UNKNOWN_CODENAME;
            }
        });

        for (Map.Entry<String, Boolean> feature : testFiles.getFeatures().entrySet()) {
            Method m = merger.getClass().getMethod(
                    feature.getKey(),
                    boolean.class);
            m.invoke(merger, feature.getValue());
        }

        boolean processOK = merger.process(testFiles.getActualResult(),
                testFiles.getMain(),
                testFiles.getLibs(),
                testFiles.getInjectAttributes(),
                testFiles.getPackageOverride());

        // Convert relative path names to absolute.
        String expectedErrors = testFiles.getExpectedErrors().trim();
        expectedErrors = expectedErrors.replaceAll(
                Pattern.quote(testFiles.getMain().getName()),
                Matcher.quoteReplacement(testFiles.getMain().getAbsolutePath()));
        for (File file : testFiles.getLibs()) {
            expectedErrors = expectedErrors.replaceAll(
                    Pattern.quote(file.getName()),
                    Matcher.quoteReplacement(file.getAbsolutePath()));
        }

        StringBuilder actualErrors = new StringBuilder();
        for (String s : log.getMessages()) {
            actualErrors.append(s);
            if (!s.endsWith("\n")) {
                actualErrors.append('\n');
            }
        }
        assertEquals("Error generated during merging",
                expectedErrors, actualErrors.toString().trim());

        if (testFiles.getShouldFail()) {
            assertFalse("Merge process() returned true, expected false", processOK);
        } else {
            assertTrue("Merge process() returned false, expected true", processOK);
        }

        // Test result XML. There should always be one created
        // since the process action does not stop on errors.
        log.clear();
        Document document = MergerXmlUtils.parseDocument(
                Preconditions.checkNotNull(testFiles.getActualResult()),
                mergerLog,
                merger);
        assertNotNull(document);
        String actual = MergerXmlUtils.printXmlString(document, mergerLog);
        assertEquals("Error parsing actual result XML", "", log.toString());
        log.clear();
        document = MergerXmlUtils.parseDocument(
                testFiles.getExpectedResult(),
                mergerLog,
                new IMergerLog.FileAndLine("<expected-result>", 0));
        assertNotNull("Failed to parse result document: " + testFiles.getExpectedResult(),
                document);
        String expected = MergerXmlUtils.printXmlString(document, mergerLog);
        assertEquals("Error parsing expected result XML", "", log.toString());
        assertEquals("Error comparing expected to actual result", expected, actual);

        testFiles.cleanup();
    }
}
