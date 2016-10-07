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
import com.android.builder.model.NativeFile
import com.android.builder.model.NativeFolder
import com.android.builder.model.NativeSettings
import com.android.builder.model.NativeToolchain
import groovy.transform.CompileStatic
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Test the ExternalNativeComponentModelPlugin.
 */
@CompileStatic
class ExternalNativeComponentPluginTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp())
            .useExperimentalGradleVersion(true)
            .create();

    @Test
    public void "check configurations using JSON data file"() {
        project.getBuildFile() << """
apply plugin: 'com.android.model.external'

model {
    nativeBuild {
        create {
            configs.add(file("config.json"))
        }
    }
}
"""
        project.file("config.json") << """
{
    "cleanCommands" : ["rm output.txt"],
    "buildFiles" : ["CMakeLists.txt"],
    "libraries" : {
        "foo" : {
            "buildCommand" : "touch output.txt",
            "artifactName" : "output",
            "toolchain" : "toolchain1",
            "output" : "build/libfoo.so",
            "abi" : "x86",
            "folders" : [
                {
                    "src" : "src/main/jni",
                    "cFlags" : "folderCFlag1 folderCFlag2",
                    "cppFlags" : "folderCppFlag1 folderCppFlag2",
                    "workingDirectory" : "workingDir"
                }
            ],
            "files" : [
                {
                    "src" : "src/main/jni/hello.c",
                    "flags" : "fileFlag1 fileFlag2",
                    "workingDirectory" : "workingDir"
                }
            ]
        }
    },
    "toolchains" : {
        "toolchain1" : {
            "cCompilerExecutable" : "clang",
            "cppCompilerExecutable" : "clang++"
        }
    },
    "cFileExtensions" : ["c"],
    "cppFileExtensions" : ["cpp"]
}
"""
        NativeAndroidProject model = project.executeAndReturnModel(NativeAndroidProject.class, "assemble")
        checkModel(model);
        project.execute("clean")
        assertThat(project.file("output.txt")).doesNotExist()
    }

    @Test
    public void "check configurations with multiple JSON data file"() {
        project.getBuildFile() << """
apply plugin: 'com.android.model.external'

model {
    nativeBuild {
        create {
            configs.addAll([
                file("config1.json"),
                file("config2.json")
            ])
            command "./generate_configs.sh"
        }
        create {
            configs.addAll([
                file("config3.json"),
                file("config4.json")
            ])
        }
    }
}
"""
        project.file("generate_configs.sh") << """
echo '
{
    "buildFiles" : ["CMakeLists.txt"],
    "libraries" : {
        "foo-DEBUG" : {
            "buildCommand" : "touch foo.txt",
            "buildType" : "debug",
            "artifactName" : "foo",
            "abi" : "x86",
            "toolchain" : "toolchain1",
            "output" : "build/debug/libfoo.so"
        }
    },
    "toolchains" : {
        "toolchain1" : {
            "cCompilerExecutable" : "clang",
            "cppCompilerExecutable" : "clang++"
        }
    }
}' > config1.json
echo '
{
    "buildFiles" : ["CMakeLists.txt"],
    "libraries" : {
        "foo-RELEASE" : {
            "buildCommand" : "touch foo.txt",
            "buildType" : "release",
            "artifactName" : "foo",
            "abi" : "x86",
            "toolchain" : "toolchain1",
            "output" : "build/release/libfoo.so"
        }
    },
    "toolchains" : {
        "toolchain1" : {
            "cCompilerExecutable" : "clang",
            "cppCompilerExecutable" : "clang++"
        }
    }
}' > config2.json
"""
        project.file("generate_configs.sh").setExecutable(true)

        project.file("config3.json") << """
{
    "buildFiles" : ["CMakeLists.txt"],
    "libraries" : {
        "bar-DEBUG" : {
            "buildCommand" : "touch bar.txt",
            "buildType" : "debug",
            "artifactName" : "bar",
            "abi" : "x86",
            "toolchain" : "toolchain2",
            "output" : "build/debug/libbar.so"
        }
    },
    "toolchains" : {
        "toolchain2" : {
            "cCompilerExecutable" : "gcc",
            "cppCompilerExecutable" : "g++"
        }
    }
}
"""
        project.file("config4.json") << """
{
    "buildFiles" : ["CMakeLists.txt"],
    "libraries" : {
        "bar-RELEASE" : {
            "buildCommand" : "touch bar.txt",
            "buildType" : "release",
            "artifactName" : "bar",
            "abi" : "x86",
            "toolchain" : "toolchain2",
            "output" : "build/release/libbar.so"
        }
    },
    "toolchains" : {
        "toolchain2" : {
            "cCompilerExecutable" : "gcc",
            "cppCompilerExecutable" : "g++"
        }
    }
}
"""
        assertThat(project.file("config1.json")).doesNotExist()
        assertThat(project.file("config2.json")).doesNotExist()
        project.execute("generateConfigFiles")
        assertThat(project.file("config1.json")).exists()
        assertThat(project.file("config2.json")).exists()

        NativeAndroidProject model = project.executeAndReturnModel(NativeAndroidProject.class, "assemble")
        assertThat(model.getFileExtensions()).containsEntry("c", "c")
        assertThat(model.getFileExtensions()).containsEntry("C", "c++")
        assertThat(model.getFileExtensions()).containsEntry("CPP", "c++")
        assertThat(model.getFileExtensions()).containsEntry("c++", "c++")
        assertThat(model.getFileExtensions()).containsEntry("cc", "c++")
        assertThat(model.getFileExtensions()).containsEntry("cp", "c++")
        assertThat(model.getFileExtensions()).containsEntry("cpp", "c++")
        assertThat(model.getFileExtensions()).containsEntry("cxx", "c++")

        assertThat(model.artifacts).hasSize(4)
        for (NativeArtifact artifact : model.artifacts) {
            if (artifact.getName().startsWith("foo")) {
                if (artifact.getName().endsWith("DEBUG")) {
                    assertThat(artifact.getName()).isEqualTo("foo-DEBUG")
                    assertThat(artifact.getAssembleTaskName()).isEqualTo("createFoo-DEBUG")
                } else {
                    assertThat(artifact.getName()).isEqualTo("foo-RELEASE")
                    assertThat(artifact.getAssembleTaskName()).isEqualTo("createFoo-RELEASE")
                }
                assertThat(artifact.getToolChain()).isEqualTo("toolchain1")
                assertThat(artifact.getOutputFile()).hasName("libfoo.so")
            } else {
                if (artifact.getName().endsWith("DEBUG")) {
                    assertThat(artifact.getName()).isEqualTo("bar-DEBUG")
                    assertThat(artifact.getAssembleTaskName()).isEqualTo("createBar-DEBUG")
                } else {
                    assertThat(artifact.getName()).isEqualTo("bar-RELEASE")
                    assertThat(artifact.getAssembleTaskName()).isEqualTo("createBar-RELEASE")
                }
                assertThat(artifact.getToolChain()).isEqualTo("toolchain2")
                assertThat(artifact.getOutputFile()).hasName("libbar.so")
            }
        }

        assertThat(model.getToolChains()).hasSize(2)
        for (NativeToolchain toolchain : model.getToolChains()) {
            if (toolchain.getName().equals("toolchain1")) {

                assertThat(toolchain.getName()).isEqualTo("toolchain1")
                assertThat(toolchain.getCCompilerExecutable().getName()).isEqualTo("clang")
                assertThat(toolchain.getCppCompilerExecutable().getName()).isEqualTo("clang++")
            } else {
                assertThat(toolchain.getName()).isEqualTo("toolchain2")
                assertThat(toolchain.getCCompilerExecutable().getName()).isEqualTo("gcc")
                assertThat(toolchain.getCppCompilerExecutable().getName()).isEqualTo("g++")
            }
        }
    }

    @Test
    public void "check configurations using plugin DSL"() {
        project.getBuildFile() << """
apply plugin: 'com.android.model.external'

model {
    nativeBuildConfig {
        cleanCommands.add("rm output.txt")
        buildFiles.addAll([file("CMakeLists.txt")])
        cFileExtensions.add("c")
        cppFileExtensions.add("cpp")

        libraries {
            create("foo") {
                buildCommand "touch output.txt"
                abi "x86"
                artifactName "output"
                toolchain "toolchain1"
                output file("build/libfoo.so")
                folders {
                    create() {
                        src "src/main/jni"
                        cFlags "folderCFlag1 folderCFlag2"
                        cppFlags "folderCppFlag1 folderCppFlag2"
                        workingDirectory "workingDir"
                    }
                }
                files {
                    create() {
                        src "src/main/jni/hello.c"
                        flags "fileFlag1 fileFlag2"
                        workingDirectory "workingDir"
                    }
                }

            }
        }
        toolchains {
            create("toolchain1") {
                // Needs to be CCompilerExecutable instead of the more correct cCompilerExecutable because,
                // of a stupid bug with Gradle.
                CCompilerExecutable = "clang"
                cppCompilerExecutable "clang++"

            }
        }
    }
}
"""
        NativeAndroidProject model = project.executeAndReturnModel(NativeAndroidProject.class, "assemble")
        checkModel(model);
        project.execute("clean")
        assertThat(project.file("output.txt")).doesNotExist()
    }

    private void checkModel(NativeAndroidProject model) {
        Collection<NativeSettings> settingsMap = model.getSettings();
        assertThat(project.file("output.txt")).exists()

        assertThat(model.artifacts).hasSize(1)

        assertThat(model.getFileExtensions()).containsEntry("c", "c")
        assertThat(model.getFileExtensions()).containsEntry("cpp", "c++")

        NativeArtifact artifact = model.artifacts.first()
        assertThat(artifact.getToolChain()).isEqualTo("toolchain1")

        // Source Folders
        assertThat(artifact.sourceFolders).hasSize(1)
        NativeFolder folder = artifact.sourceFolders.first()
        assertThat(folder.folderPath).isEqualTo(project.file("src/main/jni"))
        assertThat(folder.getWorkingDirectory()).isEqualTo(project.file("workingDir"))
        NativeSettings setting = settingsMap.find { it.getName() == folder.perLanguageSettings.get("c") }
        assertThat(setting.getCompilerFlags()).containsAllOf("folderCFlag1", "folderCFlag2");
        setting = settingsMap.find { it.getName() == artifact.sourceFolders.first().perLanguageSettings.get("c++") }
        assertThat(setting.getCompilerFlags()).containsAllOf("folderCppFlag1", "folderCppFlag2");

        assertThat(artifact.sourceFiles).hasSize(1)
        NativeFile file = artifact.sourceFiles.first()
        assertThat(file.filePath).isEqualTo(project.file("src/main/jni/hello.c"))
        assertThat(file.getWorkingDirectory()).isEqualTo(project.file("workingDir"))
        setting = settingsMap.find { it.getName() == file.settingsName }
        assertThat(setting.getCompilerFlags()).containsAllOf("fileFlag1", "fileFlag2");

        assertThat(model.getToolChains()).hasSize(1)
        NativeToolchain toolchain = model.getToolChains().first()
        assertThat(toolchain.getName()).isEqualTo("toolchain1")
        assertThat(toolchain.getCCompilerExecutable().getName()).isEqualTo("clang")
        assertThat(toolchain.getCppCompilerExecutable().getName()).isEqualTo("clang++")
    }
}
