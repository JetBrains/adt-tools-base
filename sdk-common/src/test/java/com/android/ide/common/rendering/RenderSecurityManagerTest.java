/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.ide.common.rendering;

import com.android.ide.common.res2.RecordingLogger;
import com.google.common.io.Files;

import junit.framework.TestCase;

import java.io.File;
import java.security.Permission;
import java.util.Collections;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;

public class RenderSecurityManagerTest extends TestCase {

    public void testExec() throws Exception {
        assertNull(RenderSecurityManager.getCurrent());

        RenderSecurityManager manager = new RenderSecurityManager(null, null);
        try {
            assertNull(RenderSecurityManager.getCurrent());
            manager.setActive(true);
            assertSame(manager, RenderSecurityManager.getCurrent());
            if (new File("/bin/ls").exists()) {
                Runtime.getRuntime().exec("/bin/ls");
            } else {
                manager.checkExec("/bin/ls");
            }
            fail("Should have thrown security exception");
        } catch (SecurityException exception) {
            assertEquals("Read access not allowed during rendering (/bin/ls)",
                    exception.toString());
            // pass
        } finally {
            manager.dispose();
            assertNull(RenderSecurityManager.getCurrent());
            assertNull(System.getSecurityManager());
        }
    }

    public void testSetSecurityManager() throws Exception {
        RenderSecurityManager manager = new RenderSecurityManager(null, null);
        try {
            manager.setActive(true);
            System.setSecurityManager(null);
            fail("Should have thrown security exception");
        } catch (SecurityException exception) {
            assertEquals("Security access not allowed during rendering", exception.toString());
            // pass
        } finally {
            manager.dispose();
        }
    }

    public void testInvalidRead() throws Exception {
        RenderSecurityManager manager = new RenderSecurityManager(null, null);
        try {
            manager.setActive(true);

            File file = new File(System.getProperty("user.home"));
            //noinspection ResultOfMethodCallIgnored
            file.lastModified();

            fail("Should have thrown security exception");
        } catch (SecurityException exception) {
            assertEquals("Read access not allowed during rendering (" +
                    System.getProperty("user.home") + ")", exception.toString());
            // pass
        } finally {
            manager.dispose();
        }
    }

