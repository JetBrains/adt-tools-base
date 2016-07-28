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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.Version;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.utils.XmlUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Context object for all InstantRun related information.
 */
public class InstantRunBuildContext {

    private static final Logger LOG = Logging.getLogger(InstantRunBuildContext.class);

    static final String TAG_INSTANT_RUN = "instant-run";
    static final String TAG_BUILD = "build";
    static final String TAG_ARTIFACT = "artifact";
    static final String TAG_TASK = "task";
    static final String ATTR_PLUGIN_VERSION = "plugin-version";
    static final String ATTR_NAME = "name";
    static final String ATTR_DURATION = "duration";
    static final String ATTR_TIMESTAMP = "timestamp";
    static final String ATTR_VERIFIER = "verifier";
    static final String ATTR_TYPE = "type";
    static final String ATTR_LOCATION = "location";
    static final String ATTR_API_LEVEL = "api-level";
    static final String ATTR_DENSITY = "density";
    static final String ATTR_FORMAT = "format";
    static final String ATTR_ABI = "abi";
    static final String ATTR_TOKEN = "token";

    // Keep roughly in sync with InstantRunBuildInfo#isCompatibleFormat:
    //
    // (These aren't directly aliased in case in the future we want to for
    // example have the client understand a range of versions. E.g. Gradle
    // may bump this version to force older IDE's to not attempt instant run
    // with this metadata, but a newer IDE could decide to work both with this
    // new Gradle version and the older version. Whenever we bump this version
    // we should cross check the logic and decide how to handle the isCompatible()
    // method.)
    static final String CURRENT_FORMAT = "8";

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
         * Main APK file for 19, and 21 platforms when using the {@link ColdswapMode#MULTIDEX} mode.
         */
        MAIN,
        /**
         * Main APK file when application is using the {@link ColdswapMode#MULTIAPK} mode.
         */
        SPLIT_MAIN,
        /**
         * Reload dex file that can be used to patch application live.
         */
        RELOAD_DEX,
        /**
         * Restart.dex file that can be used for Dalvik to restart applications with minimum set of
         * changes delivered.
         */
        @Deprecated
        RESTART_DEX,
        /**
         * Shard dex file that can be used to replace originally installed multi-dex shard.
         */
        DEX,
        /**
         * Pure split (code only) that can be installed individually on M+ devices.
         */
        SPLIT,
        /**
         * Resources: res.ap_ file
         */
        RESOURCES,
    }

    /**
     * A Build represents the result of an InstantRun enabled build invocation.
     * It will contain all the artifacts it produced as well as the unique timestamp for the build
     * and the result of the InstantRun verification process.
     */
    public static class Build {
        private final long buildId;
        private Optional<InstantRunVerifierStatus> verifierStatus;
        private final List<Artifact> artifacts = new ArrayList<>();

        public Build(long buildId, @NonNull Optional<InstantRunVerifierStatus> verifierStatus) {
            this.buildId = buildId;
            this.verifierStatus = verifierStatus;
        }

        @Nullable
        public Artifact getArtifactForType(@NonNull FileType fileType) {
            for (Artifact artifact : artifacts) {
                if (artifact.fileType == fileType) {
                    return artifact;
                }
            }
            return null;
        }

        private boolean hasCodeArtifact() {
            for (Artifact artifact : artifacts) {
                FileType type = artifact.getType();
                if (type == FileType.DEX || type == FileType.SPLIT
                        || type == FileType.MAIN || type == FileType.RESTART_DEX) {
                    return true;
                }
            }
            return false;
        }

        private Element toXml(@NonNull Document document) {
            Element build = document.createElement(TAG_BUILD);
            toXml(document, build);
            return build;
        }

