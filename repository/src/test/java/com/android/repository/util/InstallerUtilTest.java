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
package com.android.repository.util;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.Dependency;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.RepositorySource;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.RevisionType;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakeProgressIndicator;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import junit.framework.TestCase;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link InstallerUtil}.
 */
public class InstallerUtilTest extends TestCase {

    private static final List<Dependency> NONE = ImmutableList.of();

    public void testNoDeps() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        assertEquals(request,
                InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addLocal(new FakePackage("l1", new Revision(1), NONE))
                                .addRemote(r1)
                                .addRemote(new FakePackage("r2", new Revision(1), NONE))
                                .build(), progress));
        progress.assertNoErrorsOrWarnings();
    }

    public void testSimple() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r2")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3")));
        RemotePackage r3 = new FakePackage("r3", new Revision(1), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r1, r2, r3);
        assertEquals(expected,
                InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addLocal(new FakePackage("l1", new Revision(1), NONE))
                                .addRemote(r1)
                                .addRemote(r2)
                                .addRemote(r3)
                                .build(), progress));
        progress.assertNoErrorsOrWarnings();
    }

    public void testLocalSatisfies() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r2")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3")));
        RemotePackage r3 = new FakePackage("r3", new Revision(1), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r1, r2);
        assertEquals(expected,
                InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addLocal(new FakePackage("r3", new Revision(1), NONE))
                                .addRemote(r1)
                                .addRemote(r2)
                                .addRemote(r3)
                                .build(), progress));
        progress.assertNoErrorsOrWarnings();
    }

    public void testUpdateNeeded() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r2")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3", 2, null, null)));
        RemotePackage r3 = new FakePackage("r3", new Revision(2), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r1, r2, r3);
        assertEquals(expected,
                InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addLocal(new FakePackage("r3", new Revision(1), NONE))
                                .addRemote(r1)
                                .addRemote(r2)
                                .addRemote(r3)
                                .build(), progress));
        progress.assertNoErrorsOrWarnings();
    }

    public void testMulti() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r2"), new FakeDependency("r3")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1), NONE);
        RemotePackage r3 = new FakePackage("r3", new Revision(1), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r1, r2, r3);
        assertEquals(expected,
                InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addRemote(r1)
                                .addRemote(r2)
                                .addRemote(r3)
                                .build(), progress));
        progress.assertNoErrorsOrWarnings();
    }

    public void testDag() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r2"), new FakeDependency("r3")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r4")));
        RemotePackage r3 = new FakePackage("r3", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r4")));
        RemotePackage r4 = new FakePackage("r4", new Revision(1), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r1, r2, r3, r4);
        assertEquals(expected,
                InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addRemote(r1)
                                .addRemote(r2)
                                .addRemote(r3)
                                .addRemote(r4)
                                .build(), progress));
        progress.assertNoErrorsOrWarnings();
    }

    public void testCycle() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r2")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3")));
        RemotePackage r3 = new FakePackage("r3", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r1")));

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r1, r2, r3);
        assertEquals(expected,
                InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addRemote(r1)
                                .addRemote(r2)
                                .addRemote(r3)
                                .build(), progress));
        progress.assertNoErrorsOrWarnings();
    }

    public void testMultiRequest() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3")));
        RemotePackage r3 = new FakePackage("r3", new Revision(1), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1, r2);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r1, r2, r3);
        assertEquals(expected,
                InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addRemote(r1)
                                .addRemote(r2)
                                .addRemote(r3)
                                .build(), progress));
        progress.assertNoErrorsOrWarnings();
    }

    public void testBogus() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("bogus")));
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertNull(InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addRemote(r1)
                                .build(), progress));
        assertTrue(!progress.getWarnings().isEmpty());
    }

    private static class RepositoryPackagesBuilder {
        private Multimap<String, RemotePackage> mRemotes = HashMultimap.create();
        private Map<String, LocalPackage> mLocals = Maps.newHashMap();

        public RepositoryPackagesBuilder addLocal(LocalPackage p) {
            mLocals.put(p.getPath(), p);
            return this;
        }

        public RepositoryPackagesBuilder addRemote(RemotePackage p) {
            mRemotes.put(p.getPath(), p);
            return this;
        }

        public RepositoryPackages build() {
            return new RepositoryPackages(mLocals, mRemotes);
        }
    }

    private static class FakeDependency extends Dependency {

        private final String mPath;
        private final RevisionType mRevision;

        public FakeDependency(String path) {
            this(path, null, null, null);
        }

        public FakeDependency(String path, final Integer major, final Integer minor, final Integer micro) {
            mPath = path;
            mRevision = major == null ? null : new RevisionType() {
                @Override
                public int getMajor() {
                    return major;
                }

                @Nullable
                @Override
                public Integer getMicro() {
                    return minor;
                }

                @Nullable
                @Override
                public Integer getMinor() {
                    return micro;
                }
            };
        }

        @NonNull
        @Override
        public String getPath() {
            return mPath;
        }

        @Nullable
        @Override
        public RevisionType getMinRevision() {
            return mRevision;
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static class FakePackage implements LocalPackage, RemotePackage {
        private final String mPath;
        private final Revision mVersion;
        private final Collection<Dependency> mDependencies;

        public FakePackage(String path, Revision version, Collection<Dependency> dependencies) {
            mPath = path;
            mVersion = version;
            mDependencies = dependencies == null ? ImmutableList.<Dependency>of() : dependencies;
        }

        @NonNull
        @Override
        public RepositorySource getSource() {
            return null;
        }

        @Override
        public void setSource(@NonNull RepositorySource source) {}

        @Nullable
        @Override
        public Archive getArchive() {
            return null;
        }

        @Nullable
        @Override
        public TypeDetails getTypeDetails() {
            return null;
        }

        @NonNull
        @Override
        public Revision getVersion() {
            return mVersion;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return null;
        }

        @Nullable
        @Override
        public License getLicense() {
            return null;
        }

        @NonNull
        @Override
        public Collection<Dependency> getAllDependencies() {
            return mDependencies;
        }

        @NonNull
        @Override
        public String getPath() {
            return mPath;
        }

        @Override
        public boolean obsolete() {
            return false;
        }

        @NonNull
        @Override
        public CommonFactory createFactory() {
            return null;
        }

        @Override
        public int compareTo(@NonNull RepoPackage o) {
            return 0;
        }

        @NonNull
        @Override
        public File getLocation() {
            return null;
        }

        @Override
        public void setInstalledPath(@NonNull File root) {}

        @Override
        public String toString() {
            return mPath;
        }
    }

}
