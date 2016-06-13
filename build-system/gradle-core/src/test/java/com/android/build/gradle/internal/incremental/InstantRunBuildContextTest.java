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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.internal.incremental.InstantRunBuildContext.Build;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Tests for the {@link InstantRunBuildContext}
 */
public class InstantRunBuildContextTest {

    @Test
    public void testTaskDurationRecording() throws ParserConfigurationException {
        InstantRunBuildContext instantRunBuildContext = new InstantRunBuildContext();
        instantRunBuildContext.startRecording(InstantRunBuildContext.TaskType.VERIFIER);
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertThat(instantRunBuildContext.stopRecording(InstantRunBuildContext.TaskType.VERIFIER))
                .isAtLeast(1L);
        assertThat(instantRunBuildContext.getBuildId()).isNotEqualTo(
                new InstantRunBuildContext().getBuildId());
    }

    @Test
    public void testPersistenceFromCleanState() throws ParserConfigurationException {
        InstantRunBuildContext instantRunBuildContext = new InstantRunBuildContext();
        instantRunBuildContext.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetAbi */);
        String persistedState = instantRunBuildContext.toXml();
        assertThat(persistedState).isNotEmpty();
        assertThat(persistedState).contains(InstantRunBuildContext.ATTR_TIMESTAMP);
    }

    @Test
    public void testFormatPresence() throws ParserConfigurationException {
        InstantRunBuildContext instantRunBuildContext = new InstantRunBuildContext();
        instantRunBuildContext.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetAbi */);
        String persistedState = instantRunBuildContext.toXml();
        assertThat(persistedState).isNotEmpty();
        assertThat(persistedState).contains(InstantRunBuildContext.ATTR_FORMAT
                + "=\"" + InstantRunBuildContext.CURRENT_FORMAT + "\"");
    }

    @Test
    public void testDuplicateEntries() throws ParserConfigurationException, IOException {
        InstantRunBuildContext context = new InstantRunBuildContext();
        context.setApiLevel(21, ColdswapMode.MULTIDEX.name(), null /* targetArchitecture */);
        context.addChangedFile(
                InstantRunBuildContext.FileType.DEX, new File("/tmp/dependencies.dex"));
        context.addChangedFile(
                InstantRunBuildContext.FileType.DEX, new File("/tmp/dependencies.dex"));
        context.close();
        Build build = context.getPreviousBuilds().iterator().next();
        assertThat(build.getArtifacts()).hasSize(1);
        assertThat(build.getArtifacts().get(0).getType()).isEqualTo(
                InstantRunBuildContext.FileType.DEX);
    }

    @Test
    public void testLoadingFromCleanState()
            throws ParserConfigurationException, SAXException, IOException {
        InstantRunBuildContext instantRunBuildContext = new InstantRunBuildContext();
        File file = new File("/path/to/non/existing/file");
        instantRunBuildContext.loadFromXmlFile(file);
        assertThat(instantRunBuildContext.getBuildId()).isAtLeast(1L);
    }

    @Test
    public void testLoadingFromPreviousState()
            throws IOException, ParserConfigurationException, SAXException {
        File tmpFile = createMarkedBuildInfo();

        InstantRunBuildContext newContext = new InstantRunBuildContext();
        newContext.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetArchitecture */);

        newContext.loadFromXmlFile(tmpFile);
        String xml = newContext.toXml();
        assertThat(xml).contains(InstantRunBuildContext.ATTR_TIMESTAMP);
    }

    @Test
    public void testPersistingAndLoadingPastBuilds()
            throws IOException, ParserConfigurationException, SAXException {
        InstantRunBuildContext instantRunBuildContext = new InstantRunBuildContext();
        instantRunBuildContext.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetAbi */);
        instantRunBuildContext.setSecretToken(12345L);
        File buildInfo = createBuildInfo(instantRunBuildContext);
        instantRunBuildContext = new InstantRunBuildContext();
        instantRunBuildContext.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetAbi */);
        instantRunBuildContext.loadFromXmlFile(buildInfo);
        assertThat(instantRunBuildContext.getPreviousBuilds()).hasSize(1);
        saveBuildInfo(instantRunBuildContext, buildInfo);

        instantRunBuildContext = new InstantRunBuildContext();
        instantRunBuildContext.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetAbi */);
        instantRunBuildContext.loadFromXmlFile(buildInfo);
        assertThat(instantRunBuildContext.getSecretToken()).isEqualTo(12345L);
        assertThat(instantRunBuildContext.getPreviousBuilds()).hasSize(2);
    }

    @Test
    public void testXmlFormat() throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext first = new InstantRunBuildContext();
        first.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetArchitecture */);
        first.setDensity("xxxhdpi");
        first.addChangedFile(InstantRunBuildContext.FileType.MAIN, new File("main.apk"));
        first.addChangedFile(InstantRunBuildContext.FileType.SPLIT, new File("split.apk"));
        String buildInfo = first.toXml();

        InstantRunBuildContext second = new InstantRunBuildContext();
        second.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetArchitecture */);
        second.setDensity("xhdpi");
        second.loadFromXml(buildInfo);
        second.addChangedFile(InstantRunBuildContext.FileType.SPLIT, new File("other.apk"));
        second.addChangedFile(InstantRunBuildContext.FileType.RELOAD_DEX, new File("reload.dex"));
        buildInfo = second.toXml();

        Document document = XmlUtils.parseDocument(buildInfo, false);
        Element instantRun = (Element) document.getFirstChild();
        assertThat(instantRun.getTagName()).isEqualTo("instant-run");
        assertThat(instantRun.getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP)).isEqualTo(
                String.valueOf(second.getBuildId()));
        assertThat(instantRun.getAttribute(InstantRunBuildContext.ATTR_DENSITY)).isEqualTo("xhdpi");

        // check the most recent build (called second) records :
        List<Element> secondArtifacts = getElementsByName(instantRun,
                InstantRunBuildContext.TAG_ARTIFACT);
        assertThat(secondArtifacts).hasSize(2);
        assertThat(secondArtifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_TYPE))
                .isEqualTo("SPLIT");
        assertThat(secondArtifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .endsWith("other.apk");
        assertThat(secondArtifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_TYPE))
                .isEqualTo("RELOAD_DEX");
        assertThat(secondArtifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .endsWith("reload.dex");

        boolean foundFirst = false;
        NodeList childNodes = instantRun.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (item.getNodeName().equals(InstantRunBuildContext.TAG_BUILD)) {
                // there should be one build child with first build references.
                foundFirst = true;
                assertThat(((Element) item).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                        .isEqualTo(
                                String.valueOf(first.getBuildId()));
                List<Element> firstArtifacts = getElementsByName(item,
                        InstantRunBuildContext.TAG_ARTIFACT);
                assertThat(firstArtifacts).hasSize(2);
                assertThat(firstArtifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_TYPE))
                        .isEqualTo("SPLIT_MAIN");
                assertThat(firstArtifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                        .endsWith("main.apk");
                assertThat(firstArtifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_TYPE))
                        .isEqualTo("SPLIT");
                assertThat(firstArtifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                        .endsWith("split.apk");
            }
        }
        assertThat(foundFirst).isTrue();
    }

    @Test
    public void testArtifactsPersistence()
            throws IOException, ParserConfigurationException, SAXException {
        InstantRunBuildContext instantRunBuildContext = new InstantRunBuildContext();
        instantRunBuildContext.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetAbi */);
        instantRunBuildContext.addChangedFile(InstantRunBuildContext.FileType.MAIN,
                new File("main.apk"));
        instantRunBuildContext.addChangedFile(InstantRunBuildContext.FileType.SPLIT,
                new File("split.apk"));
        String buildInfo = instantRunBuildContext.toXml();

        // check xml format, the IDE depends on it.
        instantRunBuildContext = new InstantRunBuildContext();
        instantRunBuildContext.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetAbi */);
        instantRunBuildContext.loadFromXml(buildInfo);
        assertThat(instantRunBuildContext.getPreviousBuilds()).hasSize(1);
        Build build = instantRunBuildContext.getPreviousBuilds().iterator().next();

        assertThat(build.getArtifacts()).hasSize(2);
        assertThat(build.getArtifacts().get(0).getType()).isEqualTo(
                InstantRunBuildContext.FileType.SPLIT_MAIN);
        assertThat(build.getArtifacts().get(1).getType()).isEqualTo(
                InstantRunBuildContext.FileType.SPLIT);
    }

    @Test
    public void testOldReloadPurge()
            throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext initial = new InstantRunBuildContext();
        initial.setApiLevel(23, null /* coldswapMode */, null /* targetArchitecture */);
        initial.addChangedFile(InstantRunBuildContext.FileType.SPLIT, new File("/tmp/split-0.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        InstantRunBuildContext first = new InstantRunBuildContext();
        first.setApiLevel(23, null /* coldswapMode */, null /* targetArchitecture */);
        first.loadFromXml(buildInfo);
        first.addChangedFile(InstantRunBuildContext.FileType.RELOAD_DEX,
                new File("reload.dex"));
        first.setVerifierResult(InstantRunVerifierStatus.COMPATIBLE);
        first.close();
        buildInfo = first.toXml();

        InstantRunBuildContext second = new InstantRunBuildContext();
        second.setApiLevel(23, null /* coldswapMode */, null /* targetArchitecture */);
        second.loadFromXml(buildInfo);
        second.addChangedFile(InstantRunBuildContext.FileType.SPLIT, new File("split.apk"));
        second.setVerifierResult(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);

        second.close();
        buildInfo = second.toXml();
        Document document = XmlUtils.parseDocument(buildInfo, false /* namespaceAware */);

        List<Element> builds = getElementsByName(document.getFirstChild(),
                InstantRunBuildContext.TAG_BUILD);
        // initial is never purged.
        assertThat(builds).hasSize(2);
        assertThat(builds.get(1).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP)).isEqualTo(
                String.valueOf(second.getBuildId()));
    }

    @Test
    public void testMultipleReloadCollapse()
            throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext initial = new InstantRunBuildContext();
        initial.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetArchitecture */);
        initial.addChangedFile(InstantRunBuildContext.FileType.SPLIT, new File("/tmp/split-0.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        InstantRunBuildContext first = new InstantRunBuildContext();
        first.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetArchitecture */);
        first.loadFromXml(buildInfo);
        first.addChangedFile(InstantRunBuildContext.FileType.RELOAD_DEX,
                new File("reload.dex"));
        first.setVerifierResult(InstantRunVerifierStatus.COMPATIBLE);
        first.close();
        buildInfo = first.toXml();

        InstantRunBuildContext second = new InstantRunBuildContext();
        second.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetArchitecture */);
        second.loadFromXml(buildInfo);
        second.addChangedFile(InstantRunBuildContext.FileType.SPLIT, new File("split.apk"));
        second.setVerifierResult(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);

        second.close();
        buildInfo = second.toXml();

        InstantRunBuildContext third = new InstantRunBuildContext();
        third.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetArchitecture */);
        third.loadFromXml(buildInfo);
        third.addChangedFile(InstantRunBuildContext.FileType.RESOURCES,
                new File("resources-debug.ap_"));
        third.addChangedFile(InstantRunBuildContext.FileType.RELOAD_DEX, new File("reload.dex"));
        third.setVerifierResult(InstantRunVerifierStatus.COMPATIBLE);

        third.close();
        buildInfo = third.toXml();

        InstantRunBuildContext fourth = new InstantRunBuildContext();
        fourth.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetArchitecture */);
        fourth.loadFromXml(buildInfo);
        fourth.addChangedFile(InstantRunBuildContext.FileType.RESOURCES,
                new File("resources-debug.ap_"));
        fourth.setVerifierResult(InstantRunVerifierStatus.COMPATIBLE);
        fourth.close();
        buildInfo = fourth.toXml();

        Document document = XmlUtils.parseDocument(buildInfo, false /* namespaceAware */);

        List<Element> builds = getElementsByName(document.getFirstChild(),
                InstantRunBuildContext.TAG_BUILD);
        // first build should have been removed due to the coldswap presence.
        assertThat(builds).hasSize(4);
        assertThat(builds.get(1).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP)).isEqualTo(
                String.valueOf(second.getBuildId()));
        assertThat(builds.get(2).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP)).isEqualTo(
                String.valueOf(third.getBuildId()));
        assertThat(getElementsByName(builds.get(2), InstantRunBuildContext.TAG_ARTIFACT))
                .named("Superseded resources.ap_ artifact should be removed.")
                .hasSize(1);

    }

    @Test
    public void testOverlappingAndEmptyChanges()
            throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext initial = new InstantRunBuildContext();
        initial.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetArchitecture */);
        initial.addChangedFile(InstantRunBuildContext.FileType.MAIN, new File("/tmp/main.apk"));
        initial.addChangedFile(InstantRunBuildContext.FileType.SPLIT, new File("/tmp/split-0.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        InstantRunBuildContext first = new InstantRunBuildContext();
        first.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetArchitecture */);
        first.loadFromXml(buildInfo);
        first.addChangedFile(InstantRunBuildContext.FileType.SPLIT, new File("/tmp/split-1.apk"));
        first.addChangedFile(InstantRunBuildContext.FileType.SPLIT, new File("/tmp/split-2.apk"));
        first.setVerifierResult(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);
        first.close();
        buildInfo = first.toXml();

        InstantRunBuildContext second = new InstantRunBuildContext();
        second.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetArchitecture */);
        second.loadFromXml(buildInfo);
        second.addChangedFile(InstantRunBuildContext.FileType.SPLIT, new File("/tmp/split-2.apk"));
        second.setVerifierResult(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);
        second.close();
        buildInfo = second.toXml();

        InstantRunBuildContext third = new InstantRunBuildContext();
        third.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetArchitecture */);
        third.loadFromXml(buildInfo);
        third.addChangedFile(InstantRunBuildContext.FileType.SPLIT, new File("/tmp/split-2.apk"));
        third.addChangedFile(InstantRunBuildContext.FileType.SPLIT, new File("/tmp/split-3.apk"));
        third.setVerifierResult(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);

        third.close();
        buildInfo = third.toXml();

        Document document = XmlUtils.parseDocument(buildInfo, false /* namespaceAware */);

        List<Element> builds = getElementsByName(document.getFirstChild(),
                InstantRunBuildContext.TAG_BUILD);
        // initial builds are never removed.
        assertThat(builds).hasSize(3);
        assertThat(builds.get(0).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP)).isEqualTo(
                String.valueOf(initial.getBuildId()));
        List<Element> artifacts = getElementsByName(builds.get(0),
                InstantRunBuildContext.TAG_ARTIFACT);
        assertThat(artifacts).hasSize(2);
        // split-2 changes on first build is overlapped by third change.
        assertThat(artifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .isEqualTo(new File("/tmp/main.apk").getAbsolutePath());
        assertThat(artifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .isEqualTo(new File("/tmp/split-0.apk").getAbsolutePath());

        assertThat(builds.get(1).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP)).isEqualTo(
                String.valueOf(first.getBuildId()));
        artifacts = getElementsByName(builds.get(0),
                InstantRunBuildContext.TAG_ARTIFACT);
        assertThat(artifacts).hasSize(2);
        // split-2 changes on first build is overlapped by third change.
        assertThat(artifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .isEqualTo(new File("/tmp/main.apk").getAbsolutePath());
        assertThat(artifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .isEqualTo(new File("/tmp/split-0.apk").getAbsolutePath());

        // second is removed.

        // third has not only split-main remaining.
        assertThat(builds.get(2).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP)).isEqualTo(
                String.valueOf(third.getBuildId()));
        artifacts = getElementsByName(builds.get(2), InstantRunBuildContext.TAG_ARTIFACT);
        assertThat(artifacts).hasSize(2);
        // split-2 changes on first build is overlapped by third change.
        assertThat(artifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .isEqualTo(new File("/tmp/split-2.apk").getAbsolutePath());
        assertThat(artifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .isEqualTo(new File("/tmp/split-3.apk").getAbsolutePath());
    }

    @Test
    public void testTemporaryBuildProduction()
            throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext initial = new InstantRunBuildContext();
        initial.setApiLevel(21, ColdswapMode.MULTIDEX.name(), null /* targetArchitecture */);
        initial.addChangedFile(InstantRunBuildContext.FileType.DEX, new File("/tmp/split-1.apk"));
        initial.addChangedFile(InstantRunBuildContext.FileType.DEX, new File("/tmp/split-2.apk"));
        String buildInfo = initial.toXml();

        InstantRunBuildContext first = new InstantRunBuildContext();
        first.setApiLevel(21, null /* coldswapMode */, null /* targetArchitecture */);
        first.loadFromXml(buildInfo);
        first.addChangedFile(InstantRunBuildContext.FileType.RESOURCES, new File("/tmp/resources_ap"));
        first.close();
        String tmpBuildInfo = first.toXml(InstantRunBuildContext.PersistenceMode.TEMP_BUILD);

        InstantRunBuildContext fixed = new InstantRunBuildContext();
        fixed.setApiLevel(21, ColdswapMode.MULTIDEX.name(), null /* targetArchitecture */);
        fixed.loadFromXml(buildInfo);
        fixed.mergeFrom(tmpBuildInfo);
        fixed.addChangedFile(InstantRunBuildContext.FileType.DEX, new File("/tmp/split-1.apk"));
        fixed.close();
        buildInfo = fixed.toXml();

        // now check we only have 2 builds...
        Document document = XmlUtils.parseDocument(buildInfo, false /* namespaceAware */);
        List<Element> builds = getElementsByName(document.getFirstChild(),
                InstantRunBuildContext.TAG_BUILD);
        // initial builds are never removed.
        // first build should have been removed due to the coldswap presence.
        assertThat(builds).hasSize(2);
        List<Element> artifacts = getElementsByName(builds.get(1),
                InstantRunBuildContext.TAG_ARTIFACT);
        assertThat(artifacts).hasSize(2);
    }


    @Test
    public void testX86InjectedArchitecture() {

        InstantRunBuildContext context = new InstantRunBuildContext();
        context.setApiLevel(20, null /* coldswapMode */, "x86");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.PRE_LOLLIPOP);

        context.setApiLevel(21, null /* coldswapMode */, "x86");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_DEX);

        context.setApiLevel(23, null /* coldswapMode */, "x86");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_DEX);

        context.setApiLevel(21, ColdswapMode.MULTIDEX.name(), "x86");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_DEX);

        context.setApiLevel(23, ColdswapMode.MULTIDEX.name(), "x86");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_DEX);

        context.setApiLevel(21, ColdswapMode.MULTIAPK.name(), "x86");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_APK);

        context.setApiLevel(23, ColdswapMode.MULTIAPK.name(), "x86");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_APK);
    }

    @Test
    public void testResourceRemovalWhenBuildingMainApp() throws Exception {
        InstantRunBuildContext context = new InstantRunBuildContext();
        context.setApiLevel(19,
                ColdswapMode.MULTIDEX.name(), null /* targetArchitecture */);

        context.addChangedFile(InstantRunBuildContext.FileType.RESOURCES, new File("res.ap_"));
        String tempXml = context.toXml(InstantRunBuildContext.PersistenceMode.TEMP_BUILD);
        context.addChangedFile(InstantRunBuildContext.FileType.MAIN, new File("debug.apk"));
        context.loadFromXml(tempXml);
        context.close();

        assertNotNull(context.getLastBuild());
        assertThat(context.getLastBuild().getArtifacts()).hasSize(1);
        assertThat(Iterables.getOnlyElement(context.getLastBuild().getArtifacts()).getType())
                .isEqualTo(InstantRunBuildContext.FileType.MAIN);

    }


    private void testArmInjectedArchitecture() {
        InstantRunBuildContext context = new InstantRunBuildContext();
        context.setApiLevel(20, null /* coldswapMode */, "arm");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.PRE_LOLLIPOP);

        context.setApiLevel(21, null /* coldswapMode */, "arm");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_APK);

        context.setApiLevel(23, null /* coldswapMode */, "arm");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_APK);

        context.setApiLevel(21, ColdswapMode.MULTIAPK.name(), "arm");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_APK);

        context.setApiLevel(23, ColdswapMode.MULTIAPK.name(), "arm");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_APK);

        context.setApiLevel(21, ColdswapMode.MULTIDEX.name(), "arm");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_DEX);

        context.setApiLevel(23, ColdswapMode.MULTIDEX.name(), "arm");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_DEX);
    }

    private static List<Element> getElementsByName(Node parent, String nodeName) {
        ImmutableList.Builder<Element> builder = ImmutableList.builder();
        NodeList childNodes = parent.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (item instanceof Element && item.getNodeName().equals(nodeName)) {
                builder.add((Element) item);
            }
        }
        return builder.build();
    }

    private static File createMarkedBuildInfo() throws IOException, ParserConfigurationException {
        InstantRunBuildContext originalContext = new InstantRunBuildContext();
        originalContext.setApiLevel(23, ColdswapMode.MULTIAPK.name(), null /* targetAbi */);
        return createBuildInfo(originalContext);
    }

    private static File createBuildInfo(InstantRunBuildContext context)
            throws IOException, ParserConfigurationException {
        File tmpFile = File.createTempFile("InstantRunBuildContext", "tmp");
        saveBuildInfo(context, tmpFile);
        tmpFile.deleteOnExit();
        return tmpFile;
    }

    private static void saveBuildInfo(InstantRunBuildContext context, File buildInfo)
            throws IOException, ParserConfigurationException {
        String xml = context.toXml();
        Files.write(xml, buildInfo, Charsets.UTF_8);
    }
}
