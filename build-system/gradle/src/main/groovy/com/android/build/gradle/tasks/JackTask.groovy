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

package com.android.build.gradle.tasks

import com.android.annotations.NonNull
import com.android.build.gradle.BasePlugin
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.repository.FullRevision
import com.google.common.base.Charsets
import com.google.common.collect.Lists
import com.google.common.io.Files
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile
/**
 * Jack task.
 */
public class JackTask extends AbstractCompile {

    final static FullRevision JACK_MIN_REV = new FullRevision(21, 1, 0)

    BasePlugin plugin

    @InputFile
    File getJackExe() {
        plugin.ensureTargetSetup()
        new File(plugin.androidBuilder.targetInfo.buildTools.getPath(BuildToolInfo.PathId.JACK))
    }

    @InputFiles
    Collection<File> packagedLibraries

    @InputFiles @Optional
    Collection<File> proguardFiles

    @Input
    boolean debug

    File tempFolder

    @OutputFile
    File jackFile

    @OutputFile @Optional
    File mappingFile

    @TaskAction
    void compile() {
        FullRevision revision = plugin.androidBuilder.targetInfo.buildTools.revision
        if (revision.compareTo(JACK_MIN_REV) < 0) {
            throw new RuntimeException("Jack requires Build Tools ${JACK_MIN_REV.toString()} or later")
        }

        List<String> command = Lists.newArrayList()

        command << "java"
        command << "-Xmx1024M"
        command << "-jar"
        command << getJackExe().absolutePath
        command << "--classpath"
        command << computeBootClasspath()
        for (File lib : getPackagedLibraries()) {
            command << "--import-jack"
            command << lib.absolutePath
        }
        command << "--output"
        command << getDestinationDir().absolutePath

        // temp workaround since --jack-output cannot be used
        // with the dex output.
        /*
        command << "--jack-output"
        command << jackOutFolder.absolutePath
        */
        command << "-D"
        command << "jack.jackfile.generate=true"
        command << "-D"
        command << "jack.jackfile.output.container=zip"
        command << "-D"
        command << "jack.jackfile.output.zip=${getJackFile().absolutePath}".toString()

        command << "-D"
        command << "jack.import.resource.policy=keep-first"

        Collection<File> _proguardFiles = getProguardFiles()
        if (_proguardFiles != null && !_proguardFiles.isEmpty()) {
            for (File file : _proguardFiles) {
                command << "--proguard-flags"
                command << file.absolutePath
            }

            File _mappingFile = getMappingFile()
            if (_mappingFile != null) {
                // the normal property-based mechanism to output a mapping file
                // doesn't work, so use a proguard file instead until this is
                // fixed.
                //command << "-D"
                //command << "jack.obfuscation.mapping.dump.file=${_mappingFile.absolutePath}".toString()
                command << "--proguard-flags"
                command << createMappingFileCommand(_mappingFile)
            }
        }

        command << "--ecj"
        command << computeEcjOptionFile()

        plugin.androidBuilder.commandLineRunner.runCmdLine(command, null)
    }

    private String computeEcjOptionFile() {
        File folder = getTempFolder()
        folder.mkdirs()
        File file = new File(folder, "ecj-options.txt");

        StringBuffer sb = new StringBuffer()

        for (File sourceFile : getSource().files) {
            sb.append(sourceFile.absolutePath).append('\n')
        }

        file.getParentFile().mkdirs()

        Files.write(sb.toString(), file, Charsets.UTF_8)

        return "@$file.absolutePath"
    }

    private String createMappingFileCommand(@NonNull File mappingFile) {
        File folder = getTempFolder();
        folder.mkdirs()
        File file = new File(folder,"mapping.pro");

        Files.write("-printmapping ${mappingFile.absolutePath}\n",
                file, Charsets.UTF_8);

        return file.absolutePath
    }

    private String computeBootClasspath() {
        StringBuilder sb = new StringBuilder()

        boolean first = true;
        for (File file : getClasspath().files) {
            if (!first) {
                sb.append(':')
            } else {
                first = false
            }
            sb.append(file.getAbsolutePath())
        }

        return sb.toString()
    }
}
