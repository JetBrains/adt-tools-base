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



package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.google.common.collect.ImmutableList
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_KEY_ALIAS
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_KEY_PASSWORD
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_STORE_FILE
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_STORE_PASSWORD

/**
 * Integration test with signing overrider.
 */
@CompileStatic
class SigningConfigTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("basic")
            .create()

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void "check assemble with injected signing config"() {
        // add prop args for signing override.
        List<String> args = ImmutableList.of(
                "-P" + PROPERTY_SIGNING_STORE_FILE + "=" + project.file("debug.keystore").getPath(),
                "-P" + PROPERTY_SIGNING_STORE_PASSWORD + "=android",
                "-P" + PROPERTY_SIGNING_KEY_ALIAS + "=AndroidDebugKey",
                "-P" + PROPERTY_SIGNING_KEY_PASSWORD + "=android")

        project.execute(args, "clean", "assembleRelease")

        // Check for signing file inside the archive.
        assertThatZip(project.getApk("release")).contains("META-INF/CERT.RSA")
    }
}
