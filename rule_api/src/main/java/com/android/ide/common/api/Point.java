/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.common.api;

import com.google.common.annotations.Beta;
import com.android.annotations.NonNull;


/**
 * Mutable point.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
@Beta
public class Point {
    public int x, y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Point(@NonNull Point p) {
        x = p.x;
        y = p.y;
    }

    /** Sets the point to the given coordinates. */
    public void set(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /** Returns a new instance of a point with the same values. */
    @NonNull
    public Point copy() {
        return new Point(x, y);
    }

    /**
     * Offsets this point by adding the given x,y deltas to the x,y coordinates.
     * @return Returns self, for chaining.
     */
    @NonNull
    public Point offsetBy(int x, int y) {
        this.x += x;
        this.y += y;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Point) {
            Point rhs = (Point) obj;
            return this.x == rhs.x && this.y == rhs.y;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int h = x ^ ((y >> 16) & 0x0FFFF) ^ ((y & 0x0FFFF) << 16);
        return h;
    }

    @Override
    public String toString() {
        return String.format("Point [%dx%d]", x, y);
    }
}
