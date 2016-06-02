/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.repository.impl.manager;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SettingsController;
import com.android.repository.api.SimpleRepositorySource;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeProgressRunner;
import com.android.repository.testframework.FakeRepositorySourceProvider;
import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import junit.framework.TestCase;

import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for {@link RepoManagerImpl}.
 */
public class RepoManagerImplTest extends TestCase {

    private static final String LOCAL_PACKAGE =
            "<repo:repository\n"
            + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
            + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
            + "    <localPackage path=\"foo\" obsolete=\"true\">\n"
            + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
            + "        <revision>\n"
            + "            <major>1</major>\n"
            + "        </revision>\n"
            + "        <display-name>Test package</display-name>\n"
            + "    </localPackage>\n"
            + "</repo:repository>";

    private static final String LOCAL_PACKAGE_2 = "<repo:repository\n"
            + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
            + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
            + "    <localPackage path=\"bar\" obsolete=\"true\">\n"
            + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
            + "        <revision>\n"
            + "            <major>1</major>\n"
            + "        </revision>\n"
            + "        <display-name>Test package 2</display-name>\n"
            + "    </localPackage>\n"
            + "</repo:repository>";

    private static final String REMOTE_REPO = "<repo:repository\n"
            + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
            + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
            + "    <remotePackage path=\"dummy;foo\">\n"
            + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
            + "        <revision>\n"
            + "            <major>2</major>\n"
            + "        </revision>\n"
            + "        <display-name>Test package</display-name>\n"
            + "        <archives>\n"
            + "            <archive>\n"
            + "                <complete>\n"
            + "                    <size>1234</size>\n"
            + "                    <checksum>4321432143214321432143214321432143214321</checksum>\n"
            + "                    <url>http://example.com/arch1</url>\n"
            + "                </complete>\n"
            + "            </archive>\n"
            + "        </archives>\n"
            + "    </remotePackage>\n"
            + "</repo:repository>";

    private static final String REMOTE_REPO_2 = "<repo:repository\n"
            + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
            + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
            + "    <remotePackage path=\"dummy;bar\">\n"
            + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
            + "        <revision>\n"
            + "            <major>3</major>\n"
            + "        </revision>\n"
            + "        <display-name>Test package</display-name>\n"
            + "        <archives>\n"
            + "            <archive>\n"
            + "                <complete>\n"
            + "                    <size>1234</size>\n"
            + "                    <checksum>4321432143214321432143214321432143214321</checksum>\n"
            + "                    <url>http://example.com/arch1</url>\n"
            + "                </complete>\n"
            + "            </archive>\n"
            + "        </archives>\n"
            + "    </remotePackage>\n"
            + "</repo:repository>";