        private void toXml(@NonNull Document document, @NonNull Element element) {
            element.setAttribute(ATTR_TIMESTAMP, String.valueOf(buildId));
            if (verifierStatus.isPresent()) {
                element.setAttribute(ATTR_VERIFIER, verifierStatus.get().name());
            }
            for (Artifact artifact : artifacts) {
                element.appendChild(artifact.toXml(document));
            }
        }

        @NonNull
        public static Build fromXml(@NonNull Node buildNode) {
            NamedNodeMap attributes = buildNode.getAttributes();
            Node verifierAttribute = attributes.getNamedItem(ATTR_VERIFIER);
            Build build = new Build(
                    Long.parseLong(attributes.getNamedItem(ATTR_TIMESTAMP).getNodeValue()),
                    verifierAttribute != null
                        ? Optional.of(InstantRunVerifierStatus.valueOf(
                            verifierAttribute.getNodeValue()))
                        : Optional.<InstantRunVerifierStatus>empty());
            NodeList childNodes = buildNode.getChildNodes();
            for (int i=0; i< childNodes.getLength(); i++) {
                Node artifactNode = childNodes.item(i);
                if (artifactNode.getNodeName().equals(TAG_ARTIFACT)) {
                    Artifact artifact = Artifact.fromXml(artifactNode);
                    build.artifacts.add(artifact);
                }
            }
            return build;
        }

        public long getBuildId() {
            return buildId;
        }

        @NonNull
        public List<Artifact> getArtifacts() {
            return artifacts;
        }

        @NonNull
        public Optional<InstantRunVerifierStatus> getVerifierStatus() {
            return verifierStatus;
        }
    }

    /**
     * A build artifact defined by its type and location.
     */
    public static class Artifact {
        private final FileType fileType;
        private File location;

        public Artifact(@NonNull FileType fileType, @NonNull File location) {
            this.fileType = fileType;
            this.location = location;
        }

        @NonNull
        public Node toXml(@NonNull Document document) {
            Element artifact = document.createElement(TAG_ARTIFACT);
            artifact.setAttribute(ATTR_TYPE, fileType.name());
            artifact.setAttribute(ATTR_LOCATION,
                    XmlUtils.toXmlAttributeValue(location.getAbsolutePath()));
            return artifact;
        }

        @NonNull
        public static Artifact fromXml(@NonNull Node artifactNode) {
            NamedNodeMap attributes = artifactNode.getAttributes();
            return new Artifact(
                    FileType.valueOf(attributes.getNamedItem(ATTR_TYPE).getNodeValue()),
                    new File(attributes.getNamedItem(ATTR_LOCATION).getNodeValue()));
        }

        @NonNull
        public File getLocation() {
            return location;
        }

        /**
         * Returns true if the file accumulates all the changes since it was initially built and
         * deployed on the device.
         */
        public boolean isAccumulative() {
            return fileType != FileType.RELOAD_DEX;
        }

        public void setLocation(@NonNull File location) {
            this.location = location;
        }

        @NonNull
        public FileType getType() {
            return fileType;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("fileType", fileType)
                    .add("location", location)
                    .toString();
        }
    }

    private final long[] taskStartTime = new long[TaskType.values().length];
    private final long[] taskDurationInMs = new long[TaskType.values().length];
    private InstantRunPatchingPolicy patchingPolicy;
    /** Null until setApiLevel is called. */
    @Nullable
    private Integer featureLevel = null;
    private String density = null;
    private String abi = null;
    private final Build currentBuild = new Build(
            System.nanoTime(), Optional.<InstantRunVerifierStatus>empty());
    private final TreeMap<Long, Build> previousBuilds = new TreeMap<>();
    private File tmpBuildInfo = null;
    private boolean isInstantRunMode = false;
    private final AtomicLong token = new AtomicLong(0);
    private InstantRunBuildMode buildMode = InstantRunBuildMode.HOT_WARM;

    public void setInstantRunMode(boolean instantRunMode) {
        isInstantRunMode = instantRunMode;
    }

    public boolean isInInstantRunMode() {
        return isInstantRunMode;
    }