    public void testReadOk() throws Exception {
        RenderSecurityManager manager = new RenderSecurityManager(null,  null);
        try {
            manager.setActive(true);

            File jdkHome = new File(System.getProperty("java.home"));
            assertTrue(jdkHome.exists());
            //noinspection ResultOfMethodCallIgnored
            File[] files = jdkHome.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        Files.toByteArray(file);
                    }
                }
            }
        } finally {
            manager.dispose();
        }
    }

    public void testProperties() throws Exception {
        RenderSecurityManager manager = new RenderSecurityManager(null, null);
        try {
            manager.setActive(true);

            System.getProperties();

            fail("Should have thrown security exception");
        } catch (SecurityException exception) {
            assertEquals("Property access not allowed during rendering", exception.toString());
            // pass
        } finally {
            manager.dispose();
        }
    }

    public void testExit() throws Exception {
        RenderSecurityManager manager = new RenderSecurityManager(null, null);
        try {
            manager.setActive(true);

            System.exit(-1);

            fail("Should have thrown security exception");
        } catch (SecurityException exception) {
            assertEquals("Exit access not allowed during rendering (-1)", exception.toString());
            // pass
        } finally {
            manager.dispose();
        }
    }

    public void testThread() throws Exception {
        final AtomicBoolean failedUnexpectedly = new AtomicBoolean(false);
        Thread otherThread = new Thread("other") {
            @Override
            public void run() {
                try {
                    assertNull(RenderSecurityManager.getCurrent());
                    System.getProperties();
                } catch (SecurityException e) {
                    failedUnexpectedly.set(true);
                }
            }
        };
        RenderSecurityManager manager = new RenderSecurityManager(null, null);
        try {
            manager.setActive(true);

            // Threads cloned from this one should inherit the same security constraints
            final AtomicBoolean failedAsExpected = new AtomicBoolean(false);
            final Thread renderThread = new Thread("render") {
                @Override
                public void run() {
                    try {
                        System.getProperties();
                    } catch (SecurityException e) {
                        failedAsExpected.set(true);
                    }
                }
            };
            renderThread.start();
            renderThread.join();
            assertTrue(failedAsExpected.get());
            otherThread.start();
            otherThread.join();
            assertFalse(failedUnexpectedly.get());
        } finally {
            manager.dispose();
        }
    }

    public void testActive() throws Exception {
        RenderSecurityManager manager = new RenderSecurityManager(null, null);
        try {
            manager.setActive(true);

            try {
                System.getProperties();
                fail("Should have thrown security exception");
            } catch (SecurityException exception) {
                // pass
            }

            manager.setActive(false);

            try {
                System.getProperties();
            } catch (SecurityException exception) {
                fail(exception.toString());
            }

            manager.setActive(true);

            try {
                System.getProperties();
                fail("Should have thrown security exception");
            } catch (SecurityException exception) {
                // pass
            }
        } finally {
            manager.dispose();
        }
    }

    public void testThread2() throws Exception {
        // Check that when a new thread is created simultaneously from an unrelated
        // thread during rendering, that new thread does not pick up the security manager.
        //
        final CyclicBarrier barrier1 = new CyclicBarrier(2);
        final CyclicBarrier barrier2 = new CyclicBarrier(2);
        final CyclicBarrier barrier3 = new CyclicBarrier(4);
        final CyclicBarrier barrier4 = new CyclicBarrier(4);
        final CyclicBarrier barrier5 = new CyclicBarrier(4);
        final CyclicBarrier barrier6 = new CyclicBarrier(4);

        // First the threads reach barrier1. Then from barrier1 to barrier2, thread1
        // installs the security manager. Then from barrier2 to barrier3, thread2
        // checks that it does not have any security restrictions, and creates thread3.
        // Thread1 will ensure that the security manager is working there, and it will
        // create thread4. Then after barrier3 (where thread3 and thread4 are now also
        // participating) thread3 will ensure that it too has no security restrictions,
        // and thread4 will ensure that it does. At barrier4 the security manager gets
        // uninstalled, and at barrier5 all threads will check that there are no more
        // restrictions. At barrier6 all threads are done.

        final Thread thread1 = new Thread("render") {
            @Override
            public void run() {
                try {
                    barrier1.await();
                    assertNull(RenderSecurityManager.getCurrent());

                    RenderSecurityManager manager = new RenderSecurityManager(null, null);
                    manager.setActive(true);

                    barrier2.await();

                    Thread thread4 = new Thread() {
                        @Override
                        public void run() {
                            try {
                                barrier3.await();

                                try {
                                    System.getProperties();
                                    fail("Should have thrown security exception");
                                } catch (SecurityException e) {
                                    // pass
                                }

                                barrier4.await();
                                barrier5.await();
                                assertNull(RenderSecurityManager.getCurrent());
                                assertNull(System.getSecurityManager());
                                barrier6.await();
                            } catch (InterruptedException e) {
                                fail(e.toString());
                            } catch (BrokenBarrierException e) {
                                fail(e.toString());
                            }
                        }
                    };
                    thread4.start();

                    try {
                        System.getProperties();
                        fail("Should have thrown security exception");
                    } catch (SecurityException e) {
                        // expected
                    }

                    barrier3.await();
                    barrier4.await();
                    manager.dispose();

                    assertNull(RenderSecurityManager.getCurrent());
                    assertNull(System.getSecurityManager());

                    barrier5.await();
                    barrier6.await();

                } catch (InterruptedException e) {
                    fail(e.toString());
                } catch (BrokenBarrierException e) {
                    fail(e.toString());
                }

            }
        };

        final Thread thread2 = new Thread("unrelated") {
            @Override
            public void run() {
                try {
                    barrier1.await();
                    assertNull(RenderSecurityManager.getCurrent());
                    barrier2.await();
                    assertNull(RenderSecurityManager.getCurrent());
                    assertNotNull(System.getSecurityManager());

                    try {
                        System.getProperties();
                    } catch (SecurityException e) {
                        fail("Should not have been affected by security manager");
                    }

                    Thread thread3 = new Thread() {
                        @Override
                        public void run() {
                            try {
                                barrier3.await();

                                try {
                                    System.getProperties();
                                } catch (SecurityException e) {
                                    fail("Should not have been affected by security manager");
                                }

                                barrier4.await();
                                barrier5.await();
                                assertNull(RenderSecurityManager.getCurrent());
                                assertNull(System.getSecurityManager());
                                barrier6.await();

                            } catch (InterruptedException e) {
                                fail(e.toString());
                            } catch (BrokenBarrierException e) {
                                fail(e.toString());
                            }
                        }
                    };
                    thread3.start();

                    barrier3.await();
                    barrier4.await();
                    barrier5.await();
                    assertNull(RenderSecurityManager.getCurrent());
                    assertNull(System.getSecurityManager());
                    barrier6.await();

                } catch (InterruptedException e) {
                    fail(e.toString());
                } catch (BrokenBarrierException e) {
                    fail(e.toString());
                }

            }
        };

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
    }

    public void testDisabled() throws Exception {
        assertNull(RenderSecurityManager.getCurrent());

        RenderSecurityManager manager = new RenderSecurityManager(null, null);
        RenderSecurityManager.sEnabled = false;
        try {
            assertNull(RenderSecurityManager.getCurrent());
            manager.setActive(true);
            assertSame(manager, RenderSecurityManager.getCurrent());
            if (new File("/bin/ls").exists()) {
                Runtime.getRuntime().exec("/bin/ls");
            } else {
                manager.checkExec("/bin/ls");
            }
        } catch (SecurityException exception) {
            fail("Should have been disabled");
        } finally {
            RenderSecurityManager.sEnabled = true;
            manager.dispose();
            assertNull(RenderSecurityManager.getCurrent());
            assertNull(System.getSecurityManager());
        }
    }

    public void testLogger() throws Exception {
        assertNull(RenderSecurityManager.getCurrent());

        final CyclicBarrier barrier1 = new CyclicBarrier(2);
        final CyclicBarrier barrier2 = new CyclicBarrier(2);
        final CyclicBarrier barrier3 = new CyclicBarrier(2);

        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    barrier1.await();
                    barrier2.await();

                    System.setSecurityManager(new SecurityManager() {
                        @Override
                        public String toString() {
                            return "MyTestSecurityManager";
                        }

                        @Override
                        public void checkPermission(Permission permission) {
                        }
                    });

                    barrier3.await();
                    assertNull(RenderSecurityManager.getCurrent());
                    assertNotNull(System.getSecurityManager());
                    assertEquals("MyTestSecurityManager", System.getSecurityManager().toString());
                } catch (InterruptedException e) {
                    fail(e.toString());
                } catch (BrokenBarrierException e) {
                    fail(e.toString());
                }
            }
        };
        thread.start();

        RenderSecurityManager manager = new RenderSecurityManager(null, null);
        RecordingLogger logger = new RecordingLogger();
        manager.setLogger(logger);
        try {
            barrier1.await();
            assertNull(RenderSecurityManager.getCurrent());
            manager.setActive(true);
            assertSame(manager, RenderSecurityManager.getCurrent());
            barrier2.await();
            barrier3.await();

            assertNull(RenderSecurityManager.getCurrent());
            manager.setActive(false);
            assertNull(RenderSecurityManager.getCurrent());

            assertEquals(Collections.singletonList(
                    "RenderSecurityManager being replaced by another thread"),
                    logger.getWarningMsgs());
        } catch (InterruptedException e) {
            fail(e.toString());
        } catch (BrokenBarrierException e) {
            fail(e.toString());
        } finally {
            manager.dispose();
            assertNull(RenderSecurityManager.getCurrent());
            assertNotNull(System.getSecurityManager());
            assertEquals("MyTestSecurityManager", System.getSecurityManager().toString());
            System.setSecurityManager(null);
        }
    }
}
