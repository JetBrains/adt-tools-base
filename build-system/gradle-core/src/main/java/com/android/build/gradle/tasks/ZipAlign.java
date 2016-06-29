package com.android.build.gradle.tasks;

import static com.android.builder.packaging.NativeLibrariesPackagingMode.UNCOMPRESSED_AND_ALIGNED;
import static com.android.sdklib.BuildToolInfo.PathId.ZIP_ALIGN;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.annotations.PackageFile;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.builder.packaging.NativeLibrariesPackagingMode;
import com.android.builder.packaging.PackagingUtils;
import com.google.common.base.Preconditions;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

@ParallelizableTask
public class ZipAlign extends DefaultTask implements FileSupplier {

    // ----- PUBLIC TASK API -----

    /**
     * Resulting zip file.
     * @return the resulting zip file; may be the same as the input file
     */
    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * Input zip file to align.
     * @return the input zip file to align
     */
    @InputFile
    public File getInputFile() {
        return inputFile;
    }

    public void setInputFile(File inputFile) {
        this.inputFile = inputFile;
    }

    // ----- PRIVATE TASK API -----

    private File outputFile;

    @PackageFile
    private File inputFile;

    private File manifest;

    private File zipAlignExe;

    private InstantRunBuildContext instantRunBuildContext;

    public void setInstantRunBuildContext(InstantRunBuildContext instantRunBuildContext) {
        this.instantRunBuildContext = instantRunBuildContext;
    }

    /**
     * Obtains the executable used to perform zip-align. Not used if using the new packaging
     * code.
     * @return the zip align executable
     */
    @InputFile
    public File getZipAlignExe() {
        return zipAlignExe;
    }

    public void setZipAlignExe(File zipAlignExe) {
        this.zipAlignExe = zipAlignExe;
    }

    @InputFile
    public File getManifest() {
        return manifest;
    }

    @TaskAction
    public void zipAlign() throws IOException {
        File inputFile = getInputFile();
        File outputFile = getOutputFile();

        Preconditions.checkNotNull(inputFile, "inputFile == null");
        Preconditions.checkNotNull(outputFile, "outputFile == null");

        if (!inputFile.isFile()) {
            throw new IOException("Path '" + inputFile.getAbsolutePath()
                    + "' does not represent a file.");
        }

        if (outputFile.exists() && !outputFile.isFile()) {
            throw new IOException("Path '" + inputFile.getAbsolutePath()
                    + "' exists but is not a file.");
        }

        getProject().exec(execSpec -> {
            execSpec.executable(getZipAlignExe());
            // Overwrite output:
            execSpec.args("-f");

            NativeLibrariesPackagingMode nativeLibrariesPackagingMode =
                    PackagingUtils.getNativeLibrariesLibrariesPackagingMode(manifest);

            if (nativeLibrariesPackagingMode == UNCOMPRESSED_AND_ALIGNED) {
                // Page-align shared object files:
                execSpec.args("-p");
            }

            // Default align:
            execSpec.args("4");

            execSpec.args(getInputFile());
            execSpec.args(getOutputFile());
        });

        // mark this APK production, this will eventually be saved when instant-run is enabled.
        try {
            instantRunBuildContext.addChangedFile(
                    InstantRunBuildContext.FileType.MAIN, getOutputFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ----- FileSupplierTask -----

    @Override
    public File get() {
        return getOutputFile();
    }

    @NonNull
    @Override
    public Task getTask() {
        return this;
    }

    // ----- ConfigAction -----

    public static class ConfigAction implements TaskConfigAction<ZipAlign> {

        private final VariantOutputScope scope;

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("zipalign");
        }

        @NonNull
        @Override
        public Class<ZipAlign> getType() {
            return ZipAlign.class;
        }

        public ConfigAction(VariantOutputScope scope) {
            this.scope = scope;
        }

        @Override
        public void execute(@NonNull ZipAlign zipAlign) {
            ApkVariantOutputData variantData = (ApkVariantOutputData) scope.getVariantOutputData();
            variantData.zipAlignTask = zipAlign;

            zipAlign.manifest = scope.getManifestOutputFile();

            ConventionMappingHelper.map(zipAlign, "inputFile", () -> {
                // wire to the output of the package task.
                PackageAndroidArtifact packageAndroidArtifactTask =
                        scope.getVariantOutputData().packageAndroidArtifactTask;
                return packageAndroidArtifactTask == null
                        ? scope.getPackageApk()
                        : packageAndroidArtifactTask.getOutputFile();
            });

            ConventionMappingHelper.map(
                    zipAlign,
                    "outputFile",
                    () -> scope.getGlobalScope().getProject().file(
                            scope.getGlobalScope().getApkLocation() + "/"
                                    + scope.getGlobalScope().getProjectBaseName() + "-"
                                    + scope.getVariantOutputData().getBaseName() + ".apk"));

            ConventionMappingHelper.map(zipAlign, "zipAlignExe", () -> {
                String zipAlignPath =
                        scope.getGlobalScope()
                                .getAndroidBuilder()
                                .getTargetInfo()
                                .getBuildTools()
                                .getPath(ZIP_ALIGN);
                if (zipAlignPath != null) {
                    return new File(zipAlignPath);
                } else {
                    return null;
                }
            });

            zipAlign.instantRunBuildContext = scope.getVariantScope().getInstantRunBuildContext();
        }
    }
}