    public void setTmpBuildInfo(File tmpBuildInfo) {
        this.tmpBuildInfo = tmpBuildInfo;
    }

    /**
     * Get the unique build id for this build invocation.
     * @return a unique build id.
     */
    public long  getBuildId() {
        return currentBuild.buildId;
    }

    public void startRecording(@NonNull TaskType taskType) {
        taskStartTime[taskType.ordinal()] = System.currentTimeMillis();
    }

    public long stopRecording(@NonNull TaskType taskType) {
        long duration = System.currentTimeMillis() - taskStartTime[taskType.ordinal()];
        taskDurationInMs[taskType.ordinal()] = duration;
        return duration;
    }

    /**
     * Setst the verifier status for the current build.
     * @param verifierStatus
     */
    public void setVerifierResult(@NonNull InstantRunVerifierStatus verifierStatus) {
        if (!currentBuild.verifierStatus.isPresent() ||
                currentBuild.getVerifierStatus().get() == InstantRunVerifierStatus.COMPATIBLE) {
            currentBuild.verifierStatus = Optional.of(verifierStatus);
        }
        buildMode = buildMode.combine(
                verifierStatus.getInstantRunBuildModeForPatchingPolicy(patchingPolicy));
        LOG.info("Got verifier result: {}. Build mode is now {}.", verifierStatus, buildMode);
    }

    /**
     * Returns the verifier status if set for the current build being executed or
     * null {@link Optional} if it is not set.
     */
    public Optional<InstantRunVerifierStatus> getVerifierResult() {
        return currentBuild.getVerifierStatus();
    }

    /**
     * Returns true if the verifier did not find any incompatible changes for InstantRun or was not
     * run due to no code changes.
     * @return true to use hot swapping, false otherwise.
     */
    public boolean hasPassedVerification() {
        return !currentBuild.verifierStatus.isPresent()
                || currentBuild.verifierStatus.get() == InstantRunVerifierStatus.COMPATIBLE;
    }

    public void setApiLevel(int featureLevel,
            @Nullable String coldswapMode,
            @Nullable String targetAbi) {
        this.featureLevel = featureLevel;
        // cache the patching policy.
        this.patchingPolicy = InstantRunPatchingPolicy.getPatchingPolicy(
                featureLevel, coldswapMode, targetAbi);
        this.abi = targetAbi;
    }

    public int getFeatureLevel() {
        return Preconditions.checkNotNull(featureLevel,
                "setApiLevel should be called before any other actions.");
    }

    @Nullable
    public String getDensity() {
        return density;
    }

    public void setDensity(@Nullable String density) {
        this.density = density;
    }

    @Nullable
    public InstantRunPatchingPolicy getPatchingPolicy() {
        return patchingPolicy;
    }


    public InstantRunBuildMode getBuildMode() {
        return buildMode;
    }

