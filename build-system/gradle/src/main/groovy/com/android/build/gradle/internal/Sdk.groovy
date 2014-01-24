/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal

import com.android.annotations.NonNull
import com.android.build.gradle.BaseExtension
import com.android.builder.DefaultSdkParser
import com.android.builder.PlatformSdkParser
import com.android.builder.SdkParser
import com.android.sdklib.repository.FullRevision
import com.android.utils.ILogger
import com.google.common.base.Charsets
import org.gradle.api.Project

import static com.android.SdkConstants.FN_LOCAL_PROPERTIES
import static com.android.build.gradle.BasePlugin.TEST_SDK_DIR
import static com.google.common.base.Preconditions.checkNotNull

/**
 * Encapsulate finding, parsing, and initializing the SdkParser lazily.
 */
public class Sdk {

    @NonNull
    private final Project project
    @NonNull
    private final ILogger logger
    @NonNull
    private SdkParser parser

    private boolean isSdkParserInitialized = false
    private File androidSdkDir
    private File androidNdkDir
    private boolean isPlatformSdk = false
    private BaseExtension extension

    public Sdk(@NonNull Project project, @NonNull ILogger logger) {
        this.project = project
        this.logger = logger

        findLocation()
    }

    public void setExtension(@NonNull BaseExtension extension) {
        this.extension = extension
        parser = initParser()
    }


    public SdkParser getParser() {
        return parser
    }

    /**
     * Returns the parser, creating it if needed. This does not load it if it's not been loaded yet.
     *
     * @return the parser.
     *
     * @see #loadParser()
     */
    @NonNull
    private SdkParser initParser() {
        checkLocation()

        SdkParser parser;

        //noinspection GroovyIfStatementWithIdenticalBranches
        if (isPlatformSdk) {
            parser = new PlatformSdkParser(androidSdkDir.absolutePath)
        } else {
            parser = new DefaultSdkParser(androidSdkDir.absolutePath, androidNdkDir)
        }

        List<File> repositories = parser.repositories
        for (File file : repositories) {
            project.repositories.maven {
                url = file.toURI()
            }
        }

        return parser
    }

    /**
     * Loads and returns the parser. If it's not been created yet, this will do it too.
     *
     * {@link #setExtension(BaseExtension)} must have been called before.
     *
     * For more light weight usage, consider {@link #getParser()}
     *
     * @return the loaded parser.
     *
     * @see #getParser()
     */
    @NonNull
    public SdkParser loadParser() {
        checkNotNull(extension, "Extension has not been set")

        // call getParser to ensure it's created.
        SdkParser theParser = getParser()

        if (!isSdkParserInitialized) {
            String target = extension.getCompileSdkVersion()
            if (target == null) {
                throw new IllegalArgumentException("android.compileSdkVersion is missing!")
            }

            FullRevision buildToolsRevision = extension.buildToolsRevision
            if (buildToolsRevision == null) {
                throw new IllegalArgumentException("android.buildToolsVersion is missing!")
            }

            theParser.initParser(target, buildToolsRevision, logger)

            isSdkParserInitialized = true
        }

        return theParser
    }

    public File getSdkDirectory() {
        checkLocation()
        return androidSdkDir
    }

    public File getNdkDirectory() {
        checkLocation()
        return androidNdkDir
    }

    private void checkLocation() {
        // don't complain in test mode
        if (TEST_SDK_DIR != null) {
            return
        }

        if (androidSdkDir == null) {
            throw new RuntimeException(
                    "SDK location not found. Define location with sdk.dir in the local.properties file or with an ANDROID_HOME environment variable.")
        }

        if (!androidSdkDir.isDirectory()) {
            throw new RuntimeException(
                    "The SDK directory '$androidSdkDir.absolutePath' does not exist.")
        }
    }

    private void findLocation() {
        if (TEST_SDK_DIR != null) {
            androidSdkDir = TEST_SDK_DIR
            return
        }

        def rootDir = project.rootDir
        def localProperties = new File(rootDir, FN_LOCAL_PROPERTIES)
        if (localProperties.exists()) {
            Properties properties = new Properties()

            FileInputStream fis = new FileInputStream(localProperties)
            InputStreamReader reader = new InputStreamReader(fis, Charsets.UTF_8)
            try {
                properties.load(reader)
            } catch (IOException e) {
                throw new RuntimeException("Unable to read ${localProperties}", e)
            } finally {
                reader.close()
            }

            def sdkDirProp = properties.getProperty('sdk.dir')

            if (sdkDirProp != null) {
                androidSdkDir = new File(sdkDirProp)
            } else {
                sdkDirProp = properties.getProperty('android.dir')
                if (sdkDirProp != null) {
                    androidSdkDir = new File(rootDir, sdkDirProp)
                    isPlatformSdk = true
                } else {
                    throw new RuntimeException(
                            "No sdk.dir property defined in local.properties file.")
                }
            }

            def ndkDirProp = properties.getProperty('ndk.dir')
            if (ndkDirProp != null) {
                androidNdkDir = new File(ndkDirProp)
            }

        } else {
            String envVar = System.getenv("ANDROID_HOME")
            if (envVar != null) {
                androidSdkDir = new File(envVar)
            } else {
                String property = System.getProperty("android.home")
                if (property != null) {
                    androidSdkDir = new File(property)
                }
            }

            envVar = System.getenv("ANDROID_NDK_HOME")
            if (envVar != null) {
                androidNdkDir = new File(envVar)
            }
        }
    }
}
