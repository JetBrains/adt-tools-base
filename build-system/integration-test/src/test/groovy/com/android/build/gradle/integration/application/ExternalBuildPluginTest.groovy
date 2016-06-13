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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.truth.DexFileSubject
import com.android.build.gradle.integration.common.utils.SdkHelper
import com.android.build.gradle.internal.incremental.ColdswapMode
import com.android.build.gradle.internal.incremental.InstantRunBuildContext
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.IAndroidTarget
import com.google.common.io.ByteStreams
import com.google.common.io.Closer
import com.google.common.truth.Expect
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest.ApkManifest
import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

import static com.google.common.truth.Truth.assertThat
/**
 * Integration test for the ExternalBuildPlugin.
 */
public class ExternalBuildPluginTest {

    File manifestFile;

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("externalBuildPlugin")
            .create()

    @Before
    public void setUp() {

        IAndroidTarget target = SdkHelper.getTarget(23);
        assertThat(target).isNotNull();


        def resourceFile = project.file("resources.ap_")
        def manifest = project.file("AndroidManifest.xml")

        ApkManifest.Builder apkManifest =
                ApkManifest.newBuilder()
                        .setAndroidSdk(ExternalBuildApkManifest.AndroidSdk.newBuilder()
                                .setAndroidJar(
                                    target.getFile(IAndroidTarget.ANDROID_JAR).absolutePath)
                                // TODO: Start putting dx.jar in the proto
                                .setDx(SdkHelper.getDxJar().absolutePath)
                                .setAapt(target.getBuildToolInfo().getPath(
                                        BuildToolInfo.PathId.AAPT)))
                        .setResourceApk(ExternalBuildApkManifest.Artifact.newBuilder()
                                .setExecRootPath(resourceFile.absolutePath))
                        .setAndroidManifest(ExternalBuildApkManifest.Artifact.newBuilder()
                                .setExecRootPath(manifest.absolutePath))

        List<String> jarFiles = new FileNameFinder().getFileNames(
                project.getTestDir().getAbsolutePath(), "**/*.jar");
        for (String jarFile : jarFiles) {
            apkManifest.addJars(ExternalBuildApkManifest.Artifact.newBuilder()
                    .setExecRootPath(jarFile)
                    .setHash(ByteString.copyFromUtf8(String.valueOf(jarFile.hashCode()))))
        }

        manifestFile = project.file("apk_manifest.tmp")
        OutputStream os = new BufferedOutputStream(new FileOutputStream(manifestFile));
        try {
            CodedOutputStream cos =
                    CodedOutputStream.newInstance(os);
            apkManifest.build().writeTo(cos);
            cos.flush();
        } finally {
            os.close();
        }
    }

