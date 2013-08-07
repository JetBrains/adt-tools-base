/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.buildsrc

import com.google.common.base.Charsets
import com.google.common.collect.Sets
import com.google.common.hash.HashCode
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.common.io.Files
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.result.*
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.tasks.TaskAction

class ArtifactDownloader {

    private static final String URL_MAVEN_CENTRAL = "http://repo1.maven.org/maven2"

    private static final String MAVEN_METADATA_XML = "maven-metadata.xml"
    private static final String DOT_MD5 = ".md5"
    private static final String DOT_SHA1 = ".sha1"
    private static final String DOT_POM = ".pom"
    private static final String DOT_JAR = ".jar"
    private static final String SOURCES_JAR = "-sources.jar"

    Project project

    File mainRepo
    File secondaryRepo

    ArtifactDownloader(Project project, File mainRepo, File secondaryRepo) {
        this.project = project
        this.mainRepo = mainRepo
        this.secondaryRepo = secondaryRepo
    }

    public void downloadArtifacts() {

        Set<ModuleVersionIdentifier> mainList = Sets.newHashSet()
        Set<ModuleVersionIdentifier> secondaryList = Sets.newHashSet()
        Set<ModuleVersionIdentifier> gradleRepoList = Sets.newHashSet()

        // gather the main and secondary dependencies for all the sub-projects.
        for (Project subProject : project.allprojects) {
            ResolutionResult resolutionResult
            try {
                resolutionResult = subProject.configurations.getByName("compile")
                        .getIncoming().getResolutionResult()
                // if the sub project doesn't ship then we put it's main dependencies in
                // the secondary list.
                buildArtifactList(resolutionResult.getRoot(),
                        subProject.shipping.isShipping ? mainList : secondaryList)
            } catch (UnknownDomainObjectException ignored) {
                // ignore
            }

            try {
                resolutionResult = subProject.configurations.getByName("testCompile")
                        .getIncoming().getResolutionResult()
                buildArtifactList(resolutionResult.getRoot(), secondaryList)
            } catch (UnknownDomainObjectException ignored) {
                // ignore
            }

            try {
                resolutionResult = subProject.configurations.getByName("testRuntime")
                        .getIncoming().getResolutionResult()
                buildArtifactList(resolutionResult.getRoot(), secondaryList)
            } catch (UnknownDomainObjectException ignored) {
                // ignore
            }

            // provided is for artifacts that we need to get from MavenCentral but that
            // are not copied through pushDistribution (because CopyDependenciesTask only
            // look at "compile".
            try {
                resolutionResult = subProject.configurations.getByName("provided")
                        .getIncoming().getResolutionResult()
                buildArtifactList(resolutionResult.getRoot(), secondaryList)
            } catch (UnknownDomainObjectException ignored) {
                // ignore
            }

            // manually add some artifacts that aren't detected because they are added dynamically
            // when their task run.
            try {
                resolutionResult = subProject.configurations.getByName("hidden")
                        .getIncoming().getResolutionResult()
                buildArtifactList(resolutionResult.getRoot(), secondaryList)
            } catch (UnknownDomainObjectException ignored) {
                // ignore
            }

            // Download some artifact from the gradle repo.
            try {
                resolutionResult = subProject.configurations.getByName("gradleRepo")
                        .getIncoming().getResolutionResult()
                buildArtifactList(resolutionResult.getRoot(), gradleRepoList)
            } catch (UnknownDomainObjectException ignored) {
                // ignore
            }
        }

        String[] repoUrls = [ URL_MAVEN_CENTRAL, CloneArtifactsPlugin.GRADLE_RELEASES_REPO,
                CloneArtifactsPlugin.GRADLE_SNAPSHOT_REPO ]

        try {
            Set<ModuleVersionIdentifier> downloadedSet = Sets.newHashSet()
            for (ModuleVersionIdentifier id : mainList) {
                pullArtifact(repoUrls, id, mainRepo, downloadedSet)
            }

            for (ModuleVersionIdentifier id : secondaryList) {
                pullArtifact(repoUrls, id, secondaryRepo, downloadedSet)
            }

            for (ModuleVersionIdentifier id : gradleRepoList) {
                pullArtifact(repoUrls, id, secondaryRepo, downloadedSet)
            }
        } catch (IOException e) {
            e.printStackTrace()
        }
    }

    protected void buildArtifactList(ResolvedModuleVersionResult module,
                                   Set<ModuleVersionIdentifier> list) {
        list.add(module.id)

        for (DependencyResult d : module.getDependencies()) {
            if (d instanceof UnresolvedDependencyResult) {
                UnresolvedDependencyResult dependency = (UnresolvedDependencyResult) d
                ModuleVersionSelector attempted = dependency.getAttempted()
                ModuleVersionIdentifier id = DefaultModuleVersionIdentifier.newId(
                        attempted.getGroup(), attempted.getName(), attempted.getVersion())
                list.add(id)
            } else {
                buildArtifactList(((ResolvedDependencyResult) d).getSelected(), list)
            }
        }
    }

