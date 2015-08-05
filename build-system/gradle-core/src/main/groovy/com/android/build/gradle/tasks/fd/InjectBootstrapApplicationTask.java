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

package com.android.build.gradle.tasks.fd;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.TAG_APPLICATION;
import static java.io.File.separatorChar;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_6;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.BuildException;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Task which injects the bootstrapping application for incremental class and resource
 * deployment
 */
public class InjectBootstrapApplicationTask extends BaseTask {
    private File manifestFile;
    private File destDir;

    @OutputDirectory
    public File getOutputDir() {
        return destDir;
    }

    @InputFile
    public File getManifestFile() {
        return manifestFile;
    }

    public void setManifestFile(File manifestFile) {
        this.manifestFile = manifestFile;
    }

    public void setDestDir(File destDir) {
        this.destDir = destDir;
    }

    @TaskAction
    public void rewrite() throws IOException {
        File outputDir = getOutputDir();
        extractLibrary(outputDir);
        if (getManifestFile() != null) {
            postProcessManifest(getManifestFile(), outputDir);
        }
    }

    public static class ConfigAction implements TaskConfigAction<InjectBootstrapApplicationTask> {

        private VariantOutputScope scope;

        public ConfigAction(VariantOutputScope scope) {
            this.scope = scope;
        }

        @Override
        public String getName() {
            return scope.getTaskName("inject", "Bootstrap");
        }

        @Override
        public Class<InjectBootstrapApplicationTask> getType() {
            return InjectBootstrapApplicationTask.class;
        }

        @Override
        public void execute(InjectBootstrapApplicationTask task) {
            BaseVariantData<? extends BaseVariantOutputData> variantData =
                    scope.getVariantScope().getVariantData();
            task.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            task.setVariantName(scope.getVariantScope().getVariantConfiguration().getFullName());
            File dest = variantData.getScope().getJavaOutputDir();
            task.setDestDir(dest);
            BaseVariantOutputData outputData = scope.getVariantOutputData();
            if (outputData.manifestProcessorTask != null) {
                File manifest = outputData.manifestProcessorTask.getManifestOutputFile();
                task.setManifestFile(manifest);
            }
        }
    }