    @Test
    public void testBuild() {
        project.getBuildFile() << """
apply from: "../commonHeader.gradle"
buildscript { apply from: "../commonBuildScript.gradle" }

apply plugin: 'base'
apply plugin: 'com.android.external.build'

externalBuild {
  buildManifestPath = \$/""" + manifestFile.getAbsolutePath() + """/\$
}
"""
        project.executor()
            .withInstantRun(23, ColdswapMode.AUTO)
            .run("clean", "process")

        InstantRunBuildContext instantRunBuildContext = loadFromBuildInfo();
        assertThat(instantRunBuildContext.getPreviousBuilds()).hasSize(1);
        assertThat(instantRunBuildContext.getLastBuild().artifacts).hasSize(1);
        InstantRunBuildContext.Build fullBuild  = instantRunBuildContext.getLastBuild();
        assertThat(fullBuild.verifierStatus.get()).isEqualTo(InstantRunVerifierStatus.INITIAL_BUILD);
        assertThat(fullBuild.artifacts).hasSize(1);
        InstantRunBuildContext.Artifact artifact = fullBuild.artifacts.get(0);
        assertThat(artifact.type).is(InstantRunBuildContext.FileType.MAIN);
        assertThat(artifact.getLocation().exists()).isTrue();

        ApkSubject apkSubject = expect.about(ApkSubject.FACTORY).that(artifact.getLocation());
        assertThat(apkSubject.contains("instant-run.zip"));
        assertThat(apkSubject.hasMainDexFile());

        // now perform a hot swap test.
        File mainClasses = new File(project.getTestDir(), "jars/main/classes.jar");
        assertThat(mainClasses.exists()).isTrue();

        File originalFile = new File(mainClasses.getParentFile(), "original_classes.jar");
        mainClasses.renameTo(originalFile);

        Closer closer = Closer.create();
        JarOutputStream jarOutputFile = new JarOutputStream(new BufferedOutputStream(
                new FileOutputStream(new File(mainClasses.getParentFile(), "classes.jar"))));
        closer.register(jarOutputFile);
        try {
            JarFile inputJar = new JarFile(originalFile);
            closer.register(inputJar);
            Enumeration<JarEntry> entries = inputJar.entries();
            while (entries.hasMoreElements()) {
                JarEntry element = entries.nextElement();
                InputStream inputStream = new BufferedInputStream(
                        inputJar.getInputStream(element));
                try {
                    if (!element.isDirectory()) {
                        jarOutputFile.putNextEntry(new ZipEntry(element.getName()))
                        try {
                            if (element.getName().contains("MainActivity.class")) {
                                // perform hot swap change
                                byte[] classBytes = new byte[element.getSize()];
                                ByteStreams.readFully(inputStream, classBytes);
                                classBytes = hotswapChange(classBytes);
                                jarOutputFile.write(classBytes);
                            } else {
                                ByteStreams.copy(inputStream, jarOutputFile);
                            }
                        } finally {
                            jarOutputFile.closeEntry();
                        }
                    }
                } finally {
                    inputStream.close();
                }
            }
        } finally {
            closer.close();
        }

        project.executor()
                .withInstantRun(23, ColdswapMode.AUTO)
                .run("process");

        instantRunBuildContext = loadFromBuildInfo();
        assertThat(instantRunBuildContext.getPreviousBuilds()).hasSize(2);
        InstantRunBuildContext.Build lastBuild = instantRunBuildContext.getLastBuild();
        assertThat(lastBuild.getVerifierStatus()).is(InstantRunVerifierStatus.COMPATIBLE);
        assertThat(lastBuild.artifacts).hasSize(1);
        artifact = lastBuild.artifacts.get(0);
        assertThat(artifact.getType()).is(InstantRunBuildContext.FileType.RELOAD_DEX);
        assertThat(artifact.getLocation()).isNotNull();
        File dexFile = artifact.getLocation();
        assertThat(dexFile.exists()).isTrue();
        DexFileSubject reloadDex = expect.about(DexFileSubject.FACTORY).that(dexFile);
        reloadDex.hasClass("Lcom/android/tools/fd/runtime/AppPatchesLoaderImpl;").that();
        reloadDex.hasClass("Lcom/example/jedo/blazeapp/MainActivity\$override;").that();
    }

    private byte[] hotswapChange(byte[] inputClass) {
        ClassReader cr = new ClassReader(inputClass);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            MethodVisitor visitMethod(int access, String name, String desc, String signature,
                    String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM5, mv) {
                    @Override
                    void visitCode() {
                        // add a useless logging to the method.
                        mv.visitFieldInsn(Opcodes.GETSTATIC,
                                "java/lang/System",
                                "out",
                                "Ljava/io/PrintStream;");
                        mv.visitLdcInsn("test changed !");
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                                "java/io/PrintStream",
                                "println",
                                "(Ljava/lang/String;)V",
                                false);
                        super.visitCode()
                    }
                }
            }
        };
        cr.accept(cv, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

    private static InstantRunBuildContext loadFromBuildInfo() {
        // assert build-info.xml presence.
        File buildInfo = new File(project.getTestDir(), "build/reload-dex/debug/build-info.xml");
        assertThat(buildInfo.exists()).isTrue();
        InstantRunBuildContext instantRunBuildContext = new InstantRunBuildContext();
        instantRunBuildContext.setApiLevel(23, ColdswapMode.AUTO.toString(), "arm");
        instantRunBuildContext.loadFromXmlFile(buildInfo);
        return instantRunBuildContext;
    }
}


