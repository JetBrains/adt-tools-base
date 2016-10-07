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
package com.android.build.gradle.external.gnumake;

import com.android.SdkConstants;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.truth.NativeBuildConfigValueSubject;
import com.google.common.truth.Truth;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;


public class NativeBuildConfigValueBuilderTest {

    private static void assertThatNativeBuildConfigEquals(String string, String expected) {
        String projectPath = "/projects/MyProject/jni/Android.mk";

        NativeBuildConfigValue actualValue =
                new NativeBuildConfigValueBuilder(new File(projectPath))
                        .addCommands("echo build command", "debug", string, true)
                        .build();
        String actualResult = new GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(actualValue);
        System.err.println(actualResult);

        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            expected = expected.replace("/", "\\\\");
        }

        NativeBuildConfigValue expectedValue =
                new Gson().fromJson(expected, NativeBuildConfigValue.class);

        Truth.assert_().about(NativeBuildConfigValueSubject.FACTORY)
                .that(actualValue).isEqualTo(expectedValue);
    }

    @Test
    public void doubleTarget() throws FileNotFoundException {
        assertThatNativeBuildConfigEquals("g++ -c a.c -o x86_64/a.o\n" +
                "g++ x86_64/a.o -o x86_64/a.so\n" +
                "g++ -c a.c -o x86/a.o\n" +
                "g++ x86/a.o -o x86/a.so", "{\n"
                + "  \"buildFiles\": [\n"
                + "    {\n"
                + "      \"path\": \"/projects/MyProject/jni/Android.mk\"\n"
                + "    }\n"
                + "  ],\n"
                + "  \"cleanCommands\": [\n"
                + "    \"echo build command clean\"\n"
                + "  ],"
                + "  \"libraries\": {\n"
                + "    \"a-debug-x86_64\": {\n"
                + "      abi : \"x86_64\","
                + "      artifactName : \"a\","
                + "      \"buildCommand\": \"echo build command x86_64/a.so\",\n"
                + "      \"toolchain\": \"toolchain-x86_64\",\n"
                + "      \"files\": [\n"
                + "        {\n"
                + "          \"src\": {\n"
                + "            \"path\": \"a.c\"\n"
                + "          },\n"
                + "          \"flags\": \"\"\n"
                + "        }\n"
                + "      ],\n"
                + "      \"output\": {\n"
                + "        \"path\": \"x86_64/a.so\"\n"
                + "      }\n"
                + "    },\n"
                + "    \"a-debug-x86\": {\n"
                + "      abi : \"x86\","
                + "      artifactName : \"a\","
                + "      \"buildCommand\": \"echo build command x86/a.so\",\n"
                + "      \"toolchain\": \"toolchain-x86\",\n"
                + "      \"files\": [\n"
                + "        {\n"
                + "          \"src\": {\n"
                + "            \"path\": \"a.c\"\n"
                + "          },\n"
                + "          \"flags\": \"\"\n"
                + "        }\n"
                + "      ],\n"
                + "      \"output\": {\n"
                + "        \"path\": \"x86/a.so\"\n"
                + "      }\n"
                + "    }\n"
                + "  },\n"
                + "  \"toolchains\": {\n"
                + "    \"toolchain-x86\": {\n"
                + "      \"cCompilerExecutable\": {\n"
                + "        \"path\": \"g++\"\n"
                + "      }\n"
                + "    },\n"
                + "    \"toolchain-x86_64\": {\n"
                + "      \"cCompilerExecutable\": {\n"
                + "        \"path\": \"g++\"\n"
                + "      }\n"
                + "    }\n"
                + "  },\n"
                + "  \"cFileExtensions\": [\n"
                + "    \"c\"\n"
                + "  ],\n"
                + "  \"cppFileExtensions\": []\n"
                + "}");
    }

    @Test
    public void includeInSource() throws FileNotFoundException {
        assertThatNativeBuildConfigEquals("g++ -c a.c -o x/aa.o -Isome-include-path\n", "{\n"
                + "  \"buildFiles\": [\n"
                + "    {\n"
                + "      \"path\": \"/projects/MyProject/jni/Android.mk\"\n"
                + "    }\n"
                + "  ],\n"
                + "  \"cleanCommands\": [\n"
                + "    \"echo build command clean\"\n"
                + "  ],"
                + "  \"libraries\": {\n"
                + "    \"aa-debug-x\": {\n"
                + "      \"buildCommand\": \"echo build command x/aa.o\",\n"
                + "      \"toolchain\": \"toolchain-x\",\n"
                + "      \"abi\": \"x\",\n"
                + "      artifactName : \"aa\","
                + "      \"files\": [\n"
                + "        {\n"
                + "          \"src\": {\n"
                + "            \"path\": \"a.c\"\n"
                + "          },\n"
                + "          \"flags\": \"\\\"-Isome-include-path\\\"\"\n"
                + "        }\n"
                + "      ],\n"
                + "      \"output\": {\n"
                + "        \"path\": \"x/aa.o\"\n"
                + "      }\n"
                + "    }\n"
                + "  },\n"
                + "  \"toolchains\": {\n"
                + "    \"toolchain-x\": {\n"
                + "      \"cCompilerExecutable\": {\n"
                + "        \"path\": \"g++\"\n"
                + "      }\n"
                + "    }\n"
                + "  },\n"
                + "  \"cFileExtensions\": [\n"
                + "    \"c\"\n"
                + "  ],\n"
                + "  \"cppFileExtensions\": []\n"
                + "}");
    }

    @Test
    public void weirdExtension1() throws FileNotFoundException {
        assertThatNativeBuildConfigEquals("g++ -c a.c -o x86_64/aa.o\n" +
                "g++ -c a.S -o x86_64/aS.so\n" +
                "g++ x86_64/aa.o x86_64/aS.so -o x86/a.so", "{\n"
                + "  \"buildFiles\": [\n"
                + "    {\n"
                + "      \"path\": \"/projects/MyProject/jni/Android.mk\"\n"
                + "    }\n"
                + "  ],\n"
                + "  \"cleanCommands\": [\n"
                + "    \"echo build command clean\"\n"
                + "  ],"
                + "  \"libraries\": {\n"
                + "    \"a-debug-x86\": {\n"
                + "      abi : \"x86\","
                + "      artifactName : \"a\","
                + "      \"buildCommand\": \"echo build command x86/a.so\",\n"
                + "      \"toolchain\": \"toolchain-x86\",\n"
                + "      \"files\": [\n"
                + "        {\n"
                + "          \"src\": {\n"
                + "            \"path\": \"a.S\"\n"
                + "          },\n"
                + "          \"flags\": \"\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"src\": {\n"
                + "            \"path\": \"a.c\"\n"
                + "          },\n"
                + "          \"flags\": \"\"\n"
                + "        }\n"
                + "      ],\n"
                + "      \"output\": {\n"
                + "        \"path\": \"x86/a.so\"\n"
                + "      }\n"
                + "    }\n"
                + "  },\n"
                + "  \"toolchains\": {\n"
                + "    \"toolchain-x86\": {\n"
                + "      \"cCompilerExecutable\": {\n"
                + "        \"path\": \"g++\"\n"
                + "      }\n"
                + "    }\n"
                + "  },\n"
                + "  \"cFileExtensions\": [\n"
                + "    \"S\",\n"
                + "    \"c\"\n"
                + "  ],\n"
                + "  \"cppFileExtensions\": []\n"
                + "}");
    }

    @Test
    public void weirdExtension2() throws FileNotFoundException {
        assertThatNativeBuildConfigEquals("g++ -c a.S -o x86_64/aS.so\n" +
                "g++ -c a.c -o x86_64/aa.o\n" +
                "g++ x86_64/aa.o x86_64/aS.so -o x86/a.so", "{\n"
                + "  \"buildFiles\": [\n"
                + "    {\n"
                + "      \"path\": \"/projects/MyProject/jni/Android.mk\"\n"
                + "    }\n"
                + "  ],\n"
                + "  \"cleanCommands\": [\n"
                + "    \"echo build command clean\"\n"
                + "  ],"
                + "  \"libraries\": {\n"
                + "    \"a-debug-x86\": {\n"
                + "      abi : \"x86\","
                + "      artifactName : \"a\","
                + "      \"buildCommand\": \"echo build command x86/a.so\",\n"
                + "      \"toolchain\": \"toolchain-x86\",\n"
                + "      \"files\": [\n"
                + "        {\n"
                + "          \"src\": {\n"
                + "            \"path\": \"a.S\"\n"
                + "          },\n"
                + "          \"flags\": \"\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"src\": {\n"
                + "            \"path\": \"a.c\"\n"
                + "          },\n"
                + "          \"flags\": \"\"\n"
                + "        }\n"
                + "      ],\n"
                + "      \"output\": {\n"
                + "        \"path\": \"x86/a.so\"\n"
                + "      }\n"
                + "    }\n"
                + "  },\n"
                + "  \"toolchains\": {\n"
                + "    \"toolchain-x86\": {\n"
                + "      \"cCompilerExecutable\": {\n"
                + "        \"path\": \"g++\"\n"
                + "      }\n"
                + "    }\n"
                + "  },\n"
                + "  \"cFileExtensions\": [\n"
                + "    \"c\",\n"
                + "    \"S\"\n"
                + "  ],\n"
                + "  \"cppFileExtensions\": []\n"
                + "}");
    }
}