    public synchronized void addChangedFile(@NonNull FileType fileType, @NonNull File file)
            throws IOException {
        if (patchingPolicy == null) {
            return;
        }
        // make sure we don't add the same artifacts twice.
        for (Artifact artifact : currentBuild.artifacts) {
            if (artifact.getType() == fileType
                    && artifact.getLocation().getAbsolutePath().equals(file.getAbsolutePath())) {
                return;
            }
        }

        // validate the patching policy and the received file type to record the file or not.
        // RELOAD and MAIN are always record.
        if (fileType != FileType.RELOAD_DEX && fileType != FileType.MAIN &&
                fileType != FileType.RESOURCES) {
            switch (patchingPolicy) {
                case PRE_LOLLIPOP:
                    if (fileType != FileType.RESTART_DEX) {
                        return;
                    }
                    break;
                case MULTI_DEX:
                    if (fileType != FileType.DEX) {
                        return;
                    }
                    break;
                case MULTI_APK:
                    if (fileType != FileType.SPLIT) {
                        return;
                    }
            }
        }
        if (fileType == FileType.MAIN) {
            // in case of MAIN, we need to disambiguate whether this is a SPLIT_MAIN or just a
            // MAIN. this is useful for the IDE so it knows which deployment method to use.
            if (patchingPolicy == InstantRunPatchingPolicy.MULTI_APK) {
                fileType = FileType.SPLIT_MAIN;
            }

            // because of signing/aligning, we can be notified several times of the main APK
            // construction, last one wins.
            Artifact previousArtifact = currentBuild.getArtifactForType(fileType);
            if (previousArtifact != null) {
                currentBuild.artifacts.remove(previousArtifact);
            }

            // also if we are in LOLLIPOP, the DEX files are packaged in the original main APK, so
            // we can remove individual files.
            if (patchingPolicy == InstantRunPatchingPolicy.MULTI_DEX) {
                currentBuild.artifacts.clear();
            }

            // since the main APK is produced, no need to keep the RESOURCES record around.
            Artifact resourcesApFile = currentBuild.getArtifactForType(FileType.RESOURCES);
            while (resourcesApFile != null) {
                currentBuild.artifacts.remove(resourcesApFile);
                resourcesApFile = currentBuild.getArtifactForType(FileType.RESOURCES);
            }
        }
        currentBuild.artifacts.add(new Artifact(fileType, file));
        // save the temporary build info file in case the build fails later on.
        try {
            writeTmpBuildInfo();
        } catch (ParserConfigurationException e) {
            throw new IOException(e);
        }
    }


    @Nullable
    public Build getLastBuild() {
        return previousBuilds.isEmpty() ? null : previousBuilds.lastEntry().getValue();
    }

    @Nullable
    public Artifact getPastBuildsArtifactForType(@NonNull FileType fileType) {
        for (Build build : previousBuilds.values()) {
            Artifact artifact = build.getArtifactForType(fileType);
            if (artifact != null) {
                return artifact;
            }
        }
        return null;
    }

    public long getSecretToken() {
        return token.get();
    }

    public void setSecretToken(long token) {
        this.token.set(token);
    }

    @VisibleForTesting
    public Collection<Build> getPreviousBuilds() {
        return previousBuilds.values();
    }

    /**
     * Remove all unwanted changes :
     *  - All reload.dex changes older than the last cold swap.
     *  - Empty changes (unless it's the last one).
     */
    private void purge() {
        boolean foundColdRestart = false;
        Set<String> splitFilesAlreadyFound = new HashSet<>();
        // the oldest build is by definition the full build.
        Long initialFullBuild = previousBuilds.firstKey();
        // iterate from the most recent to the oldest build, which reflect the most up to date
        // natural order of builds.
        for (Long aBuildId : new ArrayList<>(previousBuilds.descendingKeySet())) {
            Build previousBuild = previousBuilds.get(aBuildId);
            // initial builds are never purged in any way.
            if (previousBuild.buildId == initialFullBuild) {
                continue;
            }
            if (previousBuild.verifierStatus.isPresent()) {
                if (previousBuild.verifierStatus.get() == InstantRunVerifierStatus.COMPATIBLE) {
                    if (foundColdRestart) {
                        previousBuilds.remove(aBuildId);
                        continue;
                    }
                } else {
                    foundColdRestart = true;
                }
            } else {
                // no verifier status is indicative of a full build or no code change.
                // If this is a full build, treat it as a cold restart.
                foundColdRestart = previousBuild.hasCodeArtifact();
            }
            // when a coldswap build was found, remove all RESOURCES entries for previous builds
            // as the resource is redelivered as part of the main split.
            if (foundColdRestart
                    && patchingPolicy == InstantRunPatchingPolicy.MULTI_APK) {
                Artifact resourceApArtifact = previousBuild.getArtifactForType(FileType.RESOURCES);
                if (resourceApArtifact != null) {
                    previousBuild.artifacts.remove(resourceApArtifact);
                }
            }

            // remove all DEX, SPLIT and Resources files from older built artifacts if we have
            // already seen a newer version, we only need to most recent one.
            for (Artifact artifact : new ArrayList<>(previousBuild.artifacts)) {
                if (artifact.isAccumulative()) {
                    // we don't remove artifacts from the first build.
                    if (splitFilesAlreadyFound.contains(artifact.getLocation().getAbsolutePath())) {
                        previousBuild.artifacts.remove(artifact);
                    } else {
                        splitFilesAlreadyFound.add(artifact.getLocation().getAbsolutePath());
                    }
                }
            }
        }

        // bunch of builds can be empty, either because we did nothing or all its artifact got
        // rebuilt in a more recent iteration, in such a case, remove it.
        for (Long aBuildId : new ArrayList<>(previousBuilds.descendingKeySet())) {
            Build aBuild = previousBuilds.get(aBuildId);
            // if the build artifacts are empty and it's not the current build.
            if (aBuild.artifacts.isEmpty() && aBuild.buildId != currentBuild.buildId) {
                previousBuilds.remove(aBuildId);
            }
        }
        if (buildMode == InstantRunBuildMode.FULL) {
            collapseMainArtifactsIntoCurrentBuild();
        }
    }

