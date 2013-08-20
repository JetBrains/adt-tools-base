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

package com.android.tools.perflib.vmtrace.viz;

import junit.framework.TestCase;

import java.awt.*;

public class ZoomPanInteractorTest extends TestCase {
    private ZoomPanInteractor mZoomPanInteractor;
    Point src = new Point();
    Point dst = new Point();

    @Override
    protected void setUp() throws Exception {
        mZoomPanInteractor = new ZoomPanInteractor();
    }

    public void testTranslation() {
        mZoomPanInteractor.translateBy(10, -20);

        src.setLocation(1, 2);
        mZoomPanInteractor.getTransform().transform(src, dst);

        assertEquals(11, dst.x);
        assertEquals(-18, dst.y);
    }

    public void testScaleByOrigin() {
        mZoomPanInteractor.zoomBy(4, 5, new Point(0, 0));

        src.setLocation(2, 3);
        mZoomPanInteractor.getTransform().transform(src, dst);

        assertEquals(2 * 4, dst.x);
        assertEquals(3 * 5, dst.y);
    }

    public void testScaleByLocation() {
        mZoomPanInteractor.zoomBy(4, 5, new Point(20, 0));

        src.setLocation(1, 5);
        mZoomPanInteractor.getTransform().transform(src, dst);

        // Zooming 4 times from 20 => origin is now at (-4 * 20 + 20) = -60
        // So x = 1 => -60 + 1*4 = -56
        assertEquals(-56, dst.x);

        // No translation for y, just zoom
        assertEquals(5 * 5, dst.y);
    }
}
