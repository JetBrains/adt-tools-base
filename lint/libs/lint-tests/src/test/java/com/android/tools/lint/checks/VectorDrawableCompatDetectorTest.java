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

package com.android.tools.lint.checks;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.Variant;
import com.android.builder.model.VectorDrawablesOptions;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Project;

import org.intellij.lang.annotations.Language;

import java.io.File;
import java.util.Collections;

/**
 * Tests for {@link VectorDrawableCompatDetector}.
 */
public class VectorDrawableCompatDetectorTest extends AbstractCheckTest {

    @Language("XML")
    private static final String VECTOR =
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "        android:height=\"256dp\"\n"
            + "        android:width=\"256dp\"\n"
            + "        android:viewportWidth=\"32\"\n"
            + "        android:viewportHeight=\"32\">\n"
            + "    <path android:fillColor=\"#8fff\"\n"
            + "          android:pathData=\"M20.5,9.5\n"
            + "                        c-1.955,0,-3.83,1.268,-4.5,3\n"
            + "                        c-0.67,-1.732,-2.547,-3,-4.5,-3\n"
            + "                        C8.957,9.5,7,11.432,7,14\n"
            + "                        c0,3.53,3.793,6.257,9,11.5\n"
            + "                        c5.207,-5.242,9,-7.97,9,-11.5\n"
            + "                        C25,11.432,23.043,9.5,20.5,9.5z\" />\n"
            + "</vector>\n";

    @Language("XML")
    private static final String LAYOUT_SRC =
            "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "                xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n"
            + "    <ImageView android:src=\"@drawable/foo\" />\n"
            + "</RelativeLayout>\n";

    @Language("XML")
    private static final String LAYOUT_SRC_COMPAT =
            "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "                xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n"
            + "    <ImageView app:srcCompat=\"@drawable/foo\" />\n"
            + "</RelativeLayout>\n";

    @Override
    protected Detector getDetector() {
        return new VectorDrawableCompatDetector();
    }

    @Override
    protected TestLintClient createClient() {
        return new TestLintClient() {
            @NonNull
            @Override
            protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
                return new Project(this, dir, referenceDir) {
                    @Override
                    public boolean isGradleProject() {
                        return true;
                    }

                    @Nullable
                    @Override
                    public Variant getCurrentVariant() {
                        Variant onlyVariant = mock(Variant.class);
                        ProductFlavor productFlavor = mock(ProductFlavor.class);
                        VectorDrawablesOptions vectorDrawables = mock(VectorDrawablesOptions.class);

                        Dependencies dependencies = mock(Dependencies.class);
                        when(dependencies.getLibraries()).thenReturn(Collections.emptyList());
                        AndroidArtifact artifact = mock(AndroidArtifact.class);
                        when(artifact.getDependencies()).thenReturn(dependencies);
                        when(onlyVariant.getMainArtifact()).thenReturn(artifact);

                        when(onlyVariant.getMergedFlavor()).thenReturn(productFlavor);
                        when(productFlavor.getVectorDrawables()).thenReturn(vectorDrawables);

                        if (getName().contains("SrcCompat")) {
                            when(vectorDrawables.getUseSupportLibrary()).thenReturn(false);
                        } else {
                            when(vectorDrawables.getUseSupportLibrary()).thenReturn(true);
                        }

                        return onlyVariant;
                    }

                    @Nullable
                    @Override
                    public AndroidProject getGradleProjectModel() {
                        AndroidProject project = mock(AndroidProject.class);
                        when(project.getModelVersion()).thenReturn("2.0.0");
                        return project;
                    }
                };
            }
        };
    }

    public void testSrcCompat() throws Exception {
        assertEquals(""
                + "res/layout/main_activity.xml:3: Error: To use VectorDrawableCompat, you need to set android.defaultConfig.vectorDrawables.useSupportLibrary = true. [VectorDrawableCompat]\n"
                + "    <ImageView app:srcCompat=\"@drawable/foo\" />\n"
                + "               ~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",
                lintProject(
                        xml("res/drawable/foo.xml", VECTOR),
                        xml("res/layout/main_activity.xml", LAYOUT_SRC_COMPAT)
                ));
    }

    public void testSrcCompat_incremental() throws Exception {
        assertEquals(""
                        + "res/layout/main_activity.xml:3: Error: To use VectorDrawableCompat, you need to set android.defaultConfig.vectorDrawables.useSupportLibrary = true. [VectorDrawableCompat]\n"
                        + "    <ImageView app:srcCompat=\"@drawable/foo\" />\n"
                        + "               ~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                lintProjectIncrementally(
                        "res/layout/main_activity.xml",
                        xml("res/drawable/foo.xml", VECTOR),
                        xml("res/layout/main_activity.xml", LAYOUT_SRC_COMPAT)
                ));
    }

    public void testSrc() throws Exception {
        assertEquals(""
                + "res/layout/main_activity.xml:3: Error: When using VectorDrawableCompat, you need to use app:srcCompat. [VectorDrawableCompat]\n"
                + "    <ImageView android:src=\"@drawable/foo\" />\n"
                + "               ~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",
                lintProject(
                        xml("res/drawable/foo.xml", VECTOR),
                        xml("res/layout/main_activity.xml", LAYOUT_SRC)
                ));
    }

    public void testSrc_incremental() throws Exception {
        assertEquals(""
                + "res/layout/main_activity.xml:3: Error: When using VectorDrawableCompat, you need to use app:srcCompat. [VectorDrawableCompat]\n"
                + "    <ImageView android:src=\"@drawable/foo\" />\n"
                + "               ~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",
                lintProjectIncrementally(
                        "res/layout/main_activity.xml",
                        xml("res/drawable/foo.xml", VECTOR),
                        xml("res/layout/main_activity.xml", LAYOUT_SRC)
                ));
    }
}