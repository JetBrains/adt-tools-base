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

package com.android.build.gradle.internal.transforms;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.apache.commons.io.output.TeeOutputStream;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

@RunWith(MockitoJUnitRunner.class)
public class ExtractJarsTransformTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    Context context;

    @Mock
    TransformOutputProvider transformOutputProvider;

    @Mock
    Logger logger;

    @Before
    public void setLogger() {
        ExtractJarsTransform.LOGGER = logger;
    }

    @Test
    public void checkWarningForPotentialIssuesOnCaseSensitiveFileSystems()
            throws Exception {
        File jar = temporaryFolder.newFile("Jar with case issues.jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(jar))) {
            jarOutputStream.putNextEntry(new ZipEntry("com/example/a.class"));
            jarOutputStream.closeEntry();
            jarOutputStream.putNextEntry(new ZipEntry("com/example/A.class"));
            jarOutputStream.closeEntry();
            jarOutputStream.putNextEntry(new ZipEntry("com/example/B.class"));
            jarOutputStream.closeEntry();
        }
        checkWarningForCaseIssues(jar, true  /*expectingWarning*/);
    }

    @Test
    public void checkNoWarningWhenWillNotHaveIssuesOnCaseSensitiveFileSystems()
            throws Exception {
        File jar = temporaryFolder.newFile("Jar without case issues.jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(jar))) {
            jarOutputStream.putNextEntry(new ZipEntry("com/example/a.class"));
            jarOutputStream.closeEntry();
            jarOutputStream.putNextEntry(new ZipEntry("com/example/B.class"));
            jarOutputStream.closeEntry();
        }
        checkWarningForCaseIssues(jar, false /*expectingWarning*/);
    }

    private void checkWarningForCaseIssues(@NonNull File jar, boolean expectingWarning)
            throws IOException, TransformException, InterruptedException {

        ExtractJarsTransform transform =
                new ExtractJarsTransform(
                        ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES),
                        ImmutableSet.of(QualifiedContent.Scope.SUB_PROJECTS));

        File outputFolder = temporaryFolder.newFolder();
        when(transformOutputProvider.getContentLocation(any(), any(), any(), any()))
                .thenReturn(outputFolder);

        List<TransformInput> inputList = ImmutableList.of(asJarInput(jar, Status.NOTCHANGED));


        transform.transform(new TransformInvocationBuilder(context)
                .addInputs(inputList)
                .addOutputProvider(transformOutputProvider)
                .build());
        if (expectingWarning) {
            verify(logger).error(anyString(), eq((Object)jar.getAbsolutePath()));
        }
        verifyNoMoreInteractions(logger);
    }

    private static TransformInput asJarInput(@NonNull File jarFile, @NonNull Status status) {
        return new TransformInput() {
            @NonNull
            @Override
            public Collection<JarInput> getJarInputs() {
                return ImmutableList.of(new JarInput() {
                    @NonNull
                    @Override
                    public Status getStatus() {
                        return status;
                    }

                    @NonNull
                    @Override
                    public String getName() {
                        return "test-jar";
                    }

                    @NonNull
                    @Override
                    public File getFile() {
                        return jarFile;
                    }

                    @NonNull
                    @Override
                    public Set<ContentType> getContentTypes() {
                        return ImmutableSet.of(DefaultContentType.CLASSES);
                    }

                    @NonNull
                    @Override
                    public Set<Scope> getScopes() {
                        return ImmutableSet.of(Scope.SUB_PROJECTS);
                    }
                });
            }

            @NonNull
            @Override
            public Collection<DirectoryInput> getDirectoryInputs() {
                return Collections.emptyList();
            }
        };
    }
}
