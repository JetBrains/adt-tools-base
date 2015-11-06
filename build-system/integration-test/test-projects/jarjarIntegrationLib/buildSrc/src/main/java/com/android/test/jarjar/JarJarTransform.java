package com.android.test.jarjar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closer;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.google.common.base.Preconditions.checkNotNull;

public class JarJarTransform extends Transform {

    private final boolean broken;

    public JarJarTransform(boolean broken) {
        this.broken = broken;
    }

    @Override
    public String getName() {
        return "jarjar";
    }

    @Override
    public Set<ContentType> getInputTypes() {
        return ImmutableSet.<ContentType>of(DefaultContentType.CLASSES);
    }

    @Override
    public Set<Scope> getScopes() {
        if (broken) {
            // needs to run on everything to rename what is using gson
            return EnumSet.of(
                    Scope.PROJECT,
                    Scope.PROJECT_LOCAL_DEPS,
                    Scope.SUB_PROJECTS,
                    Scope.SUB_PROJECTS_LOCAL_DEPS,
                    Scope.EXTERNAL_LIBRARIES);
        }

        return EnumSet.of(Scope.PROJECT, Scope.PROJECT_LOCAL_DEPS);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(
            @NonNull Context context,
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedStreams,
            @Nullable TransformOutputProvider output,
            boolean isIncremental) throws TransformException, IOException {

        if (output == null) {
            throw new RuntimeException("Missing output object for transform " + getName());
        }
        output.deleteAll();
        File outputJar = output.getContentLocation("main", getOutputTypes(), getScopes(),
                Format.JAR);
		// create the parent folder
		outputJar.getParentFile().mkdirs();

        // create intermediate files to handle the jar input and the rule file.
        File mergedInputs = File.createTempFile("jajar", "jar");
        File jarjarRules = File.createTempFile("jajar", "rule");

        try {
            // create a tmp jar that contains all the inputs. This is because jarjar expects a jar input.
            // this code is based on the JarMergingTransform
            combineInputIntoJar(inputs, mergedInputs);

            // create the jarjar rules file.
            Files.write("rule com.google.gson.** com.google.repacked.gson.@1", jarjarRules, Charsets.UTF_8);

            // run jarjar by calling the main method as if it came from the command line.
            String[] args =  ImmutableList.of(
                    "process",
                    jarjarRules.getAbsolutePath(),
                    mergedInputs.getAbsolutePath(),
                    outputJar.getAbsolutePath()
            ).toArray(new String[4]);
            com.tonicsystems.jarjar.Main.main(args);

        } catch (Exception e) {
            throw new TransformException(e);
        } finally {
            // delete tmp files
            mergedInputs.delete();
            jarjarRules.delete();
        }
    }

    private void combineInputIntoJar(
            @NonNull Collection<TransformInput> inputs,
            @NonNull File mergedInputs) throws TransformException, IOException {
        Closer closer = Closer.create();
        try {

            FileOutputStream fos = closer.register(new FileOutputStream(mergedInputs));
            JarOutputStream jos = closer.register(new JarOutputStream(fos));

            final byte[] buffer = new byte[8192];

            for (TransformInput input : inputs) {
                for (JarInput jarInput : input.getJarInputs()) {
                    processJarFile(jos, jarInput.getFile(), buffer);
                }

                for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                    processFolder(jos, "", dirInput.getFile(), buffer);
                }
            }

        } catch (FileNotFoundException e) {
            throw new TransformException(e);
        } catch (IOException e) {
            throw new TransformException(e);
        } finally {
            closer.close();
        }
    }

    private static void processFolder(
            @NonNull JarOutputStream jos,
            @NonNull String path,
            @NonNull File folder,
            @NonNull byte[] buffer)
            throws IOException {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    // new entry
                    jos.putNextEntry(new JarEntry(path + file.getName()));

                    // put the file content
                    Closer closer = Closer.create();
                    try {
                        FileInputStream fis = closer.register(new FileInputStream(file));
                        int count;
                        while ((count = fis.read(buffer)) != -1) {
                            jos.write(buffer, 0, count);
                        }
                    } finally {
                        closer.close();
                    }

                    // close the entry
                    jos.closeEntry();
                } else if (file.isDirectory()) {
                    processFolder(jos, path + file.getName() + "/", file, buffer);
                }
            }
        }
    }

    private static void processJarFile(JarOutputStream jos, File file, byte[] buffer)
            throws IOException {

        Closer closer = Closer.create();
        try {
            FileInputStream fis = closer.register(new FileInputStream(file));
            ZipInputStream zis = closer.register(new ZipInputStream(fis));

            // loop on the entries of the jar file package and put them in the final jar
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // do not take directories or anything inside a potential META-INF folder.
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (!name.endsWith(".class")) {
                    continue;
                }

                JarEntry newEntry;

                // Preserve the STORED method of the input entry.
                if (entry.getMethod() == JarEntry.STORED) {
                    newEntry = new JarEntry(entry);
                } else {
                    // Create a new entry so that the compressed len is recomputed.
                    newEntry = new JarEntry(name);
                }

                // add the entry to the jar archive
                jos.putNextEntry(newEntry);

                // read the content of the entry from the input stream, and write it into the archive.
                int count;
                while ((count = zis.read(buffer)) != -1) {
                    jos.write(buffer, 0, count);
                }

                // close the entries for this file
                jos.closeEntry();
                zis.closeEntry();
            }
        } finally {
            closer.close();
        }
    }
}