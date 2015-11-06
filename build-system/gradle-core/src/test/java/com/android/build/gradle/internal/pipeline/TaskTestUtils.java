/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.pipeline;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.TaskContainerAdaptor;
import com.android.build.gradle.internal.TaskFactory;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.model.SyncIssueImpl;
import com.android.build.gradle.internal.scope.AndroidTaskRegistry;
import com.android.build.gradle.internal.scope.BaseScope;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.api.transform.QualifiedContent;
import com.android.builder.core.ErrorReporter;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.blame.Message;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.mockito.Mockito;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Base class for Junit-4 based tests that need to manually instantiate tasks to test them.
 *
 * Right now this is limited to using the TransformManager but that could be refactored
 * to allow for other tasks using the AndroidTaskRegistry directly.
 */
public class TaskTestUtils {
    private static final String FOLDER_TEST_PROJECTS = "test-projects";

    protected static final String TASK_NAME = "task name";

    protected TaskFactory taskFactory;
    protected BaseScope scope;
    protected TransformManager transformManager;
    protected FakeErrorReporter errorReporter;

    static class FakeErrorReporter extends ErrorReporter {

        private SyncIssue syncIssue = null;

        protected FakeErrorReporter(@NonNull EvaluationMode mode) {
            super(mode);
        }

        public SyncIssue getSyncIssue() {
            return syncIssue;
        }

        @NonNull
        @Override
        public SyncIssue handleSyncError(@NonNull String data, int type, @NonNull String msg) {
            // always create a sync issue, no matter what the mode is. This can be used to validate
            // what error is thrown anyway.
            syncIssue = new SyncIssueImpl(type, SyncIssue.SEVERITY_ERROR, data, msg);
            return syncIssue;
        }

        @Override
        public void receiveMessage(@NonNull Message message) {
            // do nothing
        }
    }

