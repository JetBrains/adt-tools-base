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

import com.android.resources.ResourceFolderType;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    @SuppressWarnings("ConstantConditions")
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

    @SuppressWarnings("ConstantConditions")
    public void testGetConfig1() {
        FolderConfiguration configForFolder =
                FolderConfiguration.getConfig(new String[] { "values", "en", "rUS" });
        assertNotNull(configForFolder);
        assertEquals("en", configForFolder.getLanguageQualifier().getValue());
        assertEquals("US", configForFolder.getRegionQualifier().getValue());
        assertNull(configForFolder.getScreenDimensionQualifier());
        assertNull(configForFolder.getLayoutDirectionQualifier());
    }

    @SuppressWarnings("ConstantConditions")
    public void testInvalidRepeats() {
        assertNull(FolderConfiguration.getConfigForFolder("values-en-rUS-rES"));
    }

    @SuppressWarnings("ConstantConditions")
    public void testGetConfig2() {
        FolderConfiguration configForFolder =
                FolderConfiguration.getConfigForFolder("values-en-rUS");
        assertNotNull(configForFolder);
        assertEquals("en", configForFolder.getLanguageQualifier().getValue());
        assertEquals("US", configForFolder.getRegionQualifier().getValue());
        assertNull(configForFolder.getScreenDimensionQualifier());
        assertNull(configForFolder.getLayoutDirectionQualifier());
    }

    @SuppressWarnings("ConstantConditions")
    public void testGetConfigCaseInsensitive() {
        FolderConfiguration configForFolder =
                FolderConfiguration.getConfigForFolder("values-EN-rus");
        assertNotNull(configForFolder);
        assertEquals("en", configForFolder.getLanguageQualifier().getValue());
        assertEquals("US", configForFolder.getRegionQualifier().getValue());
        assertNull(configForFolder.getScreenDimensionQualifier());
        assertNull(configForFolder.getLayoutDirectionQualifier());
        assertEquals("layout-en-rUS", configForFolder.getFolderName(ResourceFolderType.LAYOUT));

        runConfigMatchTest(
                "en-rgb-Port-HDPI-notouch-12key",
                3,
                "",
                "en",
                "fr-rCA",
                "en-port",
                "en-notouch-12key",
                "port-ldpi",
                "port-notouch-12key");
    }

    public void testToStrings() {
        FolderConfiguration configForFolder = FolderConfiguration.getConfigForFolder("values-en-rUS");
        assertNotNull(configForFolder);
        assertEquals("Locale Language en_Region US", configForFolder.toDisplayString());
        assertEquals("en,US", configForFolder.toShortDisplayString());
        assertEquals("layout-en-rUS", configForFolder.getFolderName(ResourceFolderType.LAYOUT));
        assertEquals("-en-rUS", configForFolder.getUniqueKey());
    }

    // --- helper methods

    private static final class MockConfigurable implements Configurable {

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

    private static void runConfigMatchTest(String refConfig, int resultIndex, String... configs) {
        FolderConfiguration reference = FolderConfiguration.getConfig(getFolderSegments(refConfig));
        assertNotNull(reference);

        List<? extends Configurable> list = getConfigurable(configs);

        Configurable match = reference.findMatchingConfigurable(list);
        assertEquals(resultIndex, list.indexOf(match));
    }

    private static List<? extends Configurable> getConfigurable(String... configs) {
        ArrayList<MockConfigurable> list = new ArrayList<MockConfigurable>();

        for (String config : configs) {
            list.add(new MockConfigurable(config));
        }

        return list;
    }

    private static String[] getFolderSegments(String config) {
        return (!config.isEmpty() ? "foo-" + config : "foo").split("-");
    }

    public void testSort1() {
        List<FolderConfiguration> configs = Lists.newArrayList();
        FolderConfiguration f1 = FolderConfiguration.getConfigForFolder("values-hdpi");
        FolderConfiguration f2 = FolderConfiguration.getConfigForFolder("values-v11");
        FolderConfiguration f3 = FolderConfiguration.getConfigForFolder("values-sp");
        FolderConfiguration f4 = FolderConfiguration.getConfigForFolder("values-v4");
        configs.add(f1);
        configs.add(f2);
        configs.add(f3);
        configs.add(f4);
        assertEquals(Arrays.asList(f1, f2, f3, f4), configs);
        Collections.sort(configs);
        assertEquals(Arrays.asList(f2, f4, f1, f3), configs);
    }

    public void testSort2() {
        // Test case from
        // http://developer.android.com/guide/topics/resources/providing-resources.html#BestMatch
        List<FolderConfiguration> configs = Lists.newArrayList();
        for (String name : new String[] {
                "drawable",
                "drawable-en",
                "drawable-fr-rCA",
                "drawable-en-port",
                "drawable-en-notouch-12key",
                "drawable-port-ldpi",
                "drawable-port-notouch-12key"
         }) {
            FolderConfiguration config = FolderConfiguration.getConfigForFolder(name);
            assertNotNull(name, config);
            configs.add(config);
        }
        Collections.sort(configs);
        Collections.reverse(configs);
        //assertEquals("", configs.get(0).toDisplayString());

        List<String> strings = Lists.newArrayList();
        for (FolderConfiguration config : configs) {
            strings.add(config.getUniqueKey());
        }
        assertEquals("-fr-rCA,-en-port,-en-notouch-12key,-en,-port-ldpi,-port-notouch-12key,",
                Joiner.on(",").skipNulls().join(strings));

    }
}
