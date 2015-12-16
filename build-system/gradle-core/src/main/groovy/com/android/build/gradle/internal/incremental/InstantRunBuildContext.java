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

import com.android.annotations.Nullable;
import com.android.utils.XmlUtils;
import com.google.common.base.CaseFormat;
import com.google.common.base.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Enumeration of the possible file types produced by an instant run enabled build.
     */
    public enum FileType {
        /**
         * Reload dex file that can be used to patch application live.
         */
        RELOAD_DEX,
        /**
         * Restart.dex file that can be used for Dalvik to restart applications with minimum set of
         * changes delivered.
         */
        RESTART_DEX,
        /**
         * Shard dex file that can be used to replace originally installed multi-dex shard.
         */
        DEX,
        /**
         * Code based pure split that can be installed individually on M+ devices.
         */
        SPLIT
    }

    public static class Artifact {
        private final FileType fileType;
        private final File file;

        public Artifact(FileType fileType, File file) {
            this.fileType = fileType;
            this.file = file;
        }
    }

    private final long[] taskStartTime = new long[TaskType.values().length];
    private final long[] taskDurationInMs = new long[TaskType.values().length];
    private final long buildId;
    private Optional<InstantRunVerifierStatus> verifierResult = Optional.absent();
    private InstantRunPatchingPolicy patchingPolicy;
    private final List<Artifact> changedFiles = new ArrayList<Artifact>();

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

    public void setPatchingPolicy(InstantRunPatchingPolicy patchingPolicy) {
        this.patchingPolicy = patchingPolicy;
    }

    @Nullable
    public InstantRunPatchingPolicy getPatchingPolicty() {
        return patchingPolicy;
    }

    public void addChangedFile(FileType fileType, File file) {
        if (patchingPolicy == null) {
            return;
        }
        // validate the patching policy and the received file type to record the file or not.
        switch (patchingPolicy) {
            case PRE_LOLLIPOP:
                if (fileType != FileType.RELOAD_DEX && fileType != FileType.RESTART_DEX) {
                    return;
                }
                break;
            case LOLLIPOP:
                if (fileType != FileType.DEX) {
                    return;
                }
                break;
            case MARSHMALLOW_AND_ABOVE:
                if (fileType != FileType.SPLIT && fileType != FileType.RELOAD_DEX) {
                    return;
                }
        }
        this.changedFiles.add(new Artifact(fileType, file));
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
        if (patchingPolicy != null) {
            instantRun.setAttribute("patching-policy", patchingPolicy.toString());
        }
        if (!changedFiles.isEmpty()) {
            Element builtArtifacts = document.createElement("built-artifacts");
            for (Artifact changedFile : changedFiles) {
                Element artifact = document.createElement("artifact");
                artifact.setAttribute("type", changedFile.fileType.toString());
                artifact.setAttribute("location", XmlUtils.toXmlAttributeValue(
                        changedFile.file.getAbsolutePath()));
                builtArtifacts.appendChild(artifact);
            }
            instantRun.appendChild(builtArtifacts);
        }
        return XmlUtils.toXml(instantRun);
    }

}