    /**
     * Processes the given merged manifest file; reads out the application class,
     * rewrites the manifest to reference the bootstrapping application, and creates an
     * AppInfo class listing the applicationId and application classes (if any).
     *
     * @param mergedManifest the merged manifest file
     * @param classDir the root class folder to write the application info class to
     * @throws IOException if there's a problem reading/writing the manifest or class files
     */
    public static void postProcessManifest(@NonNull File mergedManifest, @NonNull File classDir)
            throws IOException {
        // Grab the application id and application class stashes away in the Android
        // manifest (not in the Android namespace) and generate an AppInfo class.
        // (Earlier, I did all the processing here - read the manifest, rewrite it by
        // generating the AppInfo class and rewriting the manifest on the fly to have
        // the new bootstrapping application, but we
        //  (1) need for this task to be done after manifest merging, and
        //  (2) need for it to be done before packaging
        // but when combined with the current task dependencies (e.g. compilation
        // depending on resource merging, such that R classes exist) this led to
        // circular task dependencies. So for now, this is split into two parts:
        // In manifest merging we stash away and replace the application id/class, and
        // here in a packaging task we inject runtime libraries.
        if (mergedManifest.exists()) {
            try {
                Document document = XmlUtils.parseUtfXmlFile(mergedManifest, true);
                Element root = document.getDocumentElement();
                if (root != null) {
                    String applicationId = root.getAttribute(ATTR_PACKAGE);
                    String applicationClass = null;
                    NodeList children = root.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node node = children.item(i);
                        if (node.getNodeType() == Node.ELEMENT_NODE &&
                                node.getNodeName().equals(TAG_APPLICATION)) {
                            String applicationClass1 = null;

                            Element element = (Element) node;
                            if (element.hasAttribute(ATTR_NAME)) {
                                String name = element.getAttribute(ATTR_NAME);
                                assert !name.startsWith(".") : name;
                                if (!name.isEmpty()) {
                                    applicationClass1 = name;
                                }
                            }

                            applicationClass = applicationClass1;
                            break;
                        }
                    }

                    if (!applicationId.isEmpty()) {
                        // Must be *after* extractLibrary() to replace dummy version
                        writeAppInfoClass(applicationId, applicationClass, classDir);
                    }
                }
            } catch (ParserConfigurationException e) {
                throw new BuildException("Failed to inject bootstrapping application", e);
            } catch (IOException e) {
                throw new BuildException("Failed to inject bootstrapping application", e);
            } catch (SAXException e) {
                throw new BuildException("Failed to inject bootstrapping application", e);
            }
        }
    }

    static void extractLibrary(@NonNull File destDir) throws IOException {
        InputStream stream = InjectBootstrapApplicationTask.class.getResourceAsStream("/fd-runtime.jar");
        if (stream == null) {
            System.err.println("Couldn't find embedded Fast Deployment runtime library");
            return;
        }

        stream = new BufferedInputStream(stream);
        try {
            JarInputStream jarInputStream = new JarInputStream(stream);
            try {
                ZipEntry entry = jarInputStream.getNextEntry();
                while (entry != null) {
                    String name = entry.getName();
                    if (name.startsWith("META-INF")) {
                        continue;
                    }
                    File dest = new File(destDir, name.replace('/', separatorChar));
                    if (entry.isDirectory()) {
                        if (!dest.exists()) {
                            boolean created = dest.mkdirs();
                            if (!created) {
                                throw new IOException(dest.getPath());
                            }
                        }
                    } else {
                        byte[] bytes = ByteStreams.toByteArray(jarInputStream);
                        Files.write(bytes, dest);
                    }
                    entry = jarInputStream.getNextEntry();
                }
            } finally {
                jarInputStream.close();
            }
        } finally {
            stream.close();
        }
    }

    static void writeAppInfoClass(
            @NonNull String applicationId,
            @Nullable String applicationClass,
            @NonNull File classDir)
            throws IOException {
        ClassWriter cw = new ClassWriter(0);
        FieldVisitor fv;
        MethodVisitor mv;

        String appInfoOwner = "com/android/tools/fd/runtime/AppInfo";
        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, appInfoOwner, null, "java/lang/Object", null);

        fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "applicationId", "Ljava/lang/String;", null, null);
        fv.visitEnd();
        fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "applicationClass", "Ljava/lang/String;", null, null);
        fv.visitEnd();
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLocalVariable("this", "L" + appInfoOwner + ";", null, l0, l1, 0);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn(applicationId);
        mv.visitFieldInsn(PUTSTATIC, appInfoOwner, "applicationId", "Ljava/lang/String;");
        if (applicationClass != null) {
            mv.visitLdcInsn(applicationClass);
        } else {
            mv.visitInsn(ACONST_NULL);
        }
        mv.visitFieldInsn(PUTSTATIC, appInfoOwner, "applicationClass", "Ljava/lang/String;");
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();

        File dest = new File(classDir, appInfoOwner.replace('/', separatorChar) + DOT_CLASS);
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            boolean created = parent.mkdirs();
            if (!created) {
                throw new IOException(parent.getPath());
            }
        }
        Files.write(bytes, dest);
    }

    public static void injectApplication(@NonNull File merged) throws IOException {
        if (merged.exists()) {
            try {
                Document document = XmlUtils.parseUtfXmlFile(merged, true);
                Element root = document.getDocumentElement();
                if (root != null) {
                    NodeList children = root.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node node = children.item(i);
                        if (node.getNodeType() == Node.ELEMENT_NODE &&
                                node.getNodeName().equals(TAG_APPLICATION)) {
                            Element application = (Element) node;
                            String applicationClass = null;
                            if (application.hasAttributeNS(ANDROID_URI, ATTR_NAME)) {
                                String name = application.getAttributeNS(ANDROID_URI, ATTR_NAME);
                                assert !name.startsWith(".") : name;
                                if (!name.isEmpty()) {
                                    applicationClass = name;
                                    // Stash for later stage (see comment in
                                    application.setAttribute(ATTR_NAME, name);
                                }
                            }
                            application.setAttributeNS(ANDROID_URI, ATTR_NAME,
                                    "com.android.tools.fd.runtime.BootstrapApplication");

                            if (applicationClass != null) {
                                System.out.println("Instrumented " + applicationClass + " with fast deploy");
                            } else {
                                System.out.println("Instrumented app with a fast deploy bootstrapping application");
                            }

                            break;
                        }
                    }

                    // Write the new modified manifest
                    String xml = AndroidBuilder.formatXml(document, false);

                    // TEMPORARY HACK: For some reason, the namespace aware parser seems
                    // to drop our namespace!
                    if (xml.contains(" name=\"com.android.tools.fd.runtime.BootstrapApplication\"")) {
                        xml = xml.replace(" name=\"com.android.tools.fd.runtime.BootstrapApplication\"",
                                " android:name=\"com.android.tools.fd.runtime.BootstrapApplication\"");
                    }

                    Files.write(xml, merged, Charsets.UTF_8);
                }
            } catch (ParserConfigurationException e) {
                // TODO: Handle properly!
                e.printStackTrace();
            } catch (IOException e) {
                // TODO: Handle properly!
                e.printStackTrace();
            } catch (SAXException e) {
                // TODO: Handle properly!
                e.printStackTrace();
            }
        }
    }

}
