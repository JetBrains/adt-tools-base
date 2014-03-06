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
import com.google.common.base.Joiner
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.google.common.io.Files
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.tasks.TaskAction

class GatherNoticesTask extends BaseTask {

    Project project

    File distributionDir

    File repoDir

    @TaskAction
    public void gatherNotices() {

        Set<ModuleVersionIdentifier> dependencyCache = Sets.newHashSet()

        File mainDir = getDistributionDir()
        File noticeDir = new File(mainDir, project.name)
        noticeDir.deleteDir()
        noticeDir.mkdirs()
        File repo = getRepoDir()

        // gather the notice files into the output folder
        for (Project subProject : project.subprojects) {

            if (!subProject.shipping.isShipping) {
                continue
            }

            File fromFile = new File(subProject.projectDir, "NOTICE")
            if (!fromFile.isFile()) {
                throw new GradleException("Missing NOTICE file: " + fromFile.absolutePath)
            }

            // copy to the noticeDir folder, adding a header
            File toFile = new File(noticeDir, "NOTICE_" + subProject.name + ".jar.txt")
            copyNoticeAndAddHeader(fromFile, toFile, subProject.name + ".jar")

            Configuration configuration = subProject.configurations.compile
            gatherFromConfiguration(configuration, dependencyCache, repo, noticeDir)
        }

        // merge all the files together.
        // First gather them so that they can be sorted before merging them. This will limit
        // the amount of diff in git when there is an update.
        File[] folders = mainDir.listFiles()
        Set<String> noticeCache = Sets.newTreeSet();
        List<File> notices = Lists.newArrayList();
        if (folders != null) {
            for (File folder : folders) {
                if (folder.isDirectory()) {
                    gatherNoticesFromFolder(folder, noticeCache, notices)
                }
            }
        }

        // sort the notices
        Collections.sort(notices);

        // merge and write the result
        File mainNoticeFile = new File(mainDir, "NOTICE.txt")
        BufferedWriter writer = Files.newWriter(mainNoticeFile, Charsets.UTF_8)
        mergeNotices(notices, writer);
        writer.close()
    }

    private static void gatherFromConfiguration(Configuration configuration,
                                                Set<ModuleVersionIdentifier> dependencyCache,
                                                File repo, File noticeDir) {
        File toFile, fromFile
        Set<ResolvedArtifact> artifacts = configuration.resolvedConfiguration.resolvedArtifacts

        System.out.println("")

        for (ResolvedArtifact artifact : artifacts) {
            System.out.print(" ${artifact.moduleVersion.id.toString()} ")
            // check it's not an android artifact or a local artifact
            if (isAndroidArtifact(artifact.moduleVersion.id)) {
                System.out.println("SKIPPED (android)")
            } else if (isLocalArtifact(artifact.moduleVersion.id)) {
                System.out.println("  SKIPPED (local)")
            } else if (!isValidArtifactType(artifact)) {
                System.out.println("  SKIPPED (type = ${artifact.type})")
            } else {
                ModuleVersionIdentifier id = artifact.moduleVersion.id
                // manually look for the NOTICE file in the repo
                if (!dependencyCache.contains(id)) {
                    dependencyCache.add(id)

                    fromFile = new File(repo,
                            id.group.replace('.', '/') +
                                    '/' + id.name + '/' + id.version + '/NOTICE')
                    if (!fromFile.isFile()) {
                        System.out.println("  Error: Missing NOTICE file")
                        throw new GradleException(
                                "Missing NOTICE file: " + fromFile.absolutePath)
                    }

                    toFile = new File(noticeDir, "NOTICE_" + artifact.file.name + ".txt")

                    System.out.println("  > ${toFile.absolutePath}")

                    copyNoticeAndAddHeader(fromFile, toFile, artifact.file.name)
                } else {
                    System.out.println("  SKIPPED (already processed)")
                }
            }
        }
        System.out.println("")
    }

    private static void gatherNoticesFromFolder(File folder,
                                      Set<String> filenameCache,
                                      List<File> noticeList) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.name.startsWith("NOTICE_") &&
                        !filenameCache.contains(file.name)) {
                    filenameCache.add(file.name)
                    noticeList.add(file)
                }
            }
        }
    }

    private static void mergeNotices(List<File> notices, BufferedWriter noticeWriter) {
        for (File file : notices) {
            List<String> lines = Files.readLines(file, Charsets.UTF_8)
            for (String line : lines) {
                noticeWriter.write(line, 0, line.length())
                noticeWriter.newLine()
            }
            noticeWriter.newLine()
        }
    }

    private static void copyNoticeAndAddHeader(File from, File to, String name) {
        List<String> lines = Files.readLines(from, Charsets.UTF_8)
        List<String> noticeLines = Lists.newArrayListWithCapacity(lines.size() + 4)
        noticeLines.addAll([
            "============================================================",
            "Notices for file(s):",
            name,
            "------------------------------------------------------------"
        ]);
        noticeLines.addAll(lines);

        Files.write(Joiner.on("\n").join(noticeLines.iterator()), to, Charsets.UTF_8)
    }
}
