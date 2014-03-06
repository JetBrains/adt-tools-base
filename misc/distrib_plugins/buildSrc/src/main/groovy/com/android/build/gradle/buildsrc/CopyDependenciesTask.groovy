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
import com.google.common.io.Files
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.tasks.TaskAction

class CopyDependenciesTask extends BaseTask {

    Project project
    File distributionDir

    @TaskAction
    public void copyDependencies() {

        Configuration configuration = project.configurations.compile
        Set<ResolvedArtifact> artifacts = configuration.resolvedConfiguration.resolvedArtifacts
        File dir = getDistributionDir()
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
                File dest = new File(dir, artifact.file.name)
                System.out.println("  > ${dest.absolutePath}")
                Files.copy(artifact.file, dest)
            }
        }
        System.out.println("")
    }
}
