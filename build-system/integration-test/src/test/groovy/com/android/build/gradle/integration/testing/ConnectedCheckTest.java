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

package com.android.build.gradle.integration.testing;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConnectedCheckTest {
    @Rule
    public GradleTestProject project;

    public ConnectedCheckTest() {
        project = GradleTestProject.builder()
                .fromTestProject("appWithTests")
                .useExperimentalGradleVersion(false)
                .create();
    }

    @Category(DeviceTests.class)
    @Test
    public void connectedCheckOnAllDevices() {
        project.executeConnectedCheck();
        assertThat(project.getStdout().contains("Starting 3 tests on"));
    }

    @Category(DeviceTests.class)
    @Test
    public void connectedCheckShardedOn1Device() {
        // assumes there is only 1 connected device
        project.executeConnectedCheck(
                ImmutableList.of("-Pandroid.androidTest.shardBetweenDevices=true"));
        // this test will fail if there are more than 1 device connected
        assertThat(project.getStdout()).contains("will shard tests into 1 shards");
        assertThat(project.getStdout()).contains("Starting 3 tests on");
        assertThat(project.getStdout()).contains("finished 1 of estimated 3 tests");
        assertThat(project.getStdout()).contains("finished 2 of estimated 3 tests");
        assertThat(project.getStdout()).contains("finished 3 of estimated 3 tests");
    }

    @Category(DeviceTests.class)
    @Test
    public void connectedCheckIn7Shards() {
        project.executeConnectedCheck(
                ImmutableList.of("-Pandroid.androidTest.shardBetweenDevices=true",
                        "-Pandroid.androidTest.numShards=7"));
        assertThat(project.getStdout()).contains("will shard tests into 7 shards");
    }
}
