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

import com.android.repository.Revision;
import com.android.repository.api.Dependency;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeDependency;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import junit.framework.TestCase;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link InstallerUtil}.
 */
public class InstallerUtilTest extends TestCase {

    private static final List<Dependency> NONE = ImmutableList.of();

    /**
     * Simple case: a package requires itself, even if has no dependencies set.
     */
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

    /**
     * Simple chain of dependencies, r1->r2->r3. Should be returned in reverse order.
     */
    public void testSimple() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r2")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3")));
        RemotePackage r3 = new FakePackage("r3", new Revision(1), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r3, r2, r1);
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

    /**
     * Request r1 and r1. The latest version of r1 is installed so only r2 is returned.
     */
    public void testLocalInstalled() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1), NONE);
        RemotePackage r2 = new FakePackage("r2", new Revision(1), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1, r2);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r2);
        assertEquals(expected,
                InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addLocal(new FakePackage("r1", new Revision(1), NONE))
                                .addRemote(r1)
                                .addRemote(r2)
                                .build(), progress));
        progress.assertNoErrorsOrWarnings();
    }

    /**
     * Request r1 and r1. r1 is installed but there is an update for it available, and so both
     * r1 and r2 are returned.
     */
    public void testLocalInstalledWithUpdate() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(2), NONE);
        RemotePackage r2 = new FakePackage("r2", new Revision(1), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1, r2);
        List<RemotePackage> result = InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addLocal(new FakePackage("r1", new Revision(1), NONE))
                                .addRemote(r1)
                                .addRemote(r2)
                                .build(), progress);
        assertTrue(result.get(0).equals(r1) || result.get(1).equals(r1));
        assertTrue(result.get(0).equals(r2) || result.get(1).equals(r2));
        progress.assertNoErrorsOrWarnings();
    }

    /**
     * Dependency chain r1->r2->r3, but r3 is already installed with sufficient version, and so is
     * not returned.
     */
    public void testLocalSatisfies() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r2")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3", 1, 1, 1)));
        RemotePackage r3 = new FakePackage("r3", new Revision(1), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r2, r1);
        assertEquals(expected,
                InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addLocal(new FakePackage("r3", new Revision(2), NONE))
                                .addRemote(r1)
                                .addRemote(r2)
                                .addRemote(r3)
                                .build(), progress));
        progress.assertNoErrorsOrWarnings();
    }

    /**
     * Dependency chain r1->r2->r3, but r3 is already installed with no required version
     * specified, and so is not returned.
     */
    public void testLocalSatisfiesNoVersion() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r2")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3")));
        RemotePackage r3 = new FakePackage("r3", new Revision(1), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r2, r1);
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

    /**
     * Dependency chain r1->r2->r3, and r3 is installed but doesn't meet the version requirement.
     */
    public void testUpdateNeeded() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r2")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3", 2, null, null)));
        RemotePackage r3 = new FakePackage("r3", new Revision(2), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r3, r2, r1);
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

    /**
     * r1->{r2, r3}. All should be returned, with r1 last.
     */
    public void testMulti() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r2"), new FakeDependency("r3")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1), NONE);
        RemotePackage r3 = new FakePackage("r3", new Revision(1), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        List<RemotePackage> result = InstallerUtil.computeRequiredPackages(request,
                new RepositoryPackagesBuilder()
                        .addRemote(r1)
                        .addRemote(r2)
                        .addRemote(r3)
                        .build(), progress);
        assertTrue(result.get(0).equals(r2) || result.get(1).equals(r2));
        assertTrue(result.get(0).equals(r3) || result.get(1).equals(r3));
        assertEquals(r1, result.get(2));
        progress.assertNoErrorsOrWarnings();
    }

    /**
     * r1->{r2, r3}->r4. All should be returned, with r4 first and r1 last.
     */
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
        List<RemotePackage> result = InstallerUtil.computeRequiredPackages(request,
                new RepositoryPackagesBuilder()
                        .addRemote(r1)
                        .addRemote(r2)
                        .addRemote(r3)
                        .addRemote(r4)
                        .build(), progress);
        assertEquals(r4, result.get(0));
        assertTrue(result.get(1).equals(r2) || result.get(2).equals(r2));
        assertTrue(result.get(1).equals(r3) || result.get(2).equals(r3));
        assertEquals(r1, result.get(3));
        progress.assertNoErrorsOrWarnings();
    }

    /**
     * r1->r2->r3->r1. All should be returned, in undefined order.
     */
    public void testCycle() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r2")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3")));
        RemotePackage r3 = new FakePackage("r3", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r1")));

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        Set<RemotePackage> expected = Sets.newHashSet(r1, r2, r3);
        // Don't have any guarantee of order in this case.
        assertEquals(expected,
                Sets.newHashSet(InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addRemote(r1)
                                .addRemote(r2)
                                .addRemote(r3)
                                .build(), progress)));
        progress.assertNoErrorsOrWarnings();
    }

    /**
     * r1->r2->r3->r4->r3. All should be returned, with [r2, r1] last.
     */
    public void testCycle2() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r2")));

        RemotePackage r2 = new FakePackage("r2", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3")));
        RemotePackage r3 = new FakePackage("r3", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r4")));
        RemotePackage r4 = new FakePackage("r4", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3")));

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        List<RemotePackage> result =
                InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addRemote(r1)
                                .addRemote(r2)
                                .addRemote(r3)
                                .addRemote(r4)
                                .build(), progress);
        assertTrue(result.get(0).equals(r3) || result.get(1).equals(r3));
        assertTrue(result.get(0).equals(r4) || result.get(1).equals(r4));
        assertEquals(r2, result.get(2));
        assertEquals(r1, result.get(3));
        progress.assertNoErrorsOrWarnings();
    }

    /**
     * {r1, r2}->r3. Request both r1 and r2. All should be returned, with r3 first.
     */
    public void testMultiRequest() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3")));
        RemotePackage r3 = new FakePackage("r3", new Revision(1), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1, r2);
        List<RemotePackage> result =
                InstallerUtil.computeRequiredPackages(request,
                        new RepositoryPackagesBuilder()
                                .addRemote(r1)
                                .addRemote(r2)
                                .addRemote(r3)
                                .build(), progress);
        assertEquals(r3, result.get(0));
        assertTrue(result.get(1).equals(r1) || result.get(2).equals(r1));
        assertTrue(result.get(1).equals(r2) || result.get(2).equals(r2));

        progress.assertNoErrorsOrWarnings();
    }

    /**
     * r1->bogus. Null should be returned, and there should be an error.
     */
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

    /**
     * r1->r2->r3. r3 is installed, but a higher version is required and not available. Null should
     * be returned, and there should be an error.
     */
    public void testUpdateNotAvailable() throws Exception {
        RemotePackage r1 = new FakePackage("r1", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r2")));
        RemotePackage r2 = new FakePackage("r2", new Revision(1),
                ImmutableList.<Dependency>of(new FakeDependency("r3", 4, null, null)));
        RemotePackage r3 = new FakePackage("r3", new Revision(2), NONE);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        assertNull(InstallerUtil.computeRequiredPackages(request,
                new RepositoryPackagesBuilder()
                        .addLocal(new FakePackage("r3", new Revision(1), NONE))
                        .addRemote(r1)
                        .addRemote(r2)
                        .addRemote(r3)
                        .build(), progress));
        assertTrue(!progress.getWarnings().isEmpty());
    }

    public void testInstallInChild() throws Exception {
        MockFileOp fop = new MockFileOp();
        fop.recordExistingFile("/sdk/foo/package.xml",
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "    <localPackage path=\"foo\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>The first Android platform ever</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:repository>");
        RepoManager mgr = new RepoManagerImpl(fop);
        mgr.setLocalPath(new File("/sdk"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mgr.loadSynchronously(0, progress, null, null);
        assertFalse(InstallerUtil.checkValidPath(new File("/sdk/foo/bar"), mgr, progress));
        assertFalse(progress.getWarnings().isEmpty());
    }

    public void testInstallInParent() throws Exception {
        MockFileOp fop = new MockFileOp();
        fop.recordExistingFile("/sdk/foo/bar/package.xml",
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "    <localPackage path=\"foo;bar\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>The first Android platform ever</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:repository>");
        RepoManager mgr = new RepoManagerImpl(fop);
        mgr.setLocalPath(new File("/sdk"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mgr.loadSynchronously(0, progress, null, null);
        assertFalse(InstallerUtil.checkValidPath(new File("/sdk/foo"), mgr, progress));
        assertFalse(progress.getWarnings().isEmpty());
    }

    public void testInstallSeparately() throws Exception {
        MockFileOp fop = new MockFileOp();
        fop.recordExistingFile("/sdk/foo2/package.xml",
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "    <localPackage path=\"foo2\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>The first Android platform ever</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:repository>");
        RepoManager mgr = new RepoManagerImpl(fop);
        mgr.setLocalPath(new File("/sdk"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mgr.loadSynchronously(0, progress, null, null);
        assertTrue(InstallerUtil.checkValidPath(new File("/sdk/foo"), mgr, progress));
        progress.assertNoErrorsOrWarnings();
    }

    public void testInstallSeparately2() throws Exception {
        MockFileOp fop = new MockFileOp();
        fop.recordExistingFile("/sdk/foo/package.xml",
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "    <localPackage path=\"foo\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>The first Android platform ever</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:repository>");
        RepoManager mgr = new RepoManagerImpl(fop);
        mgr.setLocalPath(new File("/sdk"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mgr.loadSynchronously(0, progress, null, null);
        assertTrue(InstallerUtil.checkValidPath(new File("/sdk/foo2"), mgr, progress));
        progress.assertNoErrorsOrWarnings();
    }

    private static class RepositoryPackagesBuilder {
        private Map<String, RemotePackage> mRemotes = Maps.newHashMap();
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


}
