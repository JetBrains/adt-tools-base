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

package com.android.repository.impl.meta;

import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.testframework.FakePackage;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import junit.framework.TestCase;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link RepositoryPackages}
 */
public class RepositoryPackagesTest extends TestCase {

    private RepositoryPackages mPackages;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Map<String, LocalPackage> locals = Maps.newHashMap();
        Map<String, RemotePackage> remotes = Maps.newHashMap();

        // p1 has no corresponding remote
        locals.put("p1", new FakePackage("p1", new Revision(1), null));

        // p2 has an updated remote
        locals.put("p2", new FakePackage("p2", new Revision(1), null));
        remotes.put("p2", new FakePackage("p2", new Revision(2), null));

        // p3 has a non-updated remote
        locals.put("p3", new FakePackage("p3", new Revision(1), null));
        remotes.put("p3", new FakePackage("p3", new Revision(1), null));

        // p4 is only remote
        remotes.put("p4", new FakePackage("p4", new Revision(1), null));

        mPackages = new RepositoryPackages(locals, remotes);
    }

    public void testConsolidated() throws Exception {
        Map<String, UpdatablePackage> consolidated = mPackages.getConsolidatedPkgs();
        assertEquals(4, consolidated.size());

        UpdatablePackage updatable = consolidated.get("p1");
        assertFalse(updatable.isUpdate());
        assertTrue(updatable.hasLocal());
        assertFalse(updatable.hasRemote());
        assertEquals(new Revision(1), updatable.getRepresentative().getVersion());
        assertEquals("p1", updatable.getRepresentative().getPath());

        updatable = consolidated.get("p2");
        assertTrue(updatable.isUpdate());
        assertTrue(updatable.hasLocal());
        assertTrue(updatable.hasRemote());
        assertEquals(new Revision(1), updatable.getLocal().getVersion());
        assertEquals(new Revision(2), updatable.getRemote().getVersion());
        assertEquals("p2", updatable.getRepresentative().getPath());

        updatable = consolidated.get("p3");
        assertFalse(updatable.isUpdate());
        assertTrue(updatable.hasLocal());
        assertTrue(updatable.hasRemote());
        assertEquals(new Revision(1), updatable.getRepresentative().getVersion());
        assertEquals("p3", updatable.getRepresentative().getPath());

        updatable = consolidated.get("p4");
        assertFalse(updatable.isUpdate());
        assertFalse(updatable.hasLocal());
        assertTrue(updatable.hasRemote());
        assertEquals(new Revision(1), updatable.getRemote().getVersion());
        assertEquals("p4", updatable.getRepresentative().getPath());
    }

    public void testNew() {
        Set<RemotePackage> news = mPackages.getNewPkgs();

        assertEquals(1, news.size());
        assertEquals("p4", news.iterator().next().getPath());
    }

    public void testUpdates() {
        Set<UpdatablePackage> updates = mPackages.getUpdatedPkgs();

        assertEquals(1, updates.size());
        assertEquals("p2", updates.iterator().next().getRepresentative().getPath());
    }

    public void testPrefixes() {
        Map<String, LocalPackage> locals = Maps.newHashMap();
        Map<String, RemotePackage> remotes = Maps.newHashMap();

        FakePackage p1 = new FakePackage("a;b;c", new Revision(1), null);
        locals.put("a;b;c", p1);
        remotes.put("a;b;c", p1);
        FakePackage p2 = new FakePackage("a;b;d", new Revision(1), null);
        locals.put("a;b;d", p2);
        remotes.put("a;b;d", p2);
        FakePackage p3 = new FakePackage("a;c", new Revision(1), null);
        locals.put("a;c", p3);
        remotes.put("a;c", p3);
        FakePackage p4 = new FakePackage("d", new Revision(1), null);
        locals.put("d", p4);
        remotes.put("d", p4);
        FakePackage localOnly = new FakePackage("l", new Revision(1), null);
        locals.put("l", localOnly);
        FakePackage remoteOnly = new FakePackage("r", new Revision(1), null);
        remotes.put("r", remoteOnly);

        RepositoryPackages packages = new RepositoryPackages();
        packages.setLocalPkgInfos(locals);
        packages.setRemotePkgInfos(remotes);

        Collection<LocalPackage> localPackages = packages.getLocalPackagesForPrefix("a");
        assertEquals(3, localPackages.size());
        assertTrue(localPackages.containsAll(Sets.newHashSet(p1, p2, p3)));

        Collection<RemotePackage> remotePackages = packages.getRemotePackagesForPrefix("a");
        assertEquals(3, remotePackages.size());
        assertTrue(remotePackages.containsAll(Sets.newHashSet(p1, p2, p3)));

        localPackages = packages.getLocalPackagesForPrefix("a;b");
        assertEquals(2, localPackages.size());
        assertTrue(localPackages.containsAll(Sets.newHashSet(p1, p2)));

        remotePackages = packages.getRemotePackagesForPrefix("a;b");
        assertEquals(2, remotePackages.size());
        assertTrue(remotePackages.containsAll(Sets.newHashSet(p1, p2)));

        localPackages = packages.getLocalPackagesForPrefix("a;b;c");
        assertEquals(1, localPackages.size());
        assertTrue(localPackages.contains(p1));

        remotePackages = packages.getRemotePackagesForPrefix("a;b;c");
        assertEquals(1, remotePackages.size());
        assertTrue(remotePackages.contains(p1));

        localPackages = packages.getLocalPackagesForPrefix("a;b;f");
        assertEquals(0, localPackages.size());

        remotePackages = packages.getRemotePackagesForPrefix("a;b;f");
        assertEquals(0, remotePackages.size());

        localPackages = packages.getLocalPackagesForPrefix("l");
        assertEquals(1, localPackages.size());
        assertTrue(localPackages.contains(localOnly));

        remotePackages = packages.getRemotePackagesForPrefix("l");
        assertEquals(0, remotePackages.size());
        assertFalse(remotePackages.contains(localOnly));

        localPackages = packages.getLocalPackagesForPrefix("r");
        assertEquals(0, localPackages.size());
        assertFalse(localPackages.contains(localOnly));

        remotePackages = packages.getRemotePackagesForPrefix("r");
        assertEquals(1, remotePackages.size());
        assertTrue(remotePackages.contains(localOnly));
    }

}
