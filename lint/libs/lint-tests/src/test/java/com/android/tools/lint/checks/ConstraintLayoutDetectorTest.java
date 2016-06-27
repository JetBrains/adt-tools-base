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

import static com.android.tools.lint.detector.api.TextFormat.TEXT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.Variant;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class ConstraintLayoutDetectorTest extends AbstractCheckTest {
    public void test1() throws Exception {
        assertEquals(""
                + "res/layout/layout1.xml:2: Error: Using version 1.0.0-alpha3 of the constraint library, which is obsolete [MissingConstraints]\n"
                + "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "^\n"
                + "res/layout/layout1.xml:19: Error: This view is not constrained, it only has designtime positions, so it will jump to (0,0) unless you add constraints [MissingConstraints]\n"
                + "    <TextView\n"
                + "    ^\n"
                + "res/layout/layout1.xml:43: Error: This view is not constrained vertically: at runtime it will jump to the left unless you add a vertical constraint [MissingConstraints]\n"
                + "    <TextView\n"
                + "    ^\n"
                + "res/layout/layout1.xml:53: Error: This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint [MissingConstraints]\n"
                + "    <TextView\n"
                + "    ^\n"
                + "4 errors, 0 warnings\n",
                lintProject(xml("res/layout/layout1.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    android:id=\"@+id/activity_main\"\n"
                        + "    android:layout_width=\"match_parent\"\n"
                        + "    android:layout_height=\"match_parent\"\n"
                        + "    tools:layout_editor_absoluteX=\"0dp\"\n"
                        + "    tools:layout_editor_absoluteY=\"81dp\"\n"
                        + "    tools:context=\"com.example.tnorbye.myapplication.MainActivity\"\n"
                        + "    tools:ignore=\"HardcodedText\">\n"
                        + "\n"
                        + "    <TextView\n"
                        + "        android:id=\"@+id/textView\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Not constrained and no designtime positions\" />\n"
                        + "\n"
                        + "    <TextView\n"
                        + "        android:id=\"@+id/textView2\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Not constrained\"\n"
                        + "        tools:layout_editor_absoluteX=\"21dp\"\n"
                        + "        tools:layout_editor_absoluteY=\"23dp\" />\n"
                        + "\n"
                        + "    <TextView\n"
                        + "        android:id=\"@+id/textView3\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Constrained both\"\n"
                        + "        app:layout_constraintBottom_creator=\"2\"\n"
                        + "        app:layout_constraintBottom_toBottomOf=\"@+id/activity_main\"\n"
                        + "        app:layout_constraintLeft_creator=\"2\"\n"
                        + "        app:layout_constraintLeft_toLeftOf=\"@+id/activity_main\"\n"
                        + "        app:layout_constraintRight_creator=\"2\"\n"
                        + "        app:layout_constraintRight_toRightOf=\"@+id/activity_main\"\n"
                        + "        app:layout_constraintTop_creator=\"2\"\n"
                        + "        app:layout_constraintTop_toTopOf=\"@+id/activity_main\"\n"
                        + "        tools:layout_editor_absoluteX=\"139dp\"\n"
                        + "        tools:layout_editor_absoluteY=\"247dp\" />\n"
                        + "\n"
                        + "    <TextView\n"
                        + "        android:id=\"@+id/textView4\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Constrained Horizontally\"\n"
                        + "        app:layout_constraintLeft_creator=\"0\"\n"
                        + "        app:layout_constraintLeft_toLeftOf=\"@+id/textView3\"\n"
                        + "        tools:layout_editor_absoluteX=\"139dp\"\n"
                        + "        tools:layout_editor_absoluteY=\"270dp\" />\n"
                        + "\n"
                        + "    <TextView\n"
                        + "        android:id=\"@+id/textView5\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Constrained Vertically\"\n"
                        + "        app:layout_constraintBaseline_creator=\"2\"\n"
                        + "        app:layout_constraintBaseline_toBaselineOf=\"@+id/textView4\"\n"
                        + "        tools:layout_editor_absoluteX=\"306dp\"\n"
                        + "        tools:layout_editor_absoluteY=\"270dp\" />\n"
                        + "\n"
                        + "    <android.support.constraint.Guideline\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:id=\"@+id/android.support.constraint.Guideline\"\n"
                        + "        app:orientation=\"vertical\"\n"
                        + "        tools:layout_editor_absoluteX=\"20dp\"\n"
                        + "        tools:layout_editor_absoluteY=\"0dp\"\n"
                        + "        app:relativeBegin=\"20dp\" />\n"
                        + "\n"
                        + "</android.support.constraint.ConstraintLayout>\n")));
    }

    @Override
    protected void checkReportedError(@NonNull Context context, @NonNull Issue issue,
            @NonNull Severity severity, @NonNull Location location, @NonNull String message) {
        assertEquals(message.contains("obsolete"),
                ConstraintLayoutDetector.isUpgradeDependencyError(message, TEXT));
    }

    @Override
    protected Detector getDetector() {
        return new ConstraintLayoutDetector();
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
                        /*
                        Simulate variant which has an AndroidLibrary with
                        resolved coordinates

                        com.android.support.constraint:constraint-layout:1.0.0-alpha3"
                         */
                        MavenCoordinates coordinates = mock(MavenCoordinates.class);
                        when(coordinates.getGroupId()).thenReturn("com.android.support.constraint");
                        when(coordinates.getArtifactId()).thenReturn("constraint-layout");
                        when(coordinates.getVersion()).thenReturn("1.0.0-alpha3");

                        AndroidLibrary library = mock(AndroidLibrary.class);
                        when(library.getResolvedCoordinates()).thenReturn(coordinates);
                        List<AndroidLibrary> libraries = Collections.singletonList(library);

                        Dependencies dependencies = mock(Dependencies.class);
                        when(dependencies.getLibraries()).thenReturn(libraries);

                        AndroidArtifact artifact = mock(AndroidArtifact.class);
                        //noinspection deprecation
                        when(artifact.getDependencies()).thenReturn(dependencies);
                        when(artifact.getCompileDependencies()).thenReturn(dependencies);
                        when(artifact.getCompileDependencies()).thenReturn(dependencies);

                        Variant variant = mock(Variant.class);
                        when(variant.getMainArtifact()).thenReturn(artifact);
                        return variant;
                    }
                };
            }
        };
    }
}