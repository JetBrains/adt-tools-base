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

package com.android.build.gradle.shrinker;

import static com.android.build.gradle.shrinker.AbstractShrinker.isSdkPackage;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.shrinker.AbstractShrinker.CounterSet;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.AsmUtils;
import com.android.utils.FileUtils;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

/**
 * Simple {@link ShrinkerGraph} implementation that uses strings, maps and Java serialization.
 */
public class JavaSerializationShrinkerGraph implements ShrinkerGraph<String> {
    private final SetMultimap<String, String> mAnnotations;

    private final ConcurrentMap<String, ClassInfo> mClasses;

    private final SetMultimap<String, Dependency<String>> mDependencies;

    private final SetMultimap<String, String> mMembers;

    private final ConcurrentMap<String, Integer> mModifiers;

    private final Counters mMultidexCounters;

    private final Counters mShrinkCounters;

    private final File mStateDir;


    private JavaSerializationShrinkerGraph(File stateDir) {
        mStateDir = checkNotNull(stateDir);
        mShrinkCounters = new Counters(
                Maps.<String, DependencyType>newConcurrentMap(),
                ImmutableMap.<String, Counter>of());
        mMultidexCounters = new Counters(
                Maps.<String, DependencyType>newConcurrentMap(),
                ImmutableMap.<String, Counter>of());
        mMembers = Multimaps.synchronizedSetMultimap(HashMultimap.<String, String>create());
        mAnnotations = Multimaps.synchronizedSetMultimap(HashMultimap.<String, String>create());
        mClasses = Maps.newConcurrentMap();
        mModifiers = Maps.newConcurrentMap();
        mDependencies =
                Multimaps.synchronizedSetMultimap(HashMultimap.<String, Dependency<String>>create());
    }

    private JavaSerializationShrinkerGraph(
            File stateDir,
            SetMultimap<String, String> annotations,
            ConcurrentMap<String, ClassInfo> classes,
            SetMultimap<String, Dependency<String>> dependencies,
            SetMultimap<String, String> members,
            ConcurrentMap<String, Integer> modifiers,
            ConcurrentMap<String, DependencyType> multidexRoots,
            Map<String, Counter> multidexCounters,
            ConcurrentMap<String, DependencyType> shrinkRoots,
            Map<String, Counter> shrinkCounters) {
        mStateDir = stateDir;
        mAnnotations = annotations;
        mClasses = classes;
        mDependencies = dependencies;
        mMembers = members;
        mModifiers = modifiers;
        mMultidexCounters = new Counters(multidexRoots, multidexCounters);
        mShrinkCounters = new Counters(shrinkRoots, shrinkCounters);
    }

    public static JavaSerializationShrinkerGraph empty(File stateDir) {
        return new JavaSerializationShrinkerGraph(stateDir);
    }

    @SuppressWarnings("unchecked") // readObject() returns an Object, we need to cast it.
    public static JavaSerializationShrinkerGraph readFromDir(File dir) throws IOException {
        File stateFile = getStateFile(dir);
        ObjectInputStream stream =
                new ObjectInputStream(new BufferedInputStream(new FileInputStream(stateFile)));
        try {
            return new JavaSerializationShrinkerGraph(
                    dir,
                    (SetMultimap) stream.readObject(),
                    (ConcurrentMap) stream.readObject(),
                    (SetMultimap) stream.readObject(),
                    (SetMultimap) stream.readObject(),
                    (ConcurrentMap) stream.readObject(),
                    (ConcurrentMap) stream.readObject(),
                    (Map) stream.readObject(),
                    (ConcurrentMap) stream.readObject(),
                    (Map) stream.readObject());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            stream.close();
        }
    }

    @NonNull
    @Override
    public String addMember(@NonNull String owner, @NonNull String name, @NonNull String desc, int modifiers) {
        String fullName = getFullMethodName(owner, name, desc);
        mMembers.put(owner, fullName);
        mModifiers.put(fullName, modifiers);
        return fullName;
    }