    // test load with local and remote, dummy loaders, callbacks called in order
    public void testLoadOperationsInOrder() throws Exception {
        MockFileOp fop = new MockFileOp();
        final AtomicInteger counter = new AtomicInteger(0);
        RepoManagerImpl.LocalRepoLoaderFactory localFactory =
                new TestLoaderFactory(new OrderTestLoader(1, counter, false));
        RepoManager.RepoLoadedCallback localCallback = new RepoManager.RepoLoadedCallback() {
            @Override
            public void doRun(@NonNull RepositoryPackages packages) {
                assertEquals(2, counter.addAndGet(1));
            }
        };
        RepoManagerImpl.RemoteRepoLoaderFactory remoteFactory =
                new TestLoaderFactory(new OrderTestLoader(3, counter, false));
        RepoManager.RepoLoadedCallback remoteCallback = new RepoManager.RepoLoadedCallback() {
            @Override
            public void doRun(@NonNull RepositoryPackages packages) {
                assertEquals(4, counter.addAndGet(1));
            }
        };
       Runnable errorCallback = new Runnable() {
            @Override
            public void run() {
                fail();
            }
        };

        RepoManagerImpl mgr = new RepoManagerImpl(fop, localFactory, remoteFactory);
        mgr.setLocalPath(new File("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.<RepositorySource>of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        mgr.load(0, ImmutableList.of(localCallback), ImmutableList.of(remoteCallback),
                ImmutableList.of(errorCallback), runner, new FakeDownloader(fop), null, true);

        assertEquals(4, counter.get());
    }

    // test error causes error callbacks to be called
    public void testErrorCallbacks1() throws Exception {
        MockFileOp fop = new MockFileOp();
        final AtomicInteger counter = new AtomicInteger(0);
        RepoManagerImpl.LocalRepoLoaderFactory localFactory =
                new TestLoaderFactory(new OrderTestLoader(1, counter, false));
        RepoManager.RepoLoadedCallback localCallback = new RepoManager.RepoLoadedCallback() {
            @Override
            public void doRun(@NonNull RepositoryPackages packages) {
                assertEquals(2, counter.addAndGet(1));
            }
        };
        RepoManagerImpl.RemoteRepoLoaderFactory remoteFactory =
                new TestLoaderFactory(new OrderTestLoader(3, counter, true));
        RepoManager.RepoLoadedCallback remoteCallback = new RepoManager.RepoLoadedCallback() {
            @Override
            public void doRun(@NonNull RepositoryPackages packages) {
                fail();
            }
        };
        Runnable errorCallback = new Runnable() {
            @Override
            public void run() {
                assertEquals(4, counter.addAndGet(1));
            }
        };

        RepoManagerImpl mgr = new RepoManagerImpl(fop, localFactory, remoteFactory);
        mgr.setLocalPath(new File("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.<RepositorySource>of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        try {
            mgr.load(0, ImmutableList.of(localCallback), ImmutableList.of(remoteCallback),
                    ImmutableList.of(errorCallback), runner, new FakeDownloader(fop), null, true);
        }
        catch (Exception e) {
            // expected
        }
        assertEquals(4, counter.get());

    }

    // test error causes error callbacks to be called
    public void testErrorCallbacks2() throws Exception {
        MockFileOp fop = new MockFileOp();
        final AtomicInteger counter = new AtomicInteger(0);
        RepoManagerImpl.LocalRepoLoaderFactory localFactory =
                new TestLoaderFactory(new OrderTestLoader(1, counter, true));
        RepoManager.RepoLoadedCallback localCallback = new RepoManager.RepoLoadedCallback() {
            @Override
            public void doRun(@NonNull RepositoryPackages packages) {
                fail();
            }
        };
        RepoManagerImpl.RemoteRepoLoaderFactory remoteFactory =
                new TestLoaderFactory(new OrderTestLoader(3, counter, false));
        RepoManager.RepoLoadedCallback remoteCallback = new RepoManager.RepoLoadedCallback() {
            @Override
            public void doRun(@NonNull RepositoryPackages packages) {
                fail();
            }
        };
        Runnable errorCallback = new Runnable() {
            @Override
            public void run() {
                assertEquals(2, counter.addAndGet(1));
            }
        };

        RepoManagerImpl mgr = new RepoManagerImpl(fop, localFactory, remoteFactory);
        mgr.setLocalPath(new File("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.<RepositorySource>of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        try {
            mgr.load(0, ImmutableList.of(localCallback), ImmutableList.of(remoteCallback),
                    ImmutableList.of(errorCallback), runner, new FakeDownloader(fop), null, true);
        }
        catch (Exception e) {
            // expected
        }
        assertEquals(2, counter.get());
    }

    // test multiple loads at same time only kick off one load, and callbacks are invoked
    public void testMultiLoad() throws Exception {
        MockFileOp fop = new MockFileOp();
        final AtomicBoolean localStarted = new AtomicBoolean(false);
        AtomicBoolean localCallback1Run = new AtomicBoolean(false);
        AtomicBoolean localCallback2Run = new AtomicBoolean(false);
        AtomicBoolean remoteCallback1Run = new AtomicBoolean(false);
        AtomicBoolean remoteCallback2Run = new AtomicBoolean(false);
        final Semaphore runLocal = new Semaphore(1);
        runLocal.acquire();
        final Semaphore completeDone = new Semaphore(2);
        completeDone.acquire(2);

        RepoManagerImpl.LocalRepoLoaderFactory localFactory =
                new TestLoaderFactory(new DummyLoader() {
                    @Override
                    protected Map run() {
                        assertTrue(localStarted.compareAndSet(false, true));
                        try {
                            runLocal.acquire();
                        } catch (InterruptedException e) {
                            fail();
                        }
                        return Maps.newHashMap();
                    }
                });
        RepoManager.RepoLoadedCallback localCallback1 = new RunningCallback(localCallback1Run);
        RepoManager.RepoLoadedCallback localCallback2 = new RunningCallback(localCallback2Run);
        RepoManager.RepoLoadedCallback remoteCallback1 = new RunningCallback(remoteCallback1Run) {
            @Override
            public void doRun(@NonNull RepositoryPackages packages) {
                super.doRun(packages);
                completeDone.release();
            }
        };
        RepoManager.RepoLoadedCallback remoteCallback2 = new RunningCallback(remoteCallback2Run) {
            @Override
            public void doRun(@NonNull RepositoryPackages packages) {
                super.doRun(packages);
                completeDone.release();
            }
        };

        Runnable errorCallback = new Runnable() {
            @Override
            public void run() {
                fail();
            }
        };

        RepoManagerImpl mgr = new RepoManagerImpl(fop, localFactory, new TestLoaderFactory());
        mgr.setLocalPath(new File("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.<RepositorySource>of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        mgr.load(0, ImmutableList.of(localCallback1), ImmutableList.of(remoteCallback1),
                ImmutableList.of(errorCallback), runner, new FakeDownloader(fop), null, false);
        mgr.load(0, ImmutableList.of(localCallback2), ImmutableList.of(remoteCallback2),
                ImmutableList.of(errorCallback), runner, new FakeDownloader(fop), null, false);
        runLocal.release();

        if (!completeDone.tryAcquire(10, TimeUnit.SECONDS)) {
            fail();
        }
        assertTrue(localCallback1Run.get());
        assertTrue(localCallback2Run.get());
        assertTrue(remoteCallback1Run.get());
        assertTrue(remoteCallback2Run.get());
    }

    // test timeout makes/doesn't make load happen
    public void testTimeout() throws Exception {
        MockFileOp fop = new MockFileOp();
        final AtomicBoolean localDidRun = new AtomicBoolean(false);
        final AtomicBoolean remoteDidRun = new AtomicBoolean(false);

        TestLoaderFactory localRunningFactory = new TestLoaderFactory(
                new RunningLoader(localDidRun));
        TestLoaderFactory remoteRunningFactory = new TestLoaderFactory(
                new RunningLoader(remoteDidRun));

        RepoManagerImpl mgr = new RepoManagerImpl(fop, localRunningFactory, remoteRunningFactory);
        mgr.setLocalPath(new File("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.<RepositorySource>of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        mgr.load(0, null, null, null, runner, null, null, true);
        assertTrue(localDidRun.compareAndSet(true, false));
        assertFalse(remoteDidRun.get());

        // we shouldn't run because of timeout
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null, null,
                true);

        assertFalse(localDidRun.get());
        assertFalse(remoteDidRun.get());

        // we should run since we've specified a downloader
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner,
                new FakeDownloader(fop), null, true);
        assertTrue(localDidRun.compareAndSet(true, false));
        assertTrue(remoteDidRun.compareAndSet(true, false));

        // now neither should run because of caching
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner,
                new FakeDownloader(fop), null, true);
        assertFalse(localDidRun.get());
        assertFalse(remoteDidRun.get());

        // now we will timeout, so they should run again
        mgr.load(-1, null, null, null, runner, new FakeDownloader(fop), null, true);
        assertTrue(localDidRun.compareAndSet(true, false));
        assertTrue(remoteDidRun.compareAndSet(true, false));
    }

    // test load happens if hash file is newer
    public void testHashFile() throws Exception {
        MockFileOp fop = new MockFileOp();
        final AtomicBoolean localDidRun = new AtomicBoolean(false);

        TestLoaderFactory localRunningFactory = new TestLoaderFactory(
                new RunningLoader(localDidRun) {
                    @Override
                    public long getLatestPackageUpdateTime() {
                        return 1234;
                    }

                    @Nullable
                    @Override
                    public byte[] getLocalPackagesHash() {
                        return "foo".getBytes();
                    }
                });

        RepoManagerImpl mgr = new RepoManagerImpl(fop, localRunningFactory,
                new TestLoaderFactory());
        File repoRoot = new File("/repo");
        mgr.setLocalPath(repoRoot);
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.<RepositorySource>of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        mgr.load(0, null, null, null, runner, null, null, true);
        assertTrue(localDidRun.compareAndSet(true, false));

        File hashFile = new File(repoRoot, RepoManagerImpl.KNOWN_PACKAGES_HASH_FN);
        assertEquals("foo", fop.toString(hashFile,
                Charset.defaultCharset()));

        // test that a newer timestamp causes reload
        fop.setLastModified(hashFile, System.currentTimeMillis() + 100);
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null, null,
                true);
        assertTrue(localDidRun.compareAndSet(true, false));
    }

    // test package change + invalidate makes load happen
    public void testCheckForNewPackages() throws Exception {
        MockFileOp fop = new MockFileOp();
        fop.recordExistingFile("/repo/foo", LOCAL_PACKAGE);
        RepoManager mgr = new RepoManagerImpl(fop);
        mgr.setLocalPath(new File("/repo"));
        FakeProgressRunner runner = new FakeProgressRunner();
        // First time we should load
        assertTrue(
                mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null,
                        null, true));

        fop.recordExistingFile("/repo/bar", LOCAL_PACKAGE_2);
        // while we've created a new package, we didn't scan for it yet
        assertFalse(mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null,
                null, true));

        // now we scan for it
        assertTrue(mgr.reloadLocalIfNeeded(runner.getProgressIndicator()));

        // caching keeps us from loading again
        assertFalse(mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null,
                null, true));
    }


    // test local/remote change listeners
    public void testChangeListeners() throws Exception {
        MockFileOp fop = new MockFileOp();
        fop.recordExistingFile("/repo/foo/package.xml", LOCAL_PACKAGE);
        RepoManager mgr = new RepoManagerImpl(fop);
        mgr.setLocalPath(new File("/repo"));
        FakeProgressRunner runner = new FakeProgressRunner();
        FakeDownloader downloader = new FakeDownloader(fop);
        String repoUrl = "http://example.com/repo.xml";
        downloader.registerUrl(new URL(repoUrl), REMOTE_REPO.getBytes());
        RepositorySourceProvider provider = new FakeRepositorySourceProvider(
                ImmutableList.<RepositorySource>of(
                        new SimpleRepositorySource(repoUrl, "source", true,
                                ImmutableList.of(RepoManager.getGenericModule()), null)));
        mgr.registerSourceProvider(provider);
        mgr.load(-1, null, null, null, runner, downloader, null, true);
        AtomicBoolean localRan = new AtomicBoolean(false);
        AtomicBoolean remoteRan = new AtomicBoolean(false);
        mgr.registerLocalChangeListener(new RunningCallback(localRan));
        mgr.registerRemoteChangeListener(new RunningCallback(remoteRan));

        // load again with no changes
        mgr.load(-1, null, null, null, runner, downloader, null, true);
        assertFalse(localRan.get());
        assertFalse(remoteRan.get());

        // update local and ensure the local listener fired
        fop.recordExistingFile("/repo/bar/package.xml", LOCAL_PACKAGE_2);
        mgr.load(-1, null, null, null, runner, downloader, null, true);
        assertTrue(localRan.compareAndSet(true, false));
        assertFalse(remoteRan.get());

        // update remote and ensure the remote listener fired
        downloader.registerUrl(new URL(repoUrl), REMOTE_REPO_2.getBytes());
        mgr.load(-1, null, null, null, runner, downloader, null, true);
        assertFalse(localRan.get());
        assertTrue(remoteRan.compareAndSet(true, false));
    }

    private static class RunningLoader extends DummyLoader {

        private final AtomicBoolean mDidRun;

        public RunningLoader(AtomicBoolean didRun) {
            mDidRun = didRun;
        }

        @Override
        protected Map run() {
            assertTrue(mDidRun.compareAndSet(false, true));
            return super.run();
        }
    }

    private static class RunningCallback implements RepoManager.RepoLoadedCallback {
        private final AtomicBoolean mDidRun;

        private RunningCallback(AtomicBoolean didRun) {
            mDidRun = didRun;
        }

        @Override
        public void doRun(@NonNull RepositoryPackages packages) {
            assertTrue(mDidRun.compareAndSet(false, true));
        }
    }

    private static class DummyLoader implements LocalRepoLoader, RemoteRepoLoader {

        @Override
        public long getLatestPackageUpdateTime() {
            return 0;
        }

        @NonNull
        @Override
        public Map<String, LocalPackage> getPackages(@NonNull ProgressIndicator progress) {
            return run();
        }

        @Nullable
        @Override
        public byte[] getLocalPackagesHash() {
            return new byte[0];
        }

        @NonNull
        @Override
        public Map<String, RemotePackage> fetchPackages(@NonNull ProgressIndicator progress,
                @NonNull Downloader downloader, @Nullable SettingsController settings) {
            return run();
        }

        protected Map run() {
            return Maps.newHashMap();
        }
    }

    private static class TestLoaderFactory implements RepoManagerImpl.RemoteRepoLoaderFactory,
            RepoManagerImpl.LocalRepoLoaderFactory {

        private final DummyLoader mLoader;

        public TestLoaderFactory(DummyLoader loader) {
            mLoader = loader;
        }

        public TestLoaderFactory() {
            mLoader = new DummyLoader();
        }

        @Override
        @NonNull
        public RemoteRepoLoader createRemoteRepoLoader(@NonNull ProgressIndicator progress) {
            return mLoader;
        }

        @Override
        @NonNull
        public LocalRepoLoader createLocalRepoLoader() {
            return mLoader;
        }
    }

    private static class OrderTestLoader extends DummyLoader {

        private final int mTarget;

        private final AtomicInteger mCounter;

        private final boolean mFail;

        private OrderTestLoader(int target, AtomicInteger counter, boolean fail) {
            mTarget = target;
            mCounter = counter;
            mFail = fail;
        }

        @Override
        protected Map run() {
            assertEquals(mTarget, mCounter.addAndGet(1));
            if (mFail) {
                throw new RuntimeException("expected");
            }
            return Maps.newHashMap();
        }
    }
}
