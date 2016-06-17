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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.DexFileSubject;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.ide.common.process.ProcessException;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.google.common.io.ByteStreams;
import com.google.common.truth.Expect;
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest;
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest.ApkManifest;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static com.google.common.truth.Truth.assertThat;

import javax.xml.parsers.ParserConfigurationException;

import groovy.util.FileNameFinder;

/**
 * Integration test for the ExternalBuildPlugin.
 */
public class ExternalBuildPluginTest {

    private File manifestFile;

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @ClassRule
    public static GradleTestProject sProject = GradleTestProject.builder()
            .fromTestProject("externalBuildPlugin")
            .create();

    @Before
    public void setUp() throws IOException {

        IAndroidTarget target = SdkHelper.getTarget(23);
        assertThat(target).isNotNull();

        ApkManifest.Builder apkManifest =
                ApkManifest.newBuilder()
                        .setAndroidSdk(ExternalBuildApkManifest.AndroidSdk.newBuilder()
                                .setAndroidJar(
                                    target.getFile(IAndroidTarget.ANDROID_JAR).getAbsolutePath())
                                // TODO: Start putting dx.jar in the proto
                                .setDx(SdkHelper.getDxJar().getAbsolutePath())
                                .setAapt(target.getBuildToolInfo().getPath(
                                        BuildToolInfo.PathId.AAPT)))
                        .setResourceApk(ExternalBuildApkManifest.Artifact.newBuilder()
                                .setExecRootPath("resources.ap_"))
                        .setAndroidManifest(ExternalBuildApkManifest.Artifact.newBuilder()
                                .setExecRootPath("AndroidManifest.xml"));

        List<String> jarFiles = new FileNameFinder().getFileNames(
                sProject.getTestDir().getAbsolutePath(), "**/*.jar");
        Path projectPath = sProject.getTestDir().toPath();
        for (String jarFile : jarFiles) {
            Path jarFilePath = new File(jarFile).toPath();
            Path relativePath = projectPath.relativize(jarFilePath);
            apkManifest.addJars(ExternalBuildApkManifest.Artifact.newBuilder()
                    .setExecRootPath(relativePath.toString())
                    .setHash(ByteString.copyFromUtf8(String.valueOf(jarFile.hashCode()))));
        }

        manifestFile = sProject.file("apk_manifest.tmp");
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(manifestFile))) {
            CodedOutputStream cos =
                    CodedOutputStream.newInstance(os);
            apkManifest.build().writeTo(cos);
            cos.flush();
        }
    }

    @Test
    public void testBuild()
            throws ProcessException, IOException, ParserConfigurationException, SAXException {
        FileUtils.write(sProject.getBuildFile(), ""
+ "apply from: \"../commonHeader.gradle\"\n"
+ "buildscript {\n "
+ "  apply from: \"../commonBuildScript.gradle\"\n"
+ "}\n"
+ "\n"
+ "apply plugin: 'base'\n"
+ "apply plugin: 'com.android.external.build'\n"
+ "\n"
+ "externalBuild {\n"
+ "  executionRoot = $/" + sProject.getTestDir().getAbsolutePath() +"/$\n"
+ "  buildManifestPath = $/" + manifestFile.getAbsolutePath() + "/$\n"
+ "}\n");

        sProject.executor()
            .withInstantRun(23, ColdswapMode.AUTO)
            .run("clean", "process");

        InstantRunBuildContext instantRunBuildContext = loadFromBuildInfo();
        assertThat(instantRunBuildContext.getPreviousBuilds()).hasSize(1);
        assertThat(instantRunBuildContext.getLastBuild()).isNotNull();
        assertThat(instantRunBuildContext.getLastBuild().getArtifacts()).hasSize(1);
        InstantRunBuildContext.Build fullBuild  = instantRunBuildContext.getLastBuild();
        assertThat(fullBuild.getVerifierStatus().get()).isEqualTo(InstantRunVerifierStatus.INITIAL_BUILD);
        assertThat(fullBuild.getArtifacts()).hasSize(1);
        InstantRunBuildContext.Artifact artifact = fullBuild.getArtifacts().get(0);
        assertThat(artifact.getType()).isEqualTo(InstantRunBuildContext.FileType.MAIN);
        assertThat(artifact.getLocation().exists()).isTrue();

        ApkSubject apkSubject = expect.about(ApkSubject.FACTORY).that(artifact.getLocation());
        apkSubject.contains("instant-run.zip");
        assertThat(apkSubject.hasMainDexFile());

        // now perform a hot swap test.
        File mainClasses = new File(sProject.getTestDir(), "jars/main/classes.jar");
        assertThat(mainClasses.exists()).isTrue();

        File originalFile = new File(mainClasses.getParentFile(), "original_classes.jar");
        assertThat(mainClasses.renameTo(originalFile)).isTrue();

        try (JarFile inputJar = new JarFile(originalFile);
             JarOutputStream jarOutputFile = new JarOutputStream(new BufferedOutputStream(
                new FileOutputStream(new File(mainClasses.getParentFile(), "classes.jar"))))) {
            Enumeration<JarEntry> entries = inputJar.entries();
            while (entries.hasMoreElements()) {
                JarEntry element = entries.nextElement();
                try (InputStream inputStream = new BufferedInputStream(
                        inputJar.getInputStream(element))) {
                    if (!element.isDirectory()) {
                        jarOutputFile.putNextEntry(new ZipEntry(element.getName()));
                        try {
                            if (element.getName().contains("MainActivity.class")) {
                                // perform hot swap change
                                byte[] classBytes = new byte[(int) element.getSize()];
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
                }
            }
        }

        sProject.executor()
                .withInstantRun(23, ColdswapMode.AUTO)
                .run("process");

        instantRunBuildContext = loadFromBuildInfo();
        assertThat(instantRunBuildContext.getPreviousBuilds()).hasSize(2);
        InstantRunBuildContext.Build lastBuild = instantRunBuildContext.getLastBuild();
        assertThat(lastBuild).isNotNull();
        assertThat(lastBuild.getVerifierStatus().isPresent());
        assertThat(lastBuild.getVerifierStatus().get()).isEqualTo(InstantRunVerifierStatus.COMPATIBLE);
        assertThat(lastBuild.getArtifacts()).hasSize(1);
        artifact = lastBuild.getArtifacts().get(0);
        assertThat(artifact.getType()).isEqualTo(InstantRunBuildContext.FileType.RELOAD_DEX);
        assertThat(artifact.getLocation()).isNotNull();
        File dexFile = artifact.getLocation();
        assertThat(dexFile.exists()).isTrue();
        DexFileSubject reloadDex = expect.about(DexFileSubject.FACTORY).that(dexFile);
        reloadDex.hasClass("Lcom/android/tools/fd/runtime/AppPatchesLoaderImpl;").that();
        reloadDex.hasClass("Lcom/example/jedo/blazeapp/MainActivity$override;").that();
    }

    private static byte[] hotswapChange(byte[] inputClass) {
        ClassReader cr = new ClassReader(inputClass);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                    String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM5, mv) {
                    @Override
                    public void visitCode() {
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
                        super.visitCode();
                    }
                };
            }
        };
        cr.accept(cv, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

    private static InstantRunBuildContext loadFromBuildInfo()
            throws ParserConfigurationException, SAXException, IOException {
        // assert build-info.xml presence.
        File buildInfo = new File(sProject.getTestDir(), "build/reload-dex/debug/build-info.xml");
        assertThat(buildInfo.exists()).isTrue();
        InstantRunBuildContext instantRunBuildContext = new InstantRunBuildContext();
        instantRunBuildContext.setApiLevel(23, ColdswapMode.AUTO.toString(), "arm");
        instantRunBuildContext.loadFromXmlFile(buildInfo);
        return instantRunBuildContext;
    }
}



