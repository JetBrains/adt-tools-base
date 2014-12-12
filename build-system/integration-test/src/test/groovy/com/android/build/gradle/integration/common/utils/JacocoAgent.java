/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jacoco.agent.AgentJar;

import java.io.File;
import java.io.IOException;

/**
 * Utility to setup for Jacoco agent.
 */
public class JacocoAgent {

    public static String getJvmArg() {
        String buildDir = System.getenv("PROJECT_BUILD_DIR");
        buildDir = (buildDir == null) ? "build" : buildDir;

        File jacocoAgent = new File(buildDir, "jacoco/agent.jar");
        if (!jacocoAgent.isFile()) {
            try {
                AgentJar.extractTo(jacocoAgent);
            } catch (IOException ignored) {
                fail("Error extracting jacoco agent");
            }
        }

        String jvmArgs = "-javaagent:" + jacocoAgent.toString() + "=destfile=" + buildDir + "/jacoco/test.exec";
        System.out.println(jacocoAgent.toString());
        System.out.println(jvmArgs);
        return jvmArgs;
    }
}
