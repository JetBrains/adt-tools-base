package com.android.build.gradle.tasks.factory;

import static com.google.common.base.Preconditions.checkState;

import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.model.SourceProvider;

import org.gradle.api.tasks.Sync;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Configuration Action for a process*JavaRes tasks.
 */
public class ProcessJavaResConfigAction implements TaskConfigAction<Sync> {
    private VariantScope scope;

    public ProcessJavaResConfigAction(VariantScope scope) {
        this.scope = scope;
    }

    @Override
    public String getName() {
        return scope.getTaskName("process", "JavaRes");
    }

    @Override
    public Class<Sync> getType() {
        return Sync.class;
    }

    @Override
    public void execute(Sync processResources) {
        scope.getVariantData().processJavaResourcesTask = processResources;
        GradleVariantConfiguration variantConfiguration = scope.getVariantConfiguration();

        AndroidSourceSet defaultSourceSet =
                (AndroidSourceSet) variantConfiguration.getDefaultSourceSet();

        processResources.from(defaultSourceSet.getResources().getSourceFiles());

        if (!variantConfiguration.getType().isSingleBuildType()) {
            AndroidSourceSet buildTypeSourceSet =
                    (AndroidSourceSet) variantConfiguration.getBuildTypeSourceSet();
            checkState(buildTypeSourceSet != null); // checked isSingleBuildType() above.

            processResources.from(buildTypeSourceSet.getResources().getSourceFiles());
        }

        if (variantConfiguration.hasFlavors()) {
            List<SourceProvider> flavorSourceProviders =
                    variantConfiguration.getFlavorSourceProviders();

            for (SourceProvider flavorSourceProvider : flavorSourceProviders) {
                AndroidSourceSet flavorSourceSet = (AndroidSourceSet) flavorSourceProvider;
                processResources.from(flavorSourceSet.getResources().getSourceFiles());
            }

            AndroidSourceSet multiFlavorSourceSet =
                    (AndroidSourceSet) variantConfiguration.getMultiFlavorSourceProvider();
            if (multiFlavorSourceSet != null) {
                processResources.from(multiFlavorSourceSet.getResources().getSourceFiles());
            }
        }

        AndroidSourceSet variantSourceSet =
                (AndroidSourceSet) variantConfiguration.getVariantSourceProvider();
        if (variantSourceSet != null) {
            processResources.from(variantSourceSet.getResources().getSourceFiles());
        }

        ConventionMappingHelper.map(
                processResources,
                "destinationDir",
                new Callable<File>() {
                    @Override
                    public File call() throws Exception {
                        return new File(scope.getSourceFoldersJavaResDestinationDir(), "src");
                    }
                });
    }
}
