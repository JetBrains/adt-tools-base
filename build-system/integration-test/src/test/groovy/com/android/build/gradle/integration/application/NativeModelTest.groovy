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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.builder.model.AndroidProject
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeArtifact
import com.android.builder.model.NativeSettings
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Multimap
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * General Model tests
 */
@CompileStatic
@RunWith(Parameterized.class)
class NativeModelTest {
    private static enum Config {
        ANDROID_MK_FILE_C("""
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
                externalNativeBuild {
                    ndkBuild {
                        path file("src/main/cxx/Android.mk")
                    }
                }
                defaultConfig {
                      ndkBuild {
                        cFlags = "-DTEST_C_FLAG"
                        cppFlags = "-DTEST_CPP_FLAG"
                      }
                }
            }
            """, HelloWorldJniApp.androidMkC("src/main/cxx"), false),
        ANDROID_MK_FILE_CPP("""
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
                externalNativeBuild {
                    ndkBuild {
                        path file("src/main/cxx/Android.mk")
                    }
                }
                defaultConfig {
                      ndkBuild {
                        cFlags = "-DTEST_C_FLAG"
                        cppFlags = "-DTEST_CPP_FLAG"
                      }
                }
            }
            """, HelloWorldJniApp.androidMkCpp("src/main/cxx"), true),
        ANDROID_MK_FOLDER_C("""
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
                externalNativeBuild {
                    ndkBuild {
                        path file("src/main/cxx")
                    }
                }
                defaultConfig {
                      ndkBuild {
                        cFlags = "-DTEST_C_FLAG"
                        cppFlags = "-DTEST_CPP_FLAG"
                      }
                }
            }
            """, HelloWorldJniApp.androidMkC("src/main/cxx"), false),
        ANDROID_MK_FOLDER_CPP("""
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
                externalNativeBuild {
                    ndkBuild {
                        path file("src/main/cxx")
                    }
                }
                defaultConfig {
                      ndkBuild {
                        cFlags = "-DTEST_C_FLAG"
                        cppFlags = "-DTEST_CPP_FLAG"
                      }
                }
            }
            """, HelloWorldJniApp.androidMkCpp("src/main/cxx"), true),
        CMAKELISTS_FILE_CPP("""
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
                externalNativeBuild {
                    cmake {
                        path "."
                    }
                }
                defaultConfig {
                      cmake {
                        cFlags = "-DTEST_C_FLAG"
                        cppFlags = "-DTEST_CPP_FLAG"
                      }
                }
            }
            """, HelloWorldJniApp.cmakeLists("."), true),
        CMAKELISTS_FILE_C("""
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
                externalNativeBuild {
                    cmake {
                        path "CMakeLists.txt"
                    }
                }
                defaultConfig {
                      cmake {
                        cFlags = "-DTEST_C_FLAG"
                        cppFlags = "-DTEST_CPP_FLAG"
                      }
                }
            }
            """, HelloWorldJniApp.cmakeLists("."), false),
        CMAKELISTS_FOLDER_CPP("""
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
                externalNativeBuild {
                    cmake {
                        path "CMakeLists.txt"
                    }
                }
                defaultConfig {
                      cmake {
                        cFlags = "-DTEST_C_FLAG"
                        cppFlags = "-DTEST_CPP_FLAG"
                      }
                }
            }
            """, HelloWorldJniApp.cmakeLists("."), true),
        CMAKELISTS_FOLDER_C("""
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
                externalNativeBuild {
                    cmake {
                        path "."
                    }
                }
                defaultConfig {
                      cmake {
                        cFlags = "-DTEST_C_FLAG"
                        cppFlags = "-DTEST_CPP_FLAG"
                      }
                }
            }
            """, HelloWorldJniApp.cmakeLists("."), false);

        public final String buildGradle;
        private final TestSourceFile nativeBuildFile;
        public final boolean isCpp;

        Config(String buildGradle, TestSourceFile nativeBuildFile, boolean isCpp) {
            this.buildGradle = buildGradle;
            this.nativeBuildFile = nativeBuildFile;
            this.isCpp = isCpp;
        }

        public GradleTestProject create() {
            GradleTestProject project = GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder()
                        .withNativeDir("cxx")
                        .useCppSource(isCpp)
                        .build())
                    .addFile(nativeBuildFile)
                    .create();

            return project;
        }
    }

    @Rule
    public GradleTestProject project = config.create();


    @Parameterized.Parameters(name = "model = {0}")
    public static Collection<Object[]> data() {
        return [
                [Config.ANDROID_MK_FILE_C].toArray(),
                [Config.ANDROID_MK_FILE_CPP].toArray(),
                [Config.ANDROID_MK_FOLDER_C].toArray(),
                [Config.ANDROID_MK_FOLDER_CPP].toArray(),
                [Config.CMAKELISTS_FILE_C].toArray(),
                [Config.CMAKELISTS_FILE_CPP].toArray(),
                [Config.CMAKELISTS_FOLDER_C].toArray(),
                [Config.CMAKELISTS_FOLDER_CPP].toArray()
        ];
    }

    private Config config;

    NativeModelTest(Config config) {
        this.config = config;
    }

    @Before
    public void setup() {
        project.buildFile << config.buildGradle;
        project.execute("generateJsonModelDebug", "generateJsonModelRelease")
    }

    @Test
    public void checkModel() {
        AndroidProject androidProject = project.model().getSingle(AndroidProject.class);
        assertThat(androidProject.syncIssues).hasSize(0);
        NativeAndroidProject model = project.model().getSingle(NativeAndroidProject.class);
        assertThat(model).isNotNull();
        assertThat(model.name).isEqualTo("project");
        assertThat(model.artifacts).hasSize(14);
        assertThat(model.fileExtensions).hasSize(1);

        // Settings should be non-empty but the count depends on flags and build system because
        // settings are the distinct set of flags.
        assertThat(model.settings).isNotEmpty();
        assertThat(model.toolChains).hasSize(14);

        for (File file : model.buildFiles) {
            assertThat(file).isFile();
        }

        Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();

        for (NativeArtifact artifact : model.artifacts) {
            File parent = artifact.getOutputFile().getParentFile();
            List<String> parents = Lists.newArrayList();
            while(parent != null) {
                parents.add(parent.getName());
                parent = parent.getParentFile();
            }
            assertThat(parents).contains("obj");
            assertThat(parents).doesNotContain("lib");

            groupToArtifacts.put(artifact.getGroupName(), artifact);
        }

        assertThat(groupToArtifacts.keySet()).containsExactly("debug", "release");
        assertThat(groupToArtifacts.get("debug")).hasSize(groupToArtifacts.get("release").size());

        if (config.isCpp) {
            checkIsCpp(model);
        } else {
            checkIsC(model);
        }
    }

    private void checkIsC(NativeAndroidProject model) {
        assertThat(model.fileExtensions).containsEntry("c", "c");
        for (NativeSettings settings : model.settings) {
            assertThat(settings.compilerFlags).contains("-DTEST_C_FLAG");
            assertThat(settings.compilerFlags).doesNotContain("-DTEST_CPP_FLAG");
        }
    }

    private void checkIsCpp(NativeAndroidProject model) {
        assertThat(model.fileExtensions).containsEntry("cpp", "c++");
        for (NativeSettings settings : model.settings) {
            assertThat(settings.compilerFlags).contains("-DTEST_CPP_FLAG");
        }
    }
}