    @Before
    public void setUp() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(getRootDir(), FOLDER_TEST_PROJECTS + "/basic")).build();

        scope = getScope();
        errorReporter = new FakeErrorReporter(ErrorReporter.EvaluationMode.IDE);
        transformManager = new TransformManager(new AndroidTaskRegistry(), errorReporter);
        taskFactory = new TaskContainerAdaptor(project.getTasks());
    }

    protected StreamTester streamTester() {
        return new StreamTester(null);
    }

    protected StreamTester streamTester(@NonNull final Collection<TransformStream> streams) {
        return new StreamTester(new FilterableStreamCollection() {
            @NonNull
            @Override
            Collection<TransformStream> getStreams() {
                return streams;
            }
        });
    }

    /**
     * Simple class to test that a stream is present in the list of available streams in the
     * transform manager.
     *
     * Right now this expects to find ony a single stream based on the content types and/or scopes
     * provided.
     *
     * Then it optionally test for additional values, if provided.
     */
    protected class StreamTester {
        @NonNull
        private final FilterableStreamCollection streamCollection;
        @NonNull
        private final Set<QualifiedContent.ContentType> contentTypes = Sets.newHashSet();
        private final Set<QualifiedContent.Scope> scopes = Sets.newHashSet();
        private List<Object> dependencies = Lists.newArrayList();

        private List<File> jars = Lists.newArrayList();
        private List<File> folders = Lists.newArrayList();
        private File rootLocation = null;

        private StreamTester(@Nullable FilterableStreamCollection streamCollection) {
            if (streamCollection != null) {
                this.streamCollection = streamCollection;
            } else {
                this.streamCollection = transformManager;
            }
        }

        StreamTester withContentTypes(@NonNull QualifiedContent.ContentType... contentTypes) {
            this.contentTypes.addAll(Arrays.asList(contentTypes));
            return this;
        }

        StreamTester withScopes(@NonNull QualifiedContent.Scope... scopes) {
            this.scopes.addAll(Arrays.asList(scopes));
            return this;
        }

        StreamTester withJar(@NonNull File file) {
            jars.add(file);
            return this;
        }

        StreamTester withJars(@NonNull Collection<File> files) {
            jars.addAll(files);
            return this;
        }

        StreamTester withFolder(@NonNull File file) {
            folders.add(file);
            return this;
        }

        StreamTester withFolders(@NonNull Collection<File> files) {
            folders.addAll(files);
            return this;
        }

        StreamTester withRootLocation(@NonNull File file) {
            rootLocation = file;
            return this;
        }


        StreamTester withDependency(@NonNull Object dependency) {
            this.dependencies.add(dependency);
            return this;
        }

        StreamTester withDependencies(@NonNull Collection<? extends Object> dependencies) {
            this.dependencies.addAll(dependencies);
            return this;
        }

        TransformStream test() {
            if (contentTypes.isEmpty() && scopes.isEmpty()) {
                fail("content-type and scopes empty in StreamTester");
            }

            ImmutableList<TransformStream> streams = streamCollection
                    .getStreams(new TransformManager.StreamFilter() {
                        @Override
                        public boolean accept(@NonNull Set<QualifiedContent.ContentType> types,
                                @NonNull Set<QualifiedContent.Scope> scopes) {
                            return (StreamTester.this.contentTypes.isEmpty() ||
                                    types.equals(contentTypes)) &&
                                    (StreamTester.this.scopes.isEmpty() ||
                                            StreamTester.this.scopes.equals(scopes));
                        }
                    });
            assertThat(streams)
                    .named(String.format("Streams matching requested %s/%s", scopes, contentTypes))
                            .hasSize(1);
            TransformStream stream = Iterables.getOnlyElement(streams);

            assertThat(stream.getContentTypes()).containsExactlyElementsIn(contentTypes);

            if (!dependencies.isEmpty()) {
                assertThat(stream.getDependencies()).containsExactlyElementsIn(dependencies);
            }

            if (!jars.isEmpty()) {
                if (!(stream instanceof OriginalStream)) {
                    throw new RuntimeException("StreamTest.withJar(s) used on Stream that is not an OriginalStream");
                }

                OriginalStream originalStream = (OriginalStream) stream;
                assertThat(originalStream.getJarFiles().get()).containsExactlyElementsIn(jars);
            }

            if (!folders.isEmpty()) {
                if (!(stream instanceof OriginalStream)) {
                    throw new RuntimeException("StreamTest.withFolder(s) used on Stream that is not an OriginalStream");
                }

                OriginalStream originalStream = (OriginalStream) stream;
                assertThat(originalStream.getFolders().get()).containsExactlyElementsIn(folders);
            }

            if (rootLocation != null) {
                if (!(stream instanceof IntermediateStream)) {
                    throw new RuntimeException("StreamTest.withRootLocation used on Stream that is not an IntermediateStream");
                }

                IntermediateStream originalStream = (IntermediateStream) stream;
                assertThat(originalStream.getRootLocation().get()).isEqualTo(rootLocation);
            }

            return stream;
        }
    }

    @NonNull
    private static BaseScope getScope() {
        GradleVariantConfiguration mockConfig = mock(GradleVariantConfiguration.class);
        when(mockConfig.getDirName()).thenReturn("config dir name");

        GlobalScope globalScope = mock(GlobalScope.class);
        when(globalScope.getBuildDir()).thenReturn(new File("build dir"));

        BaseScope scope = mock(BaseScope.class);
        when(scope.getDirName()).thenReturn("config dir name");
        when(scope.getVariantConfiguration()).thenReturn(mockConfig);
        when(scope.getGlobalScope()).thenReturn(globalScope);
        when(scope.getTaskName(Mockito.anyString())).thenReturn(TASK_NAME);
        return scope;
    }

    /**
     * Returns the root dir for the gradle plugin project
     */
    private File getRootDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = new File(location.toURI());
                assertTrue(dir.getPath(), dir.exists());

                File f= dir.getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
                return  new File(
                        f,
                        Joiner.on(File.separator).join(
                                "tools",
                                "base",
                                "build-system",
                                "integration-test"));
            } catch (URISyntaxException e) {
                throw new AssertionError(e);
            }
        }

        fail("Fail to get the tools/build folder");
        return null;
    }
}
