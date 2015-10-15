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

package com.android.builder.shrinker;

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
import com.android.sdklib.SdkVersionInfo;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
    }

    protected void assertClassSkipped(String className) {
        assertFalse(
                "Class " + className + " exists in output.",
                getOutputClassFile(className).exists());
    }

    protected void assertMembersLeft(String className, String... members) throws IOException {
        File outFile = getOutputClassFile(className);

        assertTrue(
                String.format("Class %s does not exist in output.", className),
                outFile.exists());

        assertEquals(
                "Members in class " + className + " not correct.",
                Sets.newHashSet(members), getMembers(outFile));
    }

    @NonNull
    protected static Set<File> getPlatformJars() {
        File androidHome = new File(System.getenv(SdkConstants.ANDROID_HOME_ENV));
        File androidJar = FileUtils.join(
                androidHome,
                SdkConstants.FD_PLATFORMS,
                "android-" + SdkVersionInfo.HIGHEST_KNOWN_STABLE_API,
                "android.jar");
        assertTrue(androidJar.getAbsolutePath(), androidJar.exists());

        return ImmutableSet.of(androidJar);
    }

    private static Set<String> getMembers(File classFile) throws IOException {
        ClassReader classReader = new ClassReader(Files.toByteArray(classFile));
        ClassNode classNode = new ClassNode(Opcodes.ASM5);
        classReader.accept(
                classNode,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        Set<String> members = Sets.newHashSet();
        //noinspection unchecked - ASM API
        for (MethodNode methodNode : (List<MethodNode>) classNode.methods) {
            members.add(methodNode.name + ":" + methodNode.desc);
        }

        //noinspection unchecked - ASM API
        for (FieldNode fieldNode : (List<FieldNode>) classNode.fields) {
            members.add(fieldNode.name + ":" + fieldNode.desc);
        }

        return members;
    }

    @NonNull
    protected File getOutputClassFile(String className) {
        return FileUtils.join(mOutDir, "test", className + ".class");
    }
}
