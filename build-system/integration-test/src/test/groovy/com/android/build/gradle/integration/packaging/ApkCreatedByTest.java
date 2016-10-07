/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.packaging;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.builder.internal.packaging.zip.StoredEntry;
import com.android.builder.internal.packaging.zip.ZFile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Properties;

@RunWith(FilterableParameterized.class)
public class ApkCreatedByTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Packaging.getParameters();
    }

    @Parameterized.Parameter
    public Packaging mPackaging;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @Test
    public void checkCreatedByInApk() throws Exception {
        project.executor().withPackaging(mPackaging).run("assembleDebug");

        File apk = project.getApk("debug");
        assertTrue(apk.isFile());

        try (ZFile zf = new ZFile(apk)) {
            StoredEntry manifestEntry = zf.get("META-INF/MANIFEST.MF");
            assertNotNull(manifestEntry);

            Properties props = new Properties();

            try (InputStream is = manifestEntry.open()) {
                props.load(is);
            }

            /*
             * This is required to keep track of gradle builds.
             */
            assertTrue(props.getProperty("Created-By").startsWith("Android Gradle"));
        }
    }
}