    private void collapseMainArtifactsIntoCurrentBuild() {
        if (patchingPolicy == InstantRunPatchingPolicy.MULTI_APK) {
            // Add all of the older splits to the current build,
            // as the older builds will be thrown away.
            Set<String> splitLocations = Sets.newHashSet();
            Artifact main = null;

            for (Build build : previousBuilds.values()) {
                for (Artifact artifact : build.artifacts) {
                    if (artifact.fileType == FileType.SPLIT) {
                        splitLocations.add(artifact.location.getAbsolutePath());
                    } else if (artifact.fileType == FileType.SPLIT_MAIN) {
                        main = artifact;
                    }
                }
            }

            // Don't re-add existing splits.
            for (Artifact artifact : currentBuild.artifacts) {
                if (artifact.fileType == FileType.SPLIT) {
                    splitLocations.remove(artifact.location.getAbsolutePath());
                } else if (artifact.fileType == FileType.SPLIT_MAIN) {
                    main = null;
                }
            }

            for (String splitLocation : splitLocations) {
                currentBuild.artifacts
                        .add(new Artifact(FileType.SPLIT, new File(splitLocation)));
            }

            if (main != null) {
                currentBuild.artifacts.add(main);
            }
        } else {
            if (currentBuild.artifacts.isEmpty()) {

                Artifact main = null;

                for (Build build : previousBuilds.values()) {
                    for (Artifact artifact : build.artifacts) {
                        if (artifact.fileType == FileType.MAIN) {
                            main = artifact;
                        }
                    }
                }
                if (main == null) {
                    throw new IllegalStateException("No current or previous main artifacts. "
                            + "This should not happen.");
                }
                currentBuild.artifacts.add(main);
            }
        }
        if (currentBuild.artifacts.isEmpty()) {
            throw new IllegalStateException("Full build with no artifacts. "
                    + "This should not happen.");
        }
    }

    /**
     * Load previous iteration build-info.xml. The only information we really care about is the
     * list of previous builds so we can provide the list of artifacts to the IDE to catch up
     * a disconnected device.
     * @param persistedState the persisted xml file.
     */
    public void loadFromXmlFile(@NonNull File persistedState)
            throws IOException, ParserConfigurationException, SAXException {
        if (!persistedState.exists()) {
            setVerifierResult(InstantRunVerifierStatus.INITIAL_BUILD);
            return;
        }
        loadFromDocument(XmlUtils.parseUtfXmlFile(persistedState, false));
    }

