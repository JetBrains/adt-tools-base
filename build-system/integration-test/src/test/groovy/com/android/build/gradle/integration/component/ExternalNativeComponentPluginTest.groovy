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

package com.android.build.gradle.integration.component

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeArtifact
import com.android.builder.model.NativeSettings
import com.android.builder.model.NativeToolchain
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Test the ExternalNativeComponentModelPlugin.
 */
class ExternalNativeComponentPluginTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp())
            .forExperimentalPlugin(true)
            .create();

    @Test
    public void "check configurations using JSON data file"() {
        project.getBuildFile() << """
apply plugin: 'com.android.model.external'

model {
    nativeBuild {
        configFile = file("config.json")
    }
}
"""
        project.file("config.json") << """
{
    "buildFiles" : ["CMakeLists.txt"],
    "libraries" : {
        "foo" : {
            "executable" : "touch",
            "args" : ["output.txt"],
            "toolchain" : "toolchain1",
            "output" : "build/libfoo.so",
            "folders" : [
                {
                    src : "src/main/jni",
                    cFlags : ["folderCFlag1", "folderCFlag2"],
                    cppFlags : ["folderCppFlag1", "folderCppFlag2"]
                }
            ],
            "files" : [
                {
                    "src" : "src/main/jni/hello.c",
                    "flags" : ["fileFlag1", "fileFlag2"]
                }
            ]
        }
    },
    "toolchains" : {
        "toolchain1" : {
            "cCompilerExecutable" : "clang",
            "cppCompilerExecutable" : "clang++"
        }
    }
}
"""
        NativeAndroidProject model = project.executeAndReturnModel(NativeAndroidProject.class, "assemble")
        checkModel(model);
    }

    @Test
    public void "check configurations using plugin DSL"() {
        project.getBuildFile() << """
apply plugin: 'com.android.model.external'

model {
    nativeBuild {
        buildFiles.addAll([file("CMakeLists.txt")])
    }
    nativeBuild.libraries {
        create("foo") {
            executable = "touch"
            args.addAll(["output.txt"])
            toolchain = "toolchain1"
            output = file("build/libfoo.so")
            folders.with {
                create() {
                    src = file("src/main/jni")
                    CFlags.addAll(["folderCFlag1", "folderCFlag2"])
                    cppFlags.addAll(["folderCppFlag1", "folderCppFlag2"])
                }
            }
            files.with {
                create() {
                    src = file("src/main/jni/hello.c")
                    flags.addAll(["fileFlag1", "fileFlag2"])
                }
            }

        }
    }
    nativeBuild.toolchains {
        create("toolchain1") {
            CCompilerExecutable = file("clang")
            cppCompilerExecutable = file("clang++")

        }
    }
}
"""
        NativeAndroidProject model = project.executeAndReturnModel(NativeAndroidProject.class, "assemble")
        checkModel(model);
    }

    private void checkModel(NativeAndroidProject model) {
        Collection<NativeSettings> settingsMap = model.getSettings();
        assertThat(project.file("output.txt")).exists()

        assertThat(model.artifacts).hasSize(1)

        NativeArtifact artifact = model.artifacts.first()
        assertThat(artifact.getToolChain()).isEqualTo("toolchain1")
        assertThat(artifact.sourceFolders).hasSize(1)
        assertThat(artifact.sourceFolders.first().folderPath.toString()).endsWith("src/main/jni")
        NativeSettings setting = settingsMap.find { it.getName() == artifact.sourceFolders.first().perLanguageSettings.get("c") }
        assertThat(setting.getCompilerFlags()).containsAllOf("folderCFlag1", "folderCFlag2");
        setting = settingsMap.find { it.getName() == artifact.sourceFolders.first().perLanguageSettings.get("c++") }
        assertThat(setting.getCompilerFlags()).containsAllOf("folderCppFlag1", "folderCppFlag2");

        assertThat(artifact.sourceFiles).hasSize(1)
        assertThat(artifact.sourceFiles.first().filePath.toString()).endsWith("src/main/jni/hello.c")
        setting = settingsMap.find { it.getName() == artifact.sourceFiles.first().settingsName }
        assertThat(setting.getCompilerFlags()).containsAllOf("fileFlag1", "fileFlag2");

        assertThat(model.getToolChains()).hasSize(1)
        NativeToolchain toolchain = model.getToolChains().first()
        assertThat(toolchain.getName()).isEqualTo("toolchain1")
        assertThat(toolchain.getCCompilerExecutable().getName()).isEqualTo("clang")
        assertThat(toolchain.getCppCompilerExecutable().getName()).isEqualTo("clang++")
    }
}
