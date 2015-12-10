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

package com.android.build.gradle.internal.tasks.multidex;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

@ParallelizableTask
public class CreateManifestKeepList extends DefaultAndroidTask {

    private File manifest;

    private File outputFile;

    private File proguardFile;

    @InputFile
    public File getManifest() {
        return manifest;
    }

    public void setManifest(File manifest) {
        this.manifest = manifest;
    }

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @InputFile @Optional
    public File getProguardFile() {
        return proguardFile;
    }

    public void setProguardFile(File proguardFile) {
        this.proguardFile = proguardFile;
    }

    @TaskAction
    public void generateKeepListFromManifest()
            throws ParserConfigurationException, SAXException, IOException {
        SAXParser parser = SAXParserFactory.newInstance().newSAXParser();

        Writer out = new BufferedWriter(new FileWriter(getOutputFile()));
        try {
            parser.parse(getManifest(), new ManifestHandler(out));

            // add a couple of rules that cannot be easily parsed from the manifest.
            out.write("-keep public class * extends android.app.backup.BackupAgent {\n"
                    + "    <init>();\n"
                    + "}\n"
                    + "-keep public class * extends java.lang.annotation.Annotation {\n"
                    + "    *;\n"
                    + "}\n"
                    + "-keep class com.android.tools.fd.** {\n"
                    + "    *;\n"
                    + "}\n"
                    + "-dontnote com.android.tools.fd.**,"
                    + "android.support.multidex.MultiDexExtractor\n");

            if (proguardFile != null) {
                out.write(Files.toString(proguardFile, Charsets.UTF_8));
            }
        } finally {
            out.close();
        }
    }

    private static final String DEFAULT_KEEP_SPEC = "{ <init>(); }";

    private static final Map<String, String> KEEP_SPECS = ImmutableMap.<String, String>builder()
            .put("application", "{\n"
                    + "    <init>();\n"
                    + "    void attachBaseContext(android.content.Context);\n"
                    + "}")
            .put("activity", DEFAULT_KEEP_SPEC)
            .put("service", DEFAULT_KEEP_SPEC)
            .put("receiver", DEFAULT_KEEP_SPEC)
            .put("provider", DEFAULT_KEEP_SPEC)
            .put("instrumentation", DEFAULT_KEEP_SPEC).build();

    private static class ManifestHandler extends DefaultHandler {
        private Writer out;

        ManifestHandler(Writer out) {
            this.out = out;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attr) {
            // IJ thinks the qualified reference to KEEP_SPECS is not needed but Groovy needs
            // it at runtime?!?
            //noinspection UnnecessaryQualifiedReference
            String keepSpec = CreateManifestKeepList.KEEP_SPECS.get(qName);
            if (!Strings.isNullOrEmpty(keepSpec)) {
                keepClass(attr.getValue("android:name"), keepSpec, out);
                // Also keep the original application class when using instant-run.
                keepClass(attr.getValue("name"), keepSpec, out);
            }
        }
    }

    private static void keepClass(
            @Nullable String className,
            @NonNull String keepSpec,
            @NonNull Writer out) {
        if (className != null) {
            try {
                out.write("-keep class ");
                out.write(className);
                out.write(" ");
                out.write(keepSpec);
                out.write("\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class ConfigAction implements TaskConfigAction<CreateManifestKeepList> {

        private VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("collect", "MultiDexComponents");
        }

        @NonNull
        @Override
        public Class<CreateManifestKeepList> getType() {
            return CreateManifestKeepList.class;
        }

        @Override
        public void execute(@NonNull CreateManifestKeepList manifestKeepListTask) {
            manifestKeepListTask.setVariantName(scope.getVariantConfiguration().getFullName());

            // since all the output have the same manifest, besides the versionCode,
            // we can take any of the output and use that.
            final BaseVariantOutputData output = scope.getVariantData().getOutputs().get(0);
            ConventionMappingHelper.map(manifestKeepListTask, "manifest", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    return output.getScope().getManifestOutputFile();
                }
            });

            manifestKeepListTask.proguardFile = scope.getVariantConfiguration().getMultiDexKeepProguard();
            manifestKeepListTask.outputFile = scope.getManifestKeepListFile();
        }
    }
}