    @NonNull
    @Override
    public String getMemberReference(@NonNull String className, @NonNull String memberName, @NonNull String desc) {
        return getFullMethodName(className, memberName, desc);
    }

    @Override
    public void addDependency(@NonNull String source, @NonNull String target, @NonNull DependencyType type) {
        Dependency<String> dep = new Dependency<String>(target, type);
        mDependencies.put(source, dep);
    }

    @NonNull
    @Override
    public Set<Dependency<String>> getDependencies(@NonNull String member) {
        return Sets.newHashSet(mDependencies.get(member));
    }

    @NonNull
    @Override
    public Set<String> getMethods(@NonNull String klass) {
        HashSet<String> members = Sets.newHashSet(mMembers.get(klass));
        for (Iterator<String> iterator = members.iterator(); iterator.hasNext(); ) {
            String member = iterator.next();
            if (!isMethod(member)) {
                iterator.remove();
            }
        }
        return members;
    }

    @NonNull
    @Override
    public Set<String> getFields(@NonNull String klass) {
        HashSet<String> members = Sets.newHashSet(mMembers.get(klass));
        for (Iterator<String> iterator = members.iterator(); iterator.hasNext(); ) {
            String member = iterator.next();
            if (isMethod(member)) {
                iterator.remove();
            }
        }
        return members;
    }

    @Override
    public boolean incrementAndCheck(@NonNull String memberOrClass, @NonNull DependencyType type, @NonNull CounterSet counterSet) {
        try {

            return getCounters(counterSet).mReferenceCounters.get(memberOrClass).incrementAndCheck(type);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveState() throws IOException {
        File stateFile = getStateFile(mStateDir);
        FileUtils.deleteIfExists(stateFile);
        Files.createParentDirs(stateFile);

        ObjectOutputStream stream =
                new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(stateFile)));
        try {
            stream.writeObject(mAnnotations);
            stream.writeObject(mClasses);
            stream.writeObject(mDependencies);
            stream.writeObject(mMembers);
            stream.writeObject(mModifiers);
            stream.writeObject(mMultidexCounters.mRoots);
            stream.writeObject(ImmutableMap.copyOf(mMultidexCounters.mReferenceCounters.asMap()));
            stream.writeObject(mShrinkCounters.mRoots);
            stream.writeObject(ImmutableMap.copyOf(mShrinkCounters.mReferenceCounters.asMap()));
        } finally {
            stream.close();
        }
    }

