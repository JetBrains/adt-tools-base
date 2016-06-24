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

import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.builder.model.AndroidProject
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.SyncIssue
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
/**
 * Tests expected build output
 */
@CompileStatic
class NativeBuildOutputTest {

    private static String cmakeLists = """cmake_minimum_required(VERSION 3.4.1)
            file(GLOB SRC src/main/cpp/hello-jni.cpp)
            set(CMAKE_VERBOSE_MAKEFILE ON)
            add_library(hello-jni SHARED \${SRC})
            target_link_libraries(hello-jni log)""";

    private static String androidMk = """LOCAL_PATH := \$(call my-dir)
           include \$(CLEAR_VARS)
           LOCAL_MODULE    := hello-jni
           LOCAL_SRC_FILES := hello-jni.cpp
           include \$(BUILD_SHARED_LIBRARY)""";

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
                        .fromTestApp(HelloWorldJniApp.builder()
                            .withNativeDir("cpp")
                            .useCppSource(true)
                            .build())
                        .create();

    @Before
    public void setup() {
        project.buildFile << """
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
            }
            """;
    }

    @Test
    public void checkNdkBuildErrorInSourceCode() {
        project.buildFile << """
            android {
                externalNativeBuild {
                    ndkBuild {
                        path "src/main/cpp/Android.mk"
                    }
                }
            }
            """;

        project.file("src/main/cpp/Android.mk") << androidMk;
        project.file("src/main/cpp/hello-jni.cpp").write("xx");

        check(["'xx' does not name a type"], [], 0);
    }

    @Test
    public void checkCMakeErrorInSourceCode() {
        project.buildFile << """
            android {
                externalNativeBuild {
                    cmake {
                        path "CMakeLists.txt"
                    }
                }
            }
            """;

        project.file("CMakeLists.txt") << cmakeLists;
        project.file("src/main/cpp/hello-jni.cpp").write("xx");

        check(["'xx' does not name a type"], [], 0);
    }

    @Test
    public void checkMissingCMakeLists() {
        project.buildFile << """
            android {
                externalNativeBuild {
                    cmake {
                        path "non/existent/CMakeLists.txt"
                    }
                }
            }
            """;

        check(["cmake.path",
             "CMakeLists.txt but that file doesn't exist"],
            ["cmake.path",
             "CMakeLists.txt but that file doesn't exist"], 2);
    }

    @Test
    public void checkNdkBuildUnrecognizedAbi() {
        project.buildFile << """
            android {
                externalNativeBuild {
                    ndkBuild {
                        path "src/main/cpp/Android.mk"
                    }
                }
                defaultConfig {
                  externalNativeBuild {
                    ndkBuild {
                        abiFilters "-unrecognized-abi-" // <-- error
                    }
                  }
                }
            }
            """;

        project.file("src/main/cpp/Android.mk") << androidMk;

        check(["The -unrecognized-abi- ABI should"], [], 2);
    }

    @Test
    public void checkCMakeUnrecognizedAbi() {
        project.buildFile << """
            android {
                externalNativeBuild {
                    cmake {
                        path "src/main/cpp/CMakeLists.txt"
                    }
                }
                defaultConfig {
                    externalNativeBuild {
                      cmake {
                        abiFilters "-unrecognized-abi-" // <-- error
                      }
                    }
                }
            }
            """;

        project.file("src/main/cpp/CMakeLists.txt") << cmakeLists;

        check([" \"-unrecognized-abi-\" is not supported"], [], 2);
    }

    @Test
    public void checkMissingAndroidMk() {
        project.buildFile << """
            android {
                externalNativeBuild {
                    ndkBuild {
                        path "non/existent/Android.mk" // <-- error
                    }
                }
            }
            """;

        check(["ndkBuild.path", "Android.mk but that file doesn't exist"],
              ["ndkBuild.path", "Android.mk but that file doesn't exist"], 2);
    }

    @Test
    public void checkNdkBuildUnrecognizedTarget() {
        project.buildFile << """
            android {
                externalNativeBuild {
                    ndkBuild {
                        path "src/main/cpp/Android.mk"
                    }
                }
                defaultConfig {
                    externalNativeBuild {
                        ndkBuild {
                            targets "-unrecognized-target-" // <-- error
                        }
                    }
                }
            }
            """;

        project.file("src/main/cpp/Android.mk") << androidMk;

        check(["Unexpected native build target -unrecognized-target-",
               "Valid values are: hello-jni"],
                [], 0);
    }

    @Test
    public void checkCMakeWrongTarget() {
        project.buildFile << """
            android {
                externalNativeBuild {
                    cmake {
                        path "CMakeLists.txt"
                    }
                }
                defaultConfig {
                    externalNativeBuild {
                      cmake {
                        targets "-unrecognized-target-" // <-- error
                      }
                    }
                }
            }
            """;

        project.file("CMakeLists.txt") << cmakeLists;

        check(["Unexpected native build target -unrecognized-target-",
            "Valid values are: hello-jni"],
            [], 0);
    }

    private void check(List<String> expectInStderr, List<String> expectInSyncIssues,
            int expectedSyncIssueCount) {
        // Check the sync
        AndroidProject androidProject = project.model().getSingle(AndroidProject.class);

        // Check for expected sync issues
        assertThat(androidProject.syncIssues).hasSize(expectedSyncIssueCount);
        if (!androidProject.getSyncIssues().isEmpty()) {
            if (!expectInSyncIssues.isEmpty()) {
                SyncIssue issue = assertThat(androidProject).hasIssue(SyncIssue.SEVERITY_ERROR,
                        SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION);

                for (String expect : expectInSyncIssues) {
                    assertThat(issue.message).contains(expect);
                }
            }

            // All other errors should be ProcessException with standard message
            if (expectInSyncIssues.isEmpty() && expectedSyncIssueCount > 0) {
                // There should be some expected stderr to be found in getData() of the sync issue
                assertThat(expectInStderr.size()).isGreaterThan(0);
                SyncIssue issue = assertThat(androidProject).hasIssue(SyncIssue.SEVERITY_ERROR,
                        SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION);
                for (String expect : expectInStderr) {
                    assertThat(issue.data).contains(expect);
                }
            }
        }

        // Make sure we can get a NativeAndroidProject
        project.model().getSingle(NativeAndroidProject.class);

        // Check the build
        GradleBuildResult result = project.executor()
                .expectFailure()
                .withEnableInfoLogging(false)
                .run("assembleDebug");
        String stderr = result.getStderr();
        for (String expect : expectInStderr) {
            assertThat(stderr).contains(expect);
        }
    }
}
