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
package com.android.tools.perflib.heap.analysis;

import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import gnu.trove.TObjectProcedure;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

public class DominatorOptimizationTest extends TestCase {
    public void testCorrectness() throws IOException {
        File file = new File(getClass().getResource("/native_allocation.android-hprof").getFile());

        Snapshot groundTruthSnapshot = Snapshot.createSnapshot(new MemoryMappedFileBuffer(file));
        groundTruthSnapshot.prepareDominatorComputation();
        groundTruthSnapshot.doComputeDominators(new ConvergingDominators(groundTruthSnapshot));

        Snapshot optimizedSnapshot = Snapshot.createSnapshot(new MemoryMappedFileBuffer(file));
        optimizedSnapshot.prepareDominatorComputation();
        optimizedSnapshot.doComputeDominators(new LinkEvalDominators(optimizedSnapshot));

        for (Heap groundTruthHeap : groundTruthSnapshot.getHeaps()) {
            int heapId = groundTruthHeap.getId();
            final Heap optimizedHeap = optimizedSnapshot.getHeap(heapId);
            assertNotNull(optimizedHeap);

            groundTruthHeap.forEachInstance(new TObjectProcedure<Instance>() {
                @Override
                public boolean execute(Instance groundTruthInstance) {
                    Instance optimizedInstance = optimizedHeap.getInstance(groundTruthInstance.getId());
                    assertNotNull(optimizedInstance);
                    assertEquals(groundTruthInstance.isReachable(), optimizedInstance.isReachable());
                    if (groundTruthInstance.isReachable()) {
                        assertNotNull(groundTruthInstance.getImmediateDominator());
                        assertNotNull(optimizedInstance.getImmediateDominator());
                        assertTrue(groundTruthInstance.getTopologicalOrder() >
                                   groundTruthInstance.getImmediateDominator().getTopologicalOrder());
                        assertEquals(groundTruthInstance.getImmediateDominator().getId(),
                                     optimizedInstance.getImmediateDominator().getId());
                        assertEquals(groundTruthInstance.getTotalRetainedSize(),
                                     optimizedInstance.getTotalRetainedSize());
                    } else {
                        assertNull(groundTruthInstance.getImmediateDominator());
                        assertEquals(groundTruthInstance.getImmediateDominator(),
                                     optimizedInstance.getImmediateDominator());
                        assertEquals(groundTruthInstance.getSize(),
                                     groundTruthInstance.getTotalRetainedSize());
                        assertEquals(groundTruthInstance.getSize(),
                                     optimizedInstance.getTotalRetainedSize());
                    }
                    return true;
                }
            });
        }

        groundTruthSnapshot.dispose();
        optimizedSnapshot.dispose();
    }
}