    /**
     * {@link #loadFromXmlFile(File)} but using a String
     */
    public void loadFromXml(@NonNull String persistedState)
            throws IOException, SAXException, ParserConfigurationException {
        loadFromDocument(XmlUtils.parseDocument(persistedState, false));
    }

    private void loadFromDocument(@NonNull Document document) {
        Element instantRun = document.getDocumentElement();

        if (!String.valueOf(getFeatureLevel()).equals(instantRun.getAttribute(ATTR_API_LEVEL))) {
            // Don't load if we've changed api level.
            Logging.getLogger(InstantRunBuildContext.class)
                    .quiet("Instant Run: Target device API level has changed.");
            setVerifierResult(InstantRunVerifierStatus.INITIAL_BUILD);
            return;
        }

        if (!(Version.ANDROID_GRADLE_PLUGIN_VERSION.equals(
                instantRun.getAttribute(ATTR_PLUGIN_VERSION)))) {
            // Don't load if the plugin version has changed.
            Logging.getLogger(InstantRunBuildContext.class)
                    .quiet("Instant Run: Android plugin version has changed.");
            setVerifierResult(InstantRunVerifierStatus.INITIAL_BUILD);
            return;
        }

        String tokenString = instantRun.getAttribute(ATTR_TOKEN);
        if (!Strings.isNullOrEmpty(tokenString)) {
            token.set(Long.parseLong(tokenString));
        }

        Build lastBuild = Build.fromXml(instantRun);
        previousBuilds.put(lastBuild.buildId, lastBuild);
        NodeList buildNodes = instantRun.getChildNodes();
        for (int i=0; i<buildNodes.getLength();i++) {
            Node buildNode = buildNodes.item(i);
            if (buildNode.getNodeName().equals(TAG_BUILD)) {
                Build build = Build.fromXml(buildNode);
                previousBuilds.put(build.buildId, build);
            }
        }
    }

    /**
     * Merges the artifacts of a temporary build info into this build's artifacts. If this build
     * finishes the build-info.xml will contain the artifacts produced by this iteration as well
     * as the artifacts produced in a previous iteration and saved into the temporary build info.
     * @param tmpBuildInfoFile a past build build-info.xml
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */

    public void mergeFromFile(@NonNull File tmpBuildInfoFile)
            throws IOException, SAXException, ParserConfigurationException {
        if (!tmpBuildInfoFile.exists()) {
            return;
        }
        mergeFrom(XmlUtils.parseUtfXmlFile(tmpBuildInfoFile, false));
    }

    /**
     * Merges the artifacts of a temporary build info into this build's artifacts. If this build
     * finishes the build-info.xml will contain the artifacts produced by this iteration as well
     * as the artifacts produced in a previous iteration and saved into the temporary build info.
     * @param tmpBuildInfo a past build build-info.xml as a String
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */

    public void mergeFrom(@NonNull String tmpBuildInfo)
            throws IOException, SAXException, ParserConfigurationException {

        mergeFrom(XmlUtils.parseDocument(tmpBuildInfo, false));
    }

    private void mergeFrom(@NonNull Document document) throws IOException {
        Element instantRun = document.getDocumentElement();
        Build lastBuild = Build.fromXml(instantRun);
        for (Artifact previousArtifact: lastBuild.getArtifacts()) {
            mergeArtifact(previousArtifact);
        }
    }

    private void mergeArtifact(@NonNull Artifact stashedArtifact) {
        for (Artifact artifact : currentBuild.artifacts) {
            if (artifact.getType() == stashedArtifact.getType()
                    && artifact.getLocation().getAbsolutePath().equals(
                    stashedArtifact.getLocation().getAbsolutePath())) {
                return;
            }
        }

        currentBuild.getArtifacts().add(stashedArtifact);
    }

    /**
     * Close all activities related to InstantRun.
     */
    public void close() {
        // add the current build to the list of builds to be persisted.
        previousBuilds.put(currentBuild.buildId, currentBuild);

        // purge unwanted past iterations.
        purge();
    }

    /**
     * Define the pesistence mode for this context (which results in the build-info.xml).
     */
    @VisibleForTesting
    enum PersistenceMode {
        /**
         * Persist this build as a final full build (and do not include any previous builds).
         */
        FULL_BUILD,
        /**
         * Persist this build as a final incremental build and include all previous builds
         */
        INCREMENTAL_BUILD,
        /**
         * Persist this build as a temporary build (that may still execute or failed to complete)
         */
        TEMP_BUILD
    }

    /**
     * Serialize this context into an xml format.
     * @return the xml persisted information as a {@link String}
     * @throws ParserConfigurationException
     */
    public String toXml() throws ParserConfigurationException {
        return toXml(buildMode == InstantRunBuildMode.FULL
                ? PersistenceMode.FULL_BUILD
                : PersistenceMode.INCREMENTAL_BUILD);
    }

    /**
     * Serialize this context into an xml format.
     * @param persistenceMode desired {@link PersistenceMode}
     * @return the xml persisted information as a {@link String}
     * @throws ParserConfigurationException
     */
    @NonNull
    @VisibleForTesting
    String toXml(@NonNull PersistenceMode persistenceMode) throws ParserConfigurationException {

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        toXml(document, persistenceMode);
        return XmlPrettyPrinter.prettyPrint(document, true);
    }

    private Element toXml(Document document, PersistenceMode persistenceMode) {
        Element instantRun = document.createElement(TAG_INSTANT_RUN);
        document.appendChild(instantRun);

        for (TaskType taskType : TaskType.values()) {
            Element taskTypeNode = document.createElement(TAG_TASK);
            taskTypeNode.setAttribute(ATTR_NAME,
                    CaseFormat.UPPER_UNDERSCORE.converterTo(
                            CaseFormat.LOWER_HYPHEN).convert(taskType.name()));
            taskTypeNode.setAttribute(ATTR_DURATION,
                    String.valueOf(taskDurationInMs[taskType.ordinal()]));
            instantRun.appendChild(taskTypeNode);
        }

        currentBuild.toXml(document, instantRun);
        instantRun.setAttribute(ATTR_API_LEVEL, String.valueOf(getFeatureLevel()));
        if (density != null) {
            instantRun.setAttribute(ATTR_DENSITY, density);
        }
        if (abi != null) {
            instantRun.setAttribute(ATTR_ABI, abi);
        }
        if (token != null) {
            instantRun.setAttribute(ATTR_TOKEN, token.toString());
        }
        instantRun.setAttribute(ATTR_FORMAT, CURRENT_FORMAT);
        instantRun.setAttribute(ATTR_PLUGIN_VERSION, Version.ANDROID_GRADLE_PLUGIN_VERSION);

        switch(persistenceMode) {
            case FULL_BUILD:
                // only include the last build.
                if (!previousBuilds.isEmpty()) {
                    instantRun.appendChild(previousBuilds.lastEntry().getValue().toXml(document));
                }
                break;
            case INCREMENTAL_BUILD:
                for (Build build : previousBuilds.values()) {
                    instantRun.appendChild(build.toXml(document));
                }
                break;
            case TEMP_BUILD:
                break;
            default :
                throw new RuntimeException("PersistenceMode not handled" + persistenceMode);
        }
        return instantRun;
    }

    /**
     * Writes a temporary build-info.xml to persist the produced artifacts in case the build
     * fails before we have a chance to write the final build-info.xml
     * @throws ParserConfigurationException
     * @throws IOException
     */
    private void writeTmpBuildInfo() throws ParserConfigurationException, IOException {

        if (tmpBuildInfo == null) {
            return;
        }
        Files.createParentDirs(tmpBuildInfo);
        Files.write(toXml(PersistenceMode.TEMP_BUILD), tmpBuildInfo, Charsets.UTF_8);
    }
}
