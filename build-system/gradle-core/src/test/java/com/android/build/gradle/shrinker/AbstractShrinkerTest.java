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

package com.android.build.gradle.shrinker;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.shrinker.parser.FilterSpecification;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.sdklib.SdkVersionInfo;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Common code for testing shrinker runs.
 */
public abstract class AbstractShrinkerTest {

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    protected File mTestPackageDir;

    protected File mOutDir;

    protected Collection<TransformInput> mInputs;

    protected File mIncrementalDir;

    protected TransformOutputProvider mOutput;

    protected DirectoryInput mDirectoryInput;

    protected ShrinkerLogger mShrinkerLogger =
            new ShrinkerLogger(
                    Collections.<FilterSpecification>emptyList(),
                    LoggerFactory.getLogger(getClass()));

    protected int mExpectedWarnings;

    protected FullRunShrinker<String> mFullRunShrinker;

    @Before
    public void setUp() throws Exception {
        mTestPackageDir = tmpDir.newFolder("app-classes", "test");
        File classDir = new File(tmpDir.getRoot(), "app-classes");
        mOutDir = tmpDir.newFolder("out");
        mIncrementalDir = tmpDir.newFolder("incremental");

        mDirectoryInput = mock(DirectoryInput.class);
        when(mDirectoryInput.getFile()).thenReturn(classDir);
        TransformInput transformInput = mock(TransformInput.class);
        when(transformInput.getDirectoryInputs()).thenReturn(ImmutableList.of(mDirectoryInput));
        mOutput = mock(TransformOutputProvider.class);
        // we probably want a better mock that than so that we can return different dir depending
        // on inputs.
        when(mOutput.getContentLocation(
                Mockito.anyString(),
                Mockito.anySetOf(ContentType.class),
                Mockito.anySetOf(Scope.class),
                Mockito.any(Format.class))).thenReturn(mOutDir);

        mInputs = ImmutableList.of(transformInput);

        mFullRunShrinker = new FullRunShrinker<>(
                WaitableExecutor.useGlobalSharedThreadPool(),
                JavaSerializationShrinkerGraph.empty(mIncrementalDir),
                getPlatformJars(),
                mShrinkerLogger);
    }

    @Before
    public void resetWarningsCounter() throws Exception {
        mExpectedWarnings = 0;
    }

    @After
    public void checkLogger() throws Exception {
        assertEquals(mExpectedWarnings, mShrinkerLogger.getWarningsCount());
    }

    protected void assertClassSkipped(String className) {
        assertFalse(
                "Class " + className + " exists in output.",
                getOutputClassFile(className).exists());
    }

    protected void assertImplements(String className, String interfaceName) throws IOException {
        File classFile = getOutputClassFile(className);
        assertThat(getInterfaceNames(classFile)).contains(interfaceName);
    }

    protected void assertDoesntImplement(String className, String interfaceName) throws IOException {
        File classFile = getOutputClassFile(className);
        assertThat(getInterfaceNames(classFile)).doesNotContain(interfaceName);
    }

    protected static Set<String> getInterfaceNames(File classFile) throws IOException {
        ClassNode classNode = getClassNode(classFile);

        if (classNode.interfaces == null) {
            return ImmutableSet.of();
        } else {
            //noinspection unchecked
            return ImmutableSet.copyOf(classNode.interfaces);
        }
    }

    private static List<InnerClassNode> getInnerClasses(File classFile) throws IOException {
        ClassNode classNode = getClassNode(classFile);
        if (classNode.innerClasses == null) {
            return ImmutableList.of();
        } else {
            //noinspection unchecked
            return classNode.innerClasses;
        }
    }

    protected void assertSingleInnerClassesEntry(String className, String outer, String inner)
            throws IOException {
        List<InnerClassNode> innerClasses = getInnerClasses(getOutputClassFile(className));
        assertThat(innerClasses).hasSize(1);
        assertThat(innerClasses.get(0).outerName).isEqualTo(outer);
        assertThat(innerClasses.get(0).name).isEqualTo(inner);
    }

    protected void assertNoInnerClasses(String className) throws IOException {
        List<InnerClassNode> innerClasses = getInnerClasses(getOutputClassFile(className));
        assertThat(innerClasses).isEmpty();
    }

    protected void assertMembersLeft(String className, String... members) throws IOException {
        File outFile = getOutputClassFile(className);

        assertTrue(
                String.format("Class %s does not exist in output.", className),
                outFile.exists());

        assertThat(getMembers(outFile)).containsExactlyElementsIn(Arrays.asList(members));
    }

    @NonNull
    protected static Set<File> getPlatformJars() {
        String androidHomePath = System.getenv(SdkConstants.ANDROID_HOME_ENV);

        assertThat(androidHomePath).named("$ANDROID_HOME env variable").isNotNull();

        File androidHome = new File(androidHomePath);
        File androidJar = FileUtils.join(
                androidHome,
                SdkConstants.FD_PLATFORMS,
                "android-" + SdkVersionInfo.HIGHEST_KNOWN_STABLE_API,
                "android.jar");

        assertTrue(
                androidJar.getAbsolutePath() + " does not exist.",
                androidJar.exists());

        return ImmutableSet.of(androidJar);
    }

    private static Set<String> getMembers(File classFile) throws IOException {
        ClassNode classNode = getClassNode(classFile);

        //noinspection unchecked - ASM API
        return Stream.concat(
                ((List<MethodNode>) classNode.methods).stream()
                        .map(methodNode -> methodNode.name + ":" + methodNode.desc),
                ((List<FieldNode>) classNode.fields).stream()
                        .map(fieldNode -> fieldNode.name + ":" + fieldNode.desc))
                .collect(Collectors.toSet());
    }

    @NonNull
    private static ClassNode getClassNode(File classFile) throws IOException {
        ClassReader classReader = new ClassReader(Files.toByteArray(classFile));
        ClassNode classNode = new ClassNode(Opcodes.ASM5);
        classReader.accept(
                classNode,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return classNode;
    }

    @NonNull
    protected File getOutputClassFile(String className) {
        return FileUtils.join(mOutDir, "test", className + ".class");
    }

    @NonNull
    protected KeepRules parseKeepRules(String rules) throws IOException {
        ProguardConfig config = new ProguardConfig();
        config.parse(rules);
        return new ProguardFlagsKeepRules(config.getFlags(), mShrinkerLogger);
    }

    protected void run(KeepRules keepRules) throws IOException {
        mFullRunShrinker.run(
                mInputs,
                Collections.<TransformInput>emptyList(),
                mOutput,
                ImmutableMap.of(
                        AbstractShrinker.CounterSet.SHRINK, keepRules),
                false);
    }
}
