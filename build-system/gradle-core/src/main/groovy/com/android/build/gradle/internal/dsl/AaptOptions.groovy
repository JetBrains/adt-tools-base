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

package com.android.build.gradle.internal.dsl

import org.gradle.api.tasks.Input
/**
 * DSL object for configuring aapt options.
 */
public class AaptOptions implements com.android.builder.model.AaptOptions {

    @Input
    private String ignoreAssetsPattern

    @Input
    private List<String> noCompressList

    @Input
    private boolean useNewCruncher = true

    @Input
    private boolean cruncherEnabled = true

    @Input
    private boolean failOnMissingConfigEntry = false

    public void setIgnoreAssetsPattern(String ignoreAssetsPattern) {
        this.ignoreAssetsPattern = ignoreAssetsPattern
    }

    /**
     * Pattern describing assets to be ignore.
     *
     * <p>See <code>aapt --help</code>
     */
    @Override
    String getIgnoreAssets() {
        return ignoreAssetsPattern
    }

    public void setNoCompress(String noCompress) {
        noCompressList = Collections.singletonList(noCompress)
    }

    public void setNoCompress(String... noCompress) {
        noCompressList = Arrays.asList(noCompress)
    }

    /**
     * Extensions of files that will not be stored compressed in the APK.
     *
     * <p>Equivalent of the -0 flag. See <code>aapt --help</code>
     */
    @Override
    Collection<String> getNoCompress() {
        return noCompressList
    }

    public void useNewCruncher(boolean value) {
        useNewCruncher = value
    }

    public void setUseNewCruncher(boolean value) {
        useNewCruncher = value
    }

    /**
     * Enables or disables PNG crunching.
     */
    public void setCruncherEnabled(boolean value) {
        cruncherEnabled = value
    }

    /**
     * Returns true if the PNGs should be crunched, false otherwise.
     */
    public boolean getCruncherEnabled() {
        return cruncherEnabled
    }
    
    /**
     * Whether to use the new cruncher.
     */
    public boolean getUseNewCruncher() {
        return useNewCruncher
    }

    public void failOnMissingConfigEntry(boolean value) {
        failOnMissingConfigEntry = value
    }

    public void setFailOnMissingConfigEntry(boolean value) {
        failOnMissingConfigEntry = value
    }

    /**
     * Forces aapt to return an error if it fails to find an entry for a configuration.
     *
     * <p>See <code>aapt --help</code>
     */
    @Override
    public boolean getFailOnMissingConfigEntry() {
        return failOnMissingConfigEntry;
    }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    /**
     * Sets extensions of files that will not be stored compressed in the APK.
     *
     * <p>Equivalent of the -0 flag. See <code>aapt --help</code>
     */
    public void noCompress(String noCompress) {
        noCompressList = Collections.singletonList(noCompress)
    }

    /**
     * Sets extensions of files that will not be stored compressed in the APK.
     *
     * <p>Equivalent of the -0 flag. See <code>aapt --help</code>
     */
    public void noCompress(String... noCompress) {
        noCompressList = Arrays.asList(noCompress)
    }
}
