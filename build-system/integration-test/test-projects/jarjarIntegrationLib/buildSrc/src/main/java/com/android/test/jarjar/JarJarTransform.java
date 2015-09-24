package com.android.test.jarjar;

import com.android.annotations.NonNull;
import com.android.build.transform.api.*;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Closer;
import com.google.common.io.Files;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.google.common.base.Preconditions.checkNotNull;

public class JarJarTransform extends Transform implements CombinedTransform {

    private final boolean broken;

    public JarJarTransform(boolean broken) {
        this.broken = broken;
    }

    @Override
    public String getName() {
        return "jarjar";
    }

    @Override
    public Set<ScopedContent.ContentType> getInputTypes() {
        return EnumSet.of(ScopedContent.ContentType.CLASSES);
    }

    @Override
    public Set<ScopedContent.Scope> getScopes() {
        if (broken) {
            // needs to run on everything to rename what is using gson
            return EnumSet.of(
                    ScopedContent.Scope.PROJECT,
                    ScopedContent.Scope.PROJECT_LOCAL_DEPS,
                    ScopedContent.Scope.SUB_PROJECTS,
                    ScopedContent.Scope.SUB_PROJECTS_LOCAL_DEPS,
                    ScopedContent.Scope.EXTERNAL_LIBRARIES);
        }

        return EnumSet.of(ScopedContent.Scope.PROJECT, ScopedContent.Scope.PROJECT_LOCAL_DEPS);
    }

    @Override
    public Type getTransformType() {
        return Type.COMBINED;
    }

    @Override
    public ScopedContent.Format getOutputFormat() {
        return ScopedContent.Format.JAR;
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
            @NonNull TransformOutput combinedOutput,
            boolean isIncremental) throws TransformException, IOException {

        checkNotNull(combinedOutput, "Found no output in transform with Type=COMBINED");
        File jarFile = combinedOutput.getOutFile();
        deleteIfExists(jarFile);

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
                    jarFile.getAbsolutePath()
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
                switch (input.getFormat()) {
                    case SINGLE_FOLDER:
                        for (File inputFile : input.getFiles()) {
                            if (inputFile.isFile()) {
                                processJarFile(jos, inputFile, buffer);
                            } else if (inputFile.isDirectory()) {
                                processFolder(jos, "", inputFile, buffer);
                            }

                        }
                        break;
                    case MULTI_FOLDER:
                        for (File file : input.getFiles()) {
                            File[] subStreams = file.listFiles();
                            if (subStreams != null) {
                                for (File subStream : subStreams) {
                                    processFolder(jos, "", subStream, buffer);
                                }
                            }
                        }

                        break;
                    case JAR:
                        for (File f : input.getFiles()) {
                            processJarFile(jos, f, buffer);
                        }
                        break;
                    default:
                        throw new RuntimeException("Unsupported ScopedContent.Format value: " + input.getFormat().name());
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

    private static void deleteIfExists(File file) throws IOException {
        boolean result = file.delete();
        if (!result && file.exists()) {
            throw new IOException("Failed to delete " + file.getAbsolutePath());
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
