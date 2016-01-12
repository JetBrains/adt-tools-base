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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.SecondaryInput;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.tasks.ColdswapArtifactsKickerTask;
import com.android.build.gradle.tasks.MarkerFile;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.gradle.api.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Transform that slices the project's classes into approximately 12 slices.
 * <ul>10 slices for {@link Scope#PROJECT} and {@link Scope#SUB_PROJECTS}</ul>
 * <ul>1 slice for all the remaining classes provided as directories</ul>
 * <ul>1 slice for all the remaining classes provided as jars</ul>
 *
 * The last slice in the list above will be a jar file slice while the eleven remaining ones are
 * directory based slices.
 *
 *
 */
public class InstantRunSlicer extends Transform {

    // somehow a number based on experimentation, to make the slices not too big, not too many.
    public static final int NUMBER_OF_SLICES_FOR_PROJECT_CLASSES = 10;

    private final ILogger logger;

    private final File instantRunSupportDir;

    @NonNull
    private final VariantScope variantScope;

    public InstantRunSlicer(@NonNull Logger logger, @NonNull VariantScope variantScope) {
        this.logger = new LoggerWrapper(logger);
        this.variantScope = variantScope;
        this.instantRunSupportDir = variantScope.getInstantRunSupportDir();
    }

    @NonNull
    @Override
    public String getName() {
        return "instantRunSlicer";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(TransformInvocation transformInvocation)
            throws IOException, TransformException, InterruptedException {

        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        boolean isIncremental = transformInvocation.isIncremental();
        Collection<TransformInput> inputs = transformInvocation.getInputs();
        if (outputProvider == null) {
            logger.error(null /* throwable */, "null TransformOutputProvider for InstantRunSlicer");
            return;
        }

        // if we are blocked, do not proceed with execution.
        if (MarkerFile.Command.BLOCK == MarkerFile.readMarkerFile(
                ColdswapArtifactsKickerTask.ConfigAction.getMarkerFile(variantScope))) {
            return;
        }

        if (isIncremental) {
            sliceIncrementally(inputs, outputProvider);
        } else {
            slice(inputs, outputProvider);
            combineAllJars(inputs, outputProvider);
        }
    }

    /**
     * Combine all {@link Scope#PROJECT} and {@link Scope#SUB_PROJECTS} inputs into slices, ignore
     * all other inputs.
     *
     * @param inputs the transform's input
     * @param outputProvider the transform's output provider to create streams
     * @throws IOException if the files cannot be copied
     * @throws TransformException never thrown
     * @throws InterruptedException never thrown.
     */
    private void slice(@NonNull Collection<TransformInput> inputs,
            @NonNull TransformOutputProvider outputProvider)
            throws IOException, TransformException, InterruptedException {

        // sort all of our input classes per package.
        Packages packages = new Packages();

        File dependenciesLocation =
                getDependenciesSliceOutputFolder(outputProvider, Format.DIRECTORY);

        // first path, gather all input files, organize per package.
        for (TransformInput input : inputs) {
            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {

                File inputDir = directoryInput.getFile();
                FluentIterable<File> files = Files.fileTreeTraverser()
                        .breadthFirstTraversal(directoryInput.getFile());

                for (File file : files) {
                    if (file.isDirectory()) {
                        continue;
                    }
                    String packagePath = FileUtils
                            .relativePossiblyNonExistingPath(file.getParentFile(), inputDir);

                    if (directoryInput.getScopes().contains(Scope.PROJECT) ||
                            directoryInput.getScopes().contains(Scope.SUB_PROJECTS)) {

                        packages.add(packagePath, file);
                    } else {
                        // dump in a single directory that may end up producing several .dex in case
                        // we are over 65K limit.
                        File outputFile = new File(dependenciesLocation,
                                new File(packagePath, file.getName()).getPath());
                        Files.copy(file, outputFile);
                    }
                }
            }
        }

        // now produces the output streams for each slice.
        Slices slices = packages.toSlices();
        slices.writeTo(outputProvider);

        // and save the metadata necessary for incremental support so we know if which slice
        // classes belong to.
        try {
            Files.write(slices.toXml(),
                    new File(instantRunSupportDir, "slices.xml"), Charsets.UTF_8);
        } catch (ParserConfigurationException e) {
            throw new TransformException(e);
        }
    }

    /**
     * Combine all input jars with a different scope than {@link Scope#PROJECT} and
     * {@link Scope#SUB_PROJECTS}
     * @param inputs the transform's input
     * @param outputProvider the transform's output provider to create streams
     * @throws IOException if jar files cannot be created successfully
     */
    private void combineAllJars(@NonNull Collection<TransformInput> inputs,
            @NonNull TransformOutputProvider outputProvider) throws IOException {

        List<JarInput> jarFilesToProcess = new ArrayList<JarInput>();
        for (TransformInput input : inputs) {
            for (JarInput jarInput : input.getJarInputs()) {
                if (jarInput.getFile().exists()) {
                    jarFilesToProcess.add(jarInput);
                }
            }
        }
        if (jarFilesToProcess.isEmpty()) {
            return;
        }

        // all the jar files will be combined into a single jar will all other scopes.
        File dependenciesJar = getDependenciesSliceOutputFolder(outputProvider, Format.JAR);
        Set<String> entries = new HashSet<String>();

        Files.createParentDirs(dependenciesJar);
        JarOutputStream jarOutputStream = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(dependenciesJar)));
        try {
            for (JarInput jarInput : jarFilesToProcess) {
                mergeJarInto(jarInput, entries, jarOutputStream);
            }
        } finally {
            jarOutputStream.close();
        }
    }

    private void mergeJarInto(@NonNull JarInput jarInput,
            @NonNull Set<String> entries, @NonNull JarOutputStream dependenciesJar)
            throws IOException {

        JarFile jarFile = new JarFile(jarInput.getFile());
        try {
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                if (jarEntry.isDirectory()) {
                    continue;
                }
                if (entries.contains(jarEntry.getName())) {
                    logger.verbose(
                            String.format("Entry %1$s is duplicated, ignore the one from %2$s",
                                    jarEntry.getName(), jarInput.getName()));
                } else {
                    dependenciesJar.putNextEntry(jarEntry);
                    InputStream inputStream = jarFile.getInputStream(jarEntry);
                    try {
                        ByteStreams.copy(inputStream, dependenciesJar);
                    } finally {
                        inputStream.close();
                    }
                    dependenciesJar.closeEntry();
                    entries.add(jarEntry.getName());
                }
            }
        } finally {
            jarFile.close();
        }
    }

    private void sliceIncrementally(@NonNull Collection<TransformInput> inputs,
            @NonNull TransformOutputProvider outputProvider)
            throws IOException, TransformException, InterruptedException {

        processChangesSinceLastRestart(inputs, outputProvider);

        // in any case, we always process jar input changes incrementally.
        for (TransformInput input : inputs) {
            for (JarInput jarInput : input.getJarInputs()) {
                if (jarInput.getStatus() != Status.NOTCHANGED) {
                    // one jar has changed, was removed or added, re-merge all of our jars.
                    combineAllJars(inputs, outputProvider);
                    return;
                }
            }
        }
        logger.info("No jar merging necessary, all input jars unchanged");
    }

    @Nullable
    private DirectoryInput getInputFor(@NonNull Collection<TransformInput> inputs, File file) {
        for (TransformInput input : inputs) {
            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                if (file.getAbsolutePath().startsWith(directoryInput.getFile().getAbsolutePath())) {
                    return directoryInput;
                }
            }
        }
        return null;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        return ImmutableList.of(new SecondaryFile(
                ColdswapArtifactsKickerTask.ConfigAction.getMarkerFile(variantScope),
                true /* supportsIncrementalChange */));
    }

    private void processChangesSinceLastRestart(
            @NonNull final Collection<TransformInput> inputs,
            @NonNull final TransformOutputProvider outputProvider)
            throws TransformException, InterruptedException, IOException {

        // first read our slicing information so we can find out in which slice changed files belong
        // to.
        final SlicingInfo slicingInfo = new SlicingInfo();
        try {
            slicingInfo.readFrom(new File(instantRunSupportDir, "slices.xml"));
        } catch (Exception e) {
            logger.error(e, "Incremental slicing failed, cannot read slices.xml");
            slice(inputs, outputProvider);
            return;
        }

        File incrementalChangesFile = InstantRunBuildType.RESTART
                .getIncrementalChangesFile(variantScope);

        // process all files
        ChangeRecords.process(incrementalChangesFile,
                new ChangeRecords.RecordHandler() {
                    @Override
                    public void handle(String filePath, Status status)
                            throws IOException, TransformException {
                        // get the output stream for this file.
                        File fileToProcess = new File(filePath);
                        DirectoryInput directoryInput = getInputFor(inputs, fileToProcess);
                        if (directoryInput == null) {
                            logger.info("Cannot find input directory for " + filePath);
                            return;
                        }
                        File sliceOutputLocation = getOutputStreamForFile(
                                outputProvider, directoryInput, fileToProcess, slicingInfo);

                        String relativePath = FileUtils.relativePossiblyNonExistingPath(
                                fileToProcess, directoryInput.getFile());

                        File outputFile = new File(sliceOutputLocation, relativePath);
                        switch(status) {
                            case ADDED:
                            case CHANGED:
                                Files.createParentDirs(outputFile);
                                Files.copy(fileToProcess, outputFile);
                                break;
                            case REMOVED:
                                if (!outputFile.delete()) {
                                    throw new TransformException(
                                            String.format("Cannot delete file %1$s",
                                                    outputFile.getAbsolutePath()));
                                }
                                break;
                            default:
                                throw new TransformException("Unhandled status " + status);

                        }
                    }
                });

    }

    private static File getOutputStreamForFile(
            @NonNull TransformOutputProvider transformOutputProvider,
            @NonNull DirectoryInput input,
            @NonNull File file,
            @NonNull SlicingInfo slicingInfo) {

        String relativePath = FileUtils.relativePossiblyNonExistingPath(file, input.getFile());
        if (input.getScopes().contains(Scope.PROJECT)
                || input.getScopes().contains(Scope.SUB_PROJECTS)) {


            SlicingInfo.SliceInfo slice = slicingInfo.getSliceFor(relativePath);
            return transformOutputProvider.getContentLocation(slice.name,
                    TransformManager.CONTENT_CLASS,
                    Sets.immutableEnumSet(Scope.PROJECT, Scope.SUB_PROJECTS),
                    Format.DIRECTORY);
        } else {
            return getDependenciesSliceOutputFolder(transformOutputProvider, Format.DIRECTORY);
        }
    }

    @NonNull
    private static File getDependenciesSliceOutputFolder(
            @NonNull TransformOutputProvider outputProvider, @NonNull Format format) {
        String name = format == Format.DIRECTORY ? "dep-classes" : "dependencies";
        return outputProvider
                .getContentLocation(name, TransformManager.CONTENT_CLASS,
                        Sets.immutableEnumSet(Scope.EXTERNAL_LIBRARIES,
                                Scope.SUB_PROJECTS_LOCAL_DEPS,
                                Scope.PROJECT_LOCAL_DEPS), format);
    }

    private static class SlicingInfo {

        private static class SliceInfo {
            @NonNull
            private final String name;
            @NonNull
            private final String upperBound;

            private SliceInfo(@NonNull String name, @NonNull String upperBound) {
                this.name = name;
                this.upperBound = upperBound;
            }
        }

        @NonNull
        private final List<SliceInfo> slices = new ArrayList<SliceInfo>();

        private void readFrom(@NonNull  File file)
                throws IOException, ParserConfigurationException, SAXException {
            Document document = XmlUtils.parseUtfXmlFile(file, false);
            Node slicesNode = document.getFirstChild();
            if (!slicesNode.getNodeName().equals("slices")) {
                throw new IOException("invalid slices.xml file " + file.getAbsolutePath());
            }
            NodeList sliceNodes = slicesNode.getChildNodes();
            for (int i = 0; i < sliceNodes.getLength(); i++) {
                Node slice = sliceNodes.item(i);
                NamedNodeMap attributes = slice.getAttributes();
                SliceInfo sliceInfo = new SliceInfo(
                        attributes.getNamedItem("name").getNodeValue(),
                        attributes.getNamedItem("upper-bound").getNodeValue());
                slices.add(sliceInfo);
            }
        }

        /**
         * Returns the {@link SliceInfo} for a package name. We will use existing slices' upper
         * bounds to determine in which slice a new element falls into. This will ensure some sort
         * of distribution among slices of all the new elements.
         */
        public SliceInfo getSliceFor(@NonNull String relativePath) {
            Iterator<SliceInfo> sliceIterator = slices.iterator();
            SliceInfo currentSlice = sliceIterator.next();
            while (sliceIterator.hasNext() && currentSlice.upperBound.compareTo(relativePath) < 0) {
                currentSlice = sliceIterator.next();
            }
            // any new class which is above the last upper bound will still get automatically
            // packaged in the last slice.
            return currentSlice;
        }
    }

    private static class Slices {
        private final int sliceSize;
        @NonNull
        private final List<Slice> slices = new ArrayList<Slice>();
        @NonNull
        private Slice currentSlice;

        private Slices(int totalSize) {
            int nbOfElementsPerSlice = (totalSize / NUMBER_OF_SLICES_FOR_PROJECT_CLASSES ) + 1;
            this.sliceSize = nbOfElementsPerSlice < 1 ? 1 : nbOfElementsPerSlice;
            currentSlice = allocateSlice();
        }

        private void addElement(@NonNull String packagePath, @NonNull File file) {
            if (currentSlice.isFull()) {
                currentSlice = allocateSlice();
            }
            currentSlice.add(packagePath, file);
        }

        @NonNull
        private Slice allocateSlice() {
            Slice newSlice = new Slice("slice_" + slices.size(), sliceSize);
            slices.add(newSlice);
            return newSlice;
        }

        private void writeTo(@NonNull TransformOutputProvider outputProvider) throws IOException {
            for (Slice slice : slices) {
                slice.writeTo(outputProvider);
            }
        }

        /**
         * Serialize this context into an xml file.
         * @return the xml persisted information as a {@link String}
         * @throws ParserConfigurationException
         */
        @NonNull
        public String toXml() throws ParserConfigurationException {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element slicesElement = document.createElement("slices");
            for (Slice slice : slices) {
                slicesElement.appendChild(slice.toXml(document));
            }
            return XmlUtils.toXml(slicesElement);
        }
    }

    private static class Slice {

        private static class SlicedElement {
            @NonNull
            private final String packagePath;
            @NonNull
            private final File slicedFile;

            private SlicedElement(@NonNull String packagePath, @NonNull File slicedFile) {
                this.packagePath = packagePath;
                this.slicedFile = slicedFile;
            }

            @Override
            public String toString() {
                return packagePath + slicedFile.getName();
            }
        }

        @NonNull
        private final String name;
        private final int expectedSize;
        private final List<SlicedElement> slicedElements;

        private Slice(@NonNull String name, int expectedSize) {
            this.name = name;
            this.expectedSize = expectedSize;
            slicedElements = new ArrayList<SlicedElement>(expectedSize);
        }

        private void add(@NonNull String packagePath, @NonNull File file) {
            slicedElements.add(new SlicedElement(packagePath, file));
        }

        private boolean isFull() {
            return slicedElements.size() == expectedSize;
        }

        @NonNull
        public Element toXml(@NonNull Document document) {
            Element sliceElement = document.createElement("slice");
            sliceElement.setAttribute("name", name);
            sliceElement.setAttribute("upper-bound", getUpperBound());
            return sliceElement;
        }

        @NonNull
        private String getUpperBound() {
            SlicedElement sliceElement = slicedElements.get(slicedElements.size() -1);
            return sliceElement.toString();
        }

        private void writeTo(@NonNull TransformOutputProvider outputProvider) throws IOException {
            if (slicedElements.isEmpty()) {
                return;
            }
            File sliceOutputLocation = outputProvider.getContentLocation(name,
                    TransformManager.CONTENT_CLASS,
                    Sets.immutableEnumSet(Scope.PROJECT, Scope.SUB_PROJECTS),
                    Format.DIRECTORY);

            // now move all the files into its new location.
            for (Slice.SlicedElement slicedElement : slicedElements) {
                File outputFile = new File(sliceOutputLocation, new File(slicedElement.packagePath,
                        slicedElement.slicedFile.getName()).getPath());
                Files.createParentDirs(outputFile);
                Files.copy(slicedElement.slicedFile, outputFile);
            }
        }
    }

    private static class Packages {
        // we need a sorted Map, so that packages are added in their natural order in the slices.
        @NonNull
        private final Map<String, PackageRef> packagesRef = new TreeMap<String, PackageRef>();
        private int nbOfElements = 0;

        private void add(@NonNull String packagePath,@NonNull File file) {

            PackageRef packageRef = packagesRef.get(packagePath);
            if (packageRef == null) {
                packageRef = new PackageRef(packagePath);
                packagesRef.put(packagePath, packageRef);
            }
            packageRef.add(file);
            nbOfElements++;
        }

        @NonNull
        private Slices toSlices() {
            Slices slices = new Slices(nbOfElements);

            // package related classes will be added one after the other so they can be part of the
            // same slice as much as possible. This could be refined later to make more elaborate
            // slicing decisions.
            for (PackageRef packageRef : packagesRef.values()) {
                for (File packageFile : packageRef.packageFiles) {
                    slices.addElement(packageRef.packagePath, packageFile);
                }
            }
            return slices;
        }
    }

    private static class PackageRef {
        // package name with File.separatorChar instead of . between elements.
        @NonNull
        private final String packagePath;
        @NonNull
        private final Set<File> packageFiles = new TreeSet<File>(new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                // all files belong to the same package, just order them based on the file name.
                return o1.getName().compareTo(o2.getName());
            }
        });

        private PackageRef(@NonNull String packagePath) {
            this.packagePath = packagePath;
        }

        private void add(@NonNull File file) {
            packageFiles.add(file);
        }
    }
}
