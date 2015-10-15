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

package com.android.builder.shrinker.parser;


import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Class representing a ProGuard config file.
 *
 * <p>Mostly copied from Jack.
 */
public class Flags {

    private boolean shrink = true;

    private boolean optimize = true;

    private boolean preverify = true;

    private boolean obfuscate = true;

    private boolean keepParameterNames = false;

    private boolean useMixedCaseClassName = true;

    @Nullable
    private File obfuscationMapping = null;

    private boolean printMapping = false;

    private boolean useUniqueClassMemberNames = false;

    @Nullable
    private String packageForRenamedClasses = null;

    @Nullable
    private String packageForFlatHierarchy = null;

    @Nullable
    private String libraryJars = null;

    @NonNull
    private final List<File> inJars = new ArrayList<File>(1);

    @NonNull
    private final List<File> outJars = new ArrayList<File>(1);

    @Nullable
    private File outputMapping;

    @Nullable
    private File obfuscationDictionary;

    @Nullable
    private File classObfuscationDictionary;

    @Nullable
    private File packageObfuscationDictionary;

    @Nullable
    private FilterSpecification keepAttributes;

    @Nullable
    private String renameSourceFileAttribute;

    @Nullable
    private FilterSpecification keepPackageNames;

    @Nullable
    private FilterSpecification adaptClassStrings;

    @NonNull
    private final List<ClassSpecification> keepClassSpecs
            = new ArrayList<ClassSpecification>();

    @NonNull
    private final List<ClassSpecification> keepClassesWithMembersSpecs
            = new ArrayList<ClassSpecification>();

    @NonNull
    private final List<ClassSpecification> keepClassMembersSpecs
            = new ArrayList<ClassSpecification>();

    private boolean printSeeds = false;

    @Nullable
    private File seedsFile;

    @Nullable
    private FilterSpecification adaptResourceFileNames;

    @Nullable
    private FilterSpecification adaptResourceFileContents;

    public void setShrink(boolean shrink) {
        this.shrink = shrink;
    }

    public void setOptimize(boolean optimize) {
        this.optimize = optimize;
    }

    public void setPreverify(boolean preverify) {
        this.preverify = preverify;
    }

    public void setPrintMapping(boolean printMapping) {
        this.printMapping = printMapping;
    }

    public boolean printMapping() {
        return this.printMapping;
    }

    public void setOutputMapping(@Nullable File outputMapping) {
        this.outputMapping = outputMapping;
    }

    public boolean shrink() {
        return shrink;
    }

    public boolean optimize() {
        return optimize;
    }

    public boolean preverify() {
        return preverify;
    }

    public void setObfuscate(boolean obfuscate) {
        this.obfuscate = obfuscate;
    }

    public boolean obfuscate() {
        return obfuscate;
    }

    public void setKeepParameterNames(boolean keepParameterNames) {
        this.keepParameterNames = keepParameterNames;
    }

    public boolean getKeepParameterNames() {
        assert obfuscate;
        return keepParameterNames;
    }

    public void setObfuscationMapping(@Nullable File obfuscationMapping) {
        this.obfuscationMapping = obfuscationMapping;
    }

    public void setUseMixedCaseClassName(boolean useMixedCaseClassName) {
        this.useMixedCaseClassName = useMixedCaseClassName;
    }

    public void setUseUniqueClassMemberNames(boolean useUniqueClassMemberNames) {
        this.useUniqueClassMemberNames = useUniqueClassMemberNames;
    }

    public void addInJars(@NonNull List<File> inJars) {
        this.inJars.addAll(inJars);
    }

    public void addOutJars(@NonNull List<File> outJars) {
        this.outJars.addAll(outJars);
    }

    public void addLibraryJars(@NonNull String libraryJars) {
        if (this.libraryJars == null) {
            this.libraryJars = libraryJars;
        } else {
            this.libraryJars += File.pathSeparatorChar + libraryJars;
        }
    }

    public boolean getUseUniqueClassMemberNames() {
        return this.useUniqueClassMemberNames;
    }

    public boolean getUseMixedCaseClassName() {
        return this.useMixedCaseClassName;
    }

    @NonNull
    public List<File> getInJars() {
        return inJars;
    }

    @NonNull
    public List<File> getOutJars() {
        return outJars;
    }

    @Nullable
    public String getLibraryJars() {
        return libraryJars;
    }

    public File getObfuscationMapping() {
        return obfuscationMapping;
    }

    public File getOutputMapping() {
        return outputMapping;
    }

    public File getObfuscationDictionary() {
        return obfuscationDictionary;
    }

