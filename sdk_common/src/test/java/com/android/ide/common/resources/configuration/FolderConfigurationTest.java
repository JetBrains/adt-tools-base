/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.ide.common.resources.configuration;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

public class FolderConfigurationTest extends TestCase {

    /*
     * Test createDefault creates all the qualifiers.
     */
    public void testCreateDefault() {
        FolderConfiguration defaultConfig = new FolderConfiguration();
        defaultConfig.createDefault();

        // this is always valid and up to date.
        final int count = FolderConfiguration.getQualifierCount();

        // make sure all the qualifiers were created.
        for (int i = 0 ; i < count ; i++) {
            assertNotNull(defaultConfig.getQualifier(i));
        }
    }

    public void testSimpleResMatch() {
        runConfigMatchTest(
                "en-rGB-port-hdpi-notouch-12key",
                3,
                "",
                "en",
                "fr-rCA",
                "en-port",
                "en-notouch-12key",
                "port-ldpi",
                "port-notouch-12key");
    }

    public void testVersionResMatch() {
        runConfigMatchTest(
                "en-rUS-w600dp-h1024dp-large-port-mdpi-finger-nokeys-v12",
                2,
                "",
                "large",
                "w540dp");
    }

    public void testAddQualifier() {
        FolderConfiguration defaultConfig = new FolderConfiguration();
        defaultConfig.createDefault();

        final int count = FolderConfiguration.getQualifierCount();
        for (int i = 0 ; i < count ; i++) {
            FolderConfiguration empty = new FolderConfiguration();

            ResourceQualifier q = defaultConfig.getQualifier(i);

            empty.addQualifier(q);

            // check it was added
            assertNotNull(
                    "addQualifier failed for " + q.getClass().getName(), empty.getQualifier(i));
        }
    }


    // --- helper methods

    private final static class MockConfigurable implements Configurable {

        private final FolderConfiguration mConfig;

        MockConfigurable(String config) {
            mConfig = FolderConfiguration.getConfig(getFolderSegments(config));
        }

        @Override
        public FolderConfiguration getConfiguration() {
            return mConfig;
        }

        @Override
        public String toString() {
            return mConfig.toString();
        }
    }

    private void runConfigMatchTest(String refConfig, int resultIndex, String... configs) {
        FolderConfiguration reference = FolderConfiguration.getConfig(getFolderSegments(refConfig));
        assertNotNull(reference);

        List<? extends Configurable> list = getConfigurable(configs);

        Configurable match = reference.findMatchingConfigurable(list);
        assertEquals(resultIndex, list.indexOf(match));
    }

    private List<? extends Configurable> getConfigurable(String... configs) {
        ArrayList<MockConfigurable> list = new ArrayList<MockConfigurable>();

        for (String config : configs) {
            list.add(new MockConfigurable(config));
        }

        return list;
    }

    private static String[] getFolderSegments(String config) {
        return (config.length() > 0 ? "foo-" + config : "foo").split("-");
    }
}
