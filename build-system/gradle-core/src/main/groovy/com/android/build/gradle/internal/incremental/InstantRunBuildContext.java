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

package com.android.build.gradle.internal.incremental;

import com.android.utils.XmlUtils;
import com.google.common.base.CaseFormat;
import com.google.common.base.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Context object for all InstantRun related information.
 */
public class InstantRunBuildContext {

    public enum TaskType {
        JAVAC,
        INSTANT_RUN_DEX,
        INSTANT_RUN_TRANSFORM,
        VERIFIER
    }

    private final long[] taskStartTime = new long[TaskType.values().length];
    private final long[] taskDurationInMs = new long[TaskType.values().length];
    private final long buildId;
    private Optional<InstantRunVerifierStatus> verifierResult = Optional.absent();

    public InstantRunBuildContext() {
        buildId = System.currentTimeMillis();
    }

    /**
     * Get the unique build id for this build invocation.
     * @return a unique build id.
     */
    public long getBuildId() {
        return buildId;
    }

    public void startRecording(TaskType taskType) {
        taskStartTime[taskType.ordinal()] = System.currentTimeMillis();
    }

    public long stopRecording(TaskType taskType) {
        long duration = System.currentTimeMillis() - taskStartTime[taskType.ordinal()];
        taskDurationInMs[taskType.ordinal()] = duration;
        return duration;
    }

    public void setVerifierResult(InstantRunVerifierStatus incompatibleChangeOptional) {
        verifierResult = Optional.of(incompatibleChangeOptional);
    }

    /**
     * Returns true if the verifier did not find any incompatible changes for InstantRun or was not
     * run due to no code changes.
     * @return true to use hot swapping, false otherwise.
     */
    public boolean hasPassedVerification() {
        return !verifierResult.isPresent()
                || verifierResult.get() == InstantRunVerifierStatus.COMPATIBLE;
    }

    /**
     * Serialize this context into an xml file.
     * @return the xml persisted information as a {@link String}
     * @throws ParserConfigurationException
     */
    public String toXml() throws ParserConfigurationException {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element instantRun = document.createElement("instant-run");
        for (TaskType taskType : TaskType.values()) {
            Element taskTypeNode = document.createElement(
                    CaseFormat.UPPER_UNDERSCORE.converterTo(
                            CaseFormat.LOWER_HYPHEN).convert(taskType.name()));
            taskTypeNode.setAttribute("duration",
                    String.valueOf(taskDurationInMs[taskType.ordinal()]));
            instantRun.appendChild(taskTypeNode);
        }
        if (verifierResult.isPresent()) {
            instantRun.setAttribute("verifier", verifierResult.get().name());
        }
        instantRun.setAttribute("build-id", String.valueOf(buildId));
        return XmlUtils.toXml(instantRun);
    }

}