    private void pullArtifact(String[] repoUrls, ModuleVersionIdentifier artifact,
                              File rootDestination, Set<ModuleVersionIdentifier> downloadedSet) throws IOException {
        // ignore all android artifacts and already downloaded artifacts
        if (artifact.group.startsWith("com.android") || BaseTask.isLocalArtifact(artifact)) {
            return
        }

        if (downloadedSet.contains(artifact)) {
            System.out.println("DUPLCTE " + artifact)
            return
        }

        downloadedSet.add(artifact)

        String folder = getFolder(artifact)

        // download the artifact metadata file.
        String repoUrl = tryToDownloadFile(repoUrls, folder, MAVEN_METADATA_XML, rootDestination,
                true, false)

        // move to the folder of the required version
        folder = folder + "/" + artifact.getVersion()

        // create name base
        String baseName = artifact.getName() + "-" + artifact.getVersion()

        // download the pom
        File pomFile = downloadFile(repoUrl ,folder, baseName + DOT_POM, rootDestination,
                false, true)

        // read the pom to figure out parents, relocation and packaging
        if (!handlePom(repoUrls, pomFile, rootDestination, downloadedSet)) {
            // pom said there's no jar to download: abort
            return
        }

        // download the jar artifact
        downloadFile(repoUrl, folder, baseName + DOT_JAR, rootDestination, false, false)

        // download the source if available
        try {
            downloadFile(repoUrl, folder, baseName + SOURCES_JAR, rootDestination, false, false)
        } catch (IOException ignored) {
            // ignore if missing
        }
    }

    private static String getFolder(ModuleVersionIdentifier artifact) {
        return artifact.getGroup().replaceAll("\\.", "/") + "/" + artifact.getName()

    }

    private String tryToDownloadFile(String[] repoUrls, String folder,
                                            String name, File rootDestination,
                                            boolean force, boolean printDownload) throws IOException {
        for (String repoUrl : repoUrls) {
            try {
                downloadFile(repoUrl, folder, name, rootDestination, force, printDownload)
                return repoUrl
            } catch (IOException ignored) {
                // ignore
            }
        }

        // if we get here, the file was not found in any repo.
        throw new IOException(String.format("Failed to find %s/%s in any repo", folder, name))
    }

    private File downloadFile(String repoUrl, String folder, String name, File rootDestination,
                              boolean force, boolean printDownload) throws IOException {
        File destinationFolder = new File(rootDestination, folder)
        destinationFolder.mkdirs()

        URL fileURL = new URL(repoUrl + "/" + folder + "/" + name)
        File destinationFile = new File(destinationFolder, name)

        if (force || !destinationFile.isFile()) {
            if (printDownload) {
                System.out.println("DWNLOAD " + destinationFile.absolutePath)
            }
            FileUtils.copyURLToFile(fileURL, destinationFile)

            // get the checksums
            URL md5URL = new URL(repoUrl + "/" + folder + "/" + name + DOT_MD5)
            File md5File = new File(destinationFolder, name + DOT_MD5)
            FileUtils.copyURLToFile(md5URL, md5File)

            checksum(destinationFile, md5File, Hashing.md5())

            URL sha15URL = new URL(repoUrl + "/" + folder + "/" + name + DOT_SHA1)
            File sha1File = new File(destinationFolder, name + DOT_SHA1)
            FileUtils.copyURLToFile(sha15URL, sha1File)

            checksum(destinationFile, sha1File, Hashing.sha1())
        } else if (printDownload) {
            System.out.println("SKIPPED " + destinationFile.absolutePath)
        }

        return destinationFile
    }

    /**
     * Handles a pom and return true if there is a jar package to download.
     *
     * @param pomFile the pom file
     * @param rootDestination where the download happens, in case parent pom must be downloaded.
     *
     * @return true if jar packaging must be downloaded
     */
    private boolean handlePom(String[] repoUrls, File pomFile, File rootDestination,
                              Set<ModuleVersionIdentifier> downloadedSet) {
        PomHandler pomHandler = new PomHandler(pomFile)

        ModuleVersionIdentifier relocation = pomHandler.getRelocation()
        if (relocation != null) {
            pullArtifact(repoUrls, relocation, rootDestination, downloadedSet)
            return false
        }

        ModuleVersionIdentifier parentPom = pomHandler.getParentPom()
        if (parentPom != null) {
            pullArtifact(repoUrls, parentPom, rootDestination, downloadedSet)
        }

        String packaging = pomHandler.getPackaging()

        // default packaging is jar so missing data is ok.
        return packaging == null || "jar".equals(packaging) || "bundle".equals(packaging)
    }

    private void checksum(File file, File checksumFile, HashFunction hashFunction)
            throws IOException {
        // get the checksum value
        List<String> lines = Files.readLines(checksumFile, Charsets.UTF_8)
        if (lines.isEmpty()) {
            throw new IOException("Empty file: " + checksumFile)
        }

        // read the first line only.
        String checksum = lines.get(0).trim()
        // it is possible that the line also contains the file for which this checksum applies:
        // <checksum> <file>
        // remove it
        int pos = checksum.indexOf(' ')
        if (pos != -1) {
            checksum = checksum.substring(0, pos)
        }

        // hash the file.
        HashCode hashCode = Files.asByteSource(file).hash(hashFunction)
        String hashCodeString = hashCode.toString()

        if (!checksum.equals(hashCodeString)) {
            project.logger.warn(String.format(
                    "Wrong checksum!\n\t%s computed for %s\n\t%s read from %s",
                hashCodeString, file,
                checksum, checksumFile))
        }
    }
}
