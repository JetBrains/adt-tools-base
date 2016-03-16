/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;

import junit.framework.TestCase;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BuiltinIssueRegistryTest extends TestCase {
    public void testNoListResize() {
        BuiltinIssueRegistry registry = new BuiltinIssueRegistry();
        List<Issue> issues = registry.getIssues();
        int issueCount = issues.size();
        assertTrue(Integer.toString(issueCount),
                BuiltinIssueRegistry.INITIAL_CAPACITY >= issueCount);
    }

    @SuppressWarnings("unchecked")
    public void testCapacities() throws IllegalAccessException {
        TestIssueRegistry registry = new TestIssueRegistry();
        for (Scope scope : Scope.values()) {
            EnumSet<Scope> scopeSet = EnumSet.of(scope);
            checkCapacity(registry, scopeSet);
        }

        // Also check the commonly used combinations
        for (Field field : Scope.class.getDeclaredFields()) {
            if (field.getType().isAssignableFrom(EnumSet.class)) {
                checkCapacity(registry, (EnumSet<Scope>) field.get(null));
            }
        }
    }

    public void testUnique() {
        // Check that ids are unique
        Set<String> ids = new HashSet<String>();
        for (Issue issue : new BuiltinIssueRegistry().getIssues()) {
            String id = issue.getId();
            assertTrue("Duplicate id " + id, !ids.contains(id));
            ids.add(id);
        }
    }

    private static void checkCapacity(TestIssueRegistry registry,
            EnumSet<Scope> scopeSet) {
        List<Issue> issuesForScope = registry.getIssuesForScope(scopeSet);
        int requiredSize = issuesForScope.size();
        int capacity = registry.getIssueCapacity(scopeSet);
        if (requiredSize > capacity) {
            fail("For Scope set " + scopeSet + ": capacity " + capacity
                    + " < actual " + requiredSize);
        }
    }

    public void testSimultaneousIssueMapInitialization() throws Exception {
        // Regression test for b/27563821: bogus
        //     Error: Unknown issue id "UseCompoundDrawables"

        final BuiltinIssueRegistry registry1 = new BuiltinIssueRegistry();
        assertNotNull(registry1.getIssue(UseCompoundDrawableDetector.ISSUE.getId()));

        // Step 1: Somebody resets the issue registry (usually the JarFileIssueRegistry
        // because new custom rules have been loaded for some new library dependency
        BuiltinIssueRegistry.reset();

        // Step 2: Thread 1 calls issue registry getIssue(String id)
        // and since the id to issue map is null (because of the above reset)
        // it's computed in double checked locking code. It ends up calling into
        // getIssues(). We'll make the code stall there with a barrier:

        final CyclicBarrier barrier1 = new CyclicBarrier(2);
        final CyclicBarrier barrier2 = new CyclicBarrier(2);
        final BuiltinIssueRegistry registry2 = new BuiltinIssueRegistry() {
            @NonNull
            @Override
            public List<Issue> getIssues() {
                final List<Issue> superList = super.getIssues();
                // Special list constructed such that *iterating* through the list
                // can be interrupted at the beginning. This lets us sequence timings
                // in getIssue(String) such that we can pause the implementation
                // right in the for loop in the middle (between the map construction
                // and assigning the field. Prior to this bug fix, at this point
                // the field would have been initialized and other threads could
                // skip the whole locked region.
                return new ArrayList<Issue>() {
                    @NotNull
                    @Override
                    public Iterator<Issue> iterator() {
                        try {
                            barrier1.await();

                            // With the bug, the second thread would immediately
                            // see the field (pointing to the empty map) and proceed.
                            // In that case the test fails immediately. However, when
                            // the code is working correctly, the second registry can't
                            // access the map while we're in the synchronized block - and
                            // if we wait forever on this barrier, the code will deadlock.
                            // Therefore, we only wait 3 seconds to simulate contention,
                            // and when the await finally times out it will exit the
                            // critical section, finish the map and let other registries
                            // access it.
                            barrier2.await(3, TimeUnit.SECONDS);
                            fail("Incorrect synchronization: other thread should have "
                                    + "blocked in synchronized block and never reached barrier "
                                    + "until timeout");
                        } catch (InterruptedException e) {
                            fail(e.getMessage());
                        } catch (BrokenBarrierException ignore) {
                            // This is expected; see above
                        } catch (TimeoutException ignore) {
                            // This is expected; see above
                        }
                        return superList.listIterator();
                    }
                };
            }
        };

        Thread thread = new Thread() {
            @Override
            public void run() {
                // Trigger computation of the issue map (which will enter the
                // synchronized section and blocking on barrier1 in getIssues()
                registry2.getIssue(UseCompoundDrawableDetector.ISSUE.getId());
            }
        };
        thread.start();

        // Sync this thread with the issue thread such that we know it's inside the
        // getIssue(String) method:
        barrier1.await();

        // Now thread 2 (the UI test thread) comes along and asks for the issue id's
        // (while the other thread is busy computing the map - it's between barrier1 and
        // barrier2 in getIssues() above)
        assertNotNull(registry1.getIssue(UseCompoundDrawableDetector.ISSUE.getId()));

        // All done
        try {
            barrier2.await();
            fail("Incorrect synchronization: getIssue should have blocked until thread1 is done");
        } catch (BrokenBarrierException ignore) {
            // This is expected; see comment in iterator() above
        }
        thread.join();
    }

    private static class TestIssueRegistry extends BuiltinIssueRegistry {
        // Override to make method accessible outside package
        @NonNull
        @Override
        public List<Issue> getIssuesForScope(@NonNull EnumSet<Scope> scope) {
            return super.getIssuesForScope(scope);
        }
    }
}