    @Override
    public boolean isReachable(@NonNull String klass, @NonNull CounterSet counterSet) {
        try {
            return getCounters(counterSet).mReferenceCounters.get(klass).isReachable();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAllCodeDependencies(@NonNull String source) {
        Set<Dependency<String>> dependencies = mDependencies.get(source);
        for (Iterator<Dependency<String>> iterator = dependencies.iterator(); iterator.hasNext(); ) {
            if (iterator.next().type == DependencyType.REQUIRED_CODE_REFERENCE) {
                iterator.remove();
            }
        }
    }

    @Override
    @Nullable
    public String getSuperclass(@NonNull String klass) throws ClassLookupException {
        ClassInfo classInfo = mClasses.get(klass);
        if (classInfo == null) {
            throw new ClassLookupException(klass);
        }
        String superclass = classInfo.superclass;

        if (superclass != null && !mClasses.containsKey(superclass)) {
            throw new ClassLookupException(superclass);
        }

        return superclass;
    }

    @Nullable
    @Override
    public String findMatchingMethod(@NonNull String klass, @NonNull String method) {
        // Common case:
        if (mMembers.containsEntry(klass, method)) {
            return method;
        }

        String methodToLookFor = klass + "." + getMemberId(method);
        if (mMembers.containsEntry(klass, methodToLookFor)) {
            return methodToLookFor;
        } else {
            return null;
        }
    }

    @Override
    public boolean isLibraryClass(@NonNull String klass) {
        if (isSdkPackage(klass)) {
            return true;
        }

        ClassInfo classInfo = mClasses.get(klass);
        return classInfo == null || classInfo.isLibraryClass();
    }

    @NonNull
    @Override
    public String[] getInterfaces(String klass) {
        return mClasses.get(klass).interfaces;
    }

    @Override
    public void checkDependencies(ShrinkerLogger shrinkerLogger) {
        Map<String, Dependency<String>> invalidDeps = Maps.newHashMap();

        for (Map.Entry<String, Dependency<String>> entry : mDependencies.entries()) {
            String source = entry.getKey();
            Dependency<String> dep = entry.getValue();
            String target = dep.target;
            if (!target.contains(".")) {
                if (!mClasses.containsKey(target)) {
                    shrinkerLogger.invalidClassReference(source, target);
                    invalidDeps.put(source, entry.getValue());
                }
            } else {
                if (!mMembers.containsEntry(getClassForMember(target), target)) {
                    shrinkerLogger.invalidMemberReference(source, target);
                    invalidDeps.put(source, entry.getValue());
                }
            }
        }

        for (Map.Entry<String, Dependency<String>> entry : invalidDeps.entrySet()) {
            mDependencies.remove(entry.getKey(), entry.getValue());
        }
    }

    @NonNull
    @Override
    public Set<String> getReachableClasses(@NonNull CounterSet counterSet) {
        Set<String> classesToKeep = Sets.newHashSet();
        for (Map.Entry<String, ClassInfo> entry : mClasses.entrySet()) {
            if (entry.getValue().isLibraryClass()) {
                // Skip lib
                continue;
            }
            if (isReachable(entry.getKey(), counterSet)) {
                classesToKeep.add(entry.getKey());
            }
        }

        return classesToKeep;
    }

    @Override
    public File getSourceFile(@NonNull String klass) {
        return mClasses.get(klass).classFile;
    }

    @NonNull
    @Override
    public Set<String> getReachableMembersLocalNames(@NonNull String klass, @NonNull CounterSet counterSet) {
        Set<String> memberIds = Sets.newHashSet();
        for (String member : mMembers.get(klass)) {
            if (isReachable(member, counterSet)) {
                String memberId = getMemberId(member);
                memberIds.add(memberId);
            }
        }

        return memberIds;
    }

    @NonNull
    @Override
    public String getClassForMember(@NonNull String member) {
        return AsmUtils.getClassName(member);
    }

    @NonNull
    @Override
    public String getClassReference(@NonNull String className) {
        checkNotNull(className);
        return className;
    }

    @NonNull
    @Override
    public String addClass(
            @NonNull String name,
            String superName,
            String[] interfaces,
            int modifiers,
            @Nullable File classFile) {
        //noinspection unchecked - ASM API
        ClassInfo classInfo = new ClassInfo(classFile, superName, interfaces);
        mClasses.put(name, classInfo);
        mModifiers.put(name, modifiers);
        return name;
    }

    @NonNull
    @Override
    public Iterable<String> getAllProgramClasses() {
        List<String> classes = Lists.newArrayList();
        for (Map.Entry<String, ClassInfo> entry : mClasses.entrySet()) {
            boolean isProgramClass = entry.getValue().classFile != null;
            if (isProgramClass) {
                classes.add(entry.getKey());
            }
        }

        return classes;
    }

    @NonNull
    @Override
    public String getClassName(@NonNull String klass) {
        return klass;
    }

    @NonNull
    @Override
    public String getMethodNameAndDesc(@NonNull String method) {
        return method.substring(method.indexOf('.') + 1);
    }

    @NonNull
    @Override
    public String getFieldName(@NonNull String field) {
        return field.substring(field.indexOf('.') + 1, field.indexOf(':'));
    }

    @NonNull
    @Override
    public String getFieldDesc(@NonNull String field) {
        return field.substring(field.indexOf(':') + 1);
    }

    @Override
    public int getClassModifiers(@NonNull String klass) {
        return mModifiers.get(klass);
    }

    @Override
    public int getMemberModifiers(@NonNull String member) {
        return mModifiers.get(member);
    }

    @Override
    public void addAnnotation(@NonNull String classOrMember, @NonNull String annotationName) {
        mAnnotations.put(classOrMember, annotationName);
    }

    @NonNull
    @Override
    public Iterable<String> getAnnotations(@NonNull String classOrMember) {
        return mAnnotations.get(classOrMember);
    }

    @Override
    public void addRoots(@NonNull Map<String, DependencyType> symbolsToKeep, @NonNull CounterSet counterSet) {
        getCounters(counterSet).mRoots.putAll(symbolsToKeep);
    }

    @NonNull
    @Override
    public Map<String, DependencyType> getRoots(@NonNull CounterSet counterSet) {
        return ImmutableMap.copyOf(getCounters(counterSet).mRoots);
    }

    @Override
    public void clearCounters(@NonNull WaitableExecutor<Void> executor) {
        getCounters(CounterSet.SHRINK).mReferenceCounters.invalidateAll();
        getCounters(CounterSet.LEGACY_MULTIDEX).mReferenceCounters.invalidateAll();
    }

    @Override
    public String getMemberName(@NonNull String member) {
        return member;
    }

    @Override
    public boolean isClassKnown(@NonNull String klass) {
        return mClasses.containsKey(klass);
    }

    private Counters getCounters(CounterSet counterSet) {
        if (counterSet == CounterSet.SHRINK) {
            return mShrinkCounters;
        } else {
            return mMultidexCounters;
        }
    }

    @NonNull
    private static String getFullMethodName(String className, String methodName, String typeDesc) {
        return className + "." + methodName + ":" + typeDesc;
    }

    @NonNull
    private static String getMemberId(String member) {
        return member.substring(member.indexOf('.') + 1);
    }

    @NonNull
    private static File getStateFile(File dir) {
        return new File(dir, "shrinker.bin");
    }

    private static boolean isMethod(String member) {
        return member.contains("(");
    }

    private static final class ClassInfo implements Serializable {
        final File classFile;
        final String superclass;
        final String[] interfaces;

        private ClassInfo(File classFile, String superclass, String[] interfaces) {
            this.classFile = classFile;
            this.superclass = superclass;
            this.interfaces = interfaces;
        }

        boolean isLibraryClass() {
            return classFile == null;
        }
    }

    private static final class Counters implements Serializable {

        private final LoadingCache<String, Counter> mReferenceCounters;
        private final ConcurrentMap<String, DependencyType> mRoots;

        public Counters(
                ConcurrentMap<String, DependencyType> roots,
                Map<String, Counter> counters) {
            mRoots = roots;

            mReferenceCounters = CacheBuilder.newBuilder()
                    // TODO: set concurrency level?
                    .build(new CacheLoader<String, Counter>() {
                        @Override
                        public Counter load(@NonNull String unused) throws Exception {
                            return new Counter();
                        }
                    });

            mReferenceCounters.putAll(counters);
        }
    }

    private static final class Counter implements Serializable {
        int required = 0;
        int ifClassKept = 0;
        int classIsKept = 0;

        synchronized boolean incrementAndCheck(DependencyType type) {
            boolean before = isReachable();
            switch (type) {
                case REQUIRED_CLASS_STRUCTURE:
                case REQUIRED_CODE_REFERENCE:
                    required++;
                    break;
                case IF_CLASS_KEPT:
                    ifClassKept++;
                    break;
                case CLASS_IS_KEPT:
                    classIsKept++;
                    break;
            }
            boolean after = isReachable();
            return before != after;
        }

        synchronized boolean isReachable() {
            return required > 0 || (ifClassKept > 0 && classIsKept > 0);
        }
    }
}