    public void setObfuscationDictionary(@Nullable File obfuscationDictionary) {
        this.obfuscationDictionary = obfuscationDictionary;
    }

    public File getPackageObfuscationDictionary() {
        return packageObfuscationDictionary;
    }

    public void setPackageObfuscationDictionary(@Nullable File packageObfuscationDictionary) {
        this.packageObfuscationDictionary = packageObfuscationDictionary;
    }

    public File getClassObfuscationDictionary() {
        return classObfuscationDictionary;
    }

    public void setClassObfuscationDictionary(@Nullable File classObfuscationDictionary) {
        this.classObfuscationDictionary = classObfuscationDictionary;
    }

    public void setPackageForRenamedClasses(@Nullable String packageForRenamedClasses) {
        this.packageForRenamedClasses = packageForRenamedClasses;
        if (packageForRenamedClasses != null) {
            // packageForRenamedClasses overrides packageForFlatHierarchy
            this.packageForFlatHierarchy = null;
        }
    }

    @Nullable
    public String getPackageForRenamedClasses() {
        return packageForRenamedClasses;
    }

    public void setPackageForFlatHierarchy(@Nullable String packageForFlatHierarchy) {
        if (packageForRenamedClasses == null) {
            // packageForRenamedClasses overrides packageForFlatHierarchy
            this.packageForFlatHierarchy = packageForFlatHierarchy;
        } else {
            assert this.packageForFlatHierarchy == null;
        }
    }

    @Nullable
    public String getPackageForFlatHierarchy() {
        return packageForFlatHierarchy;
    }

    @NonNull
    public List<ClassSpecification> getKeepClassSpecs() {
        return keepClassSpecs;
    }

    @NonNull
    public List<ClassSpecification> getKeepClassesWithMembersSpecs() {
        return keepClassesWithMembersSpecs;
    }

    @NonNull
    public List<ClassSpecification> getKeepClassMembersSpecs() {
        return keepClassMembersSpecs;
    }

    public void addKeepClassSpecification(
            @Nullable ClassSpecification classSpecification) {
        assert classSpecification != null;
        keepClassSpecs.add(classSpecification);
    }

    public void addKeepClassesWithMembers(
            @Nullable ClassSpecification classSpecification) {
        assert classSpecification != null;
        keepClassesWithMembersSpecs.add(classSpecification);
    }

    public void addKeepClassMembers(
            @Nullable ClassSpecification classSpecification) {
        assert classSpecification != null;
        keepClassMembersSpecs.add(classSpecification);
    }

    public void setKeepAttribute(@Nullable FilterSpecification attribute) {
        keepAttributes = attribute;
    }

    public void setKeepPackageName(@Nullable FilterSpecification packageSpec) {
        keepPackageNames = packageSpec;
    }

    public FilterSpecification getKeepPackageNames() {
        return keepPackageNames;
    }

    public void addKeepPackageNames(@NonNull NameSpecification packageName, boolean negator) {
        if (keepPackageNames == null) {
            keepPackageNames = new FilterSpecification();
        }
        keepPackageNames.addElement(packageName, negator);
    }

    public boolean keepAttribute(@NonNull String attributeName) {
        assert obfuscate;
        return keepAttributes != null && keepAttributes.matches(attributeName);
    }

    public void setRenameSourceFileAttribute(@Nullable String renameSourceFileAttribute) {
        this.renameSourceFileAttribute = renameSourceFileAttribute;
    }

    @Nullable
    public String getRenameSourceFileAttribute() {
        return renameSourceFileAttribute;
    }

    @Nullable
    public FilterSpecification getAdaptClassStrings() {
        return adaptClassStrings;
    }

    public void setAdaptClassStrings(@Nullable FilterSpecification adaptClassStrings) {
        this.adaptClassStrings = adaptClassStrings;
    }

    public boolean printSeeds() {
        return printSeeds;
    }

    public void setPrintSeeds(boolean printSeeds) {
        this.printSeeds = printSeeds;
    }

    @Nullable
    public File getSeedsFile() {
        return seedsFile;
    }

    public void setSeedsFile(@Nullable File seedsFile) {
        this.seedsFile = seedsFile;
    }

    public void adaptResourceFileNames(@Nullable FilterSpecification filter) {
        this.adaptResourceFileNames = filter;
    }

    @Nullable
    public FilterSpecification getAdaptResourceFileNames() {
        return adaptResourceFileNames;
    }

    public void adaptResourceFileContents(@Nullable FilterSpecification filter) {
        this.adaptResourceFileContents = filter;
    }

    @Nullable
    public FilterSpecification getAdaptResourceFileContents() {
        return adaptResourceFileContents;
    }
}

