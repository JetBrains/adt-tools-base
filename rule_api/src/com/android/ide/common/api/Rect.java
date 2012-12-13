/*
 * Copyright (C) 2009 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.Beta;


/**
 * Mutable rectangle bounds.
 * <p/>
 * To be valid, w >= 1 and h >= 1.
 * By definition:
 * - right side = x + w - 1.
 * - bottom side = y + h - 1.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
@Beta
public class Rect {
    public int x, y, w, h;

    /** Initialize an invalid rectangle. */
    public Rect() {
    }

    /** Initialize rectangle to the given values. They can be invalid. */
    public Rect(int x, int y, int w, int h) {
        set(x, y, w, h);
    }

    /** Initialize rectangle to the given values. They can be invalid. */
    public Rect(@NonNull Rect r) {
        set(r);
    }

    /** Initialize rectangle to the given values. They can be invalid. */
    @NonNull
    public Rect set(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    /** Initialize rectangle to match the given one. */
    @NonNull
    public Rect set(@NonNull Rect r) {
        set(r.x, r.y, r.w, r.h);
        return this;
    }

    /** Returns a new instance of a rectangle with the same values. */
    @NonNull
    public Rect copy() {
        return new Rect(x, y, w, h);
    }

    /** Returns true if the rectangle has valid bounds, i.e. w>0 and h>0. */
    public boolean isValid() {
        return w > 0 && h > 0;
    }

    /** Returns true if the rectangle contains the x,y coordinates, borders included. */
    public boolean contains(int x, int y) {
        return isValid()
                && x >= this.x
                && y >= this.y
                && x < (this.x + this.w)
                && y < (this.y + this.h);
    }

    /**
     * Returns true if this rectangle intersects the given rectangle.
     * Two rectangles intersect if they overlap.
     * @param other the other rectangle to test
     * @return true if the two rectangles overlap
     */
    public boolean intersects(@Nullable Rect other) {
        if (other == null) {
            return false;
        }
        if (x2() <= other.x
                || other.x2() <= x
                || y2() <= other.y
                || other.y2() <= y) {
            return false;
        }

        return true;
    }

    /** Returns true if the rectangle fully contains the given rectangle */
    public boolean contains(@Nullable Rect rect) {
        return rect != null && x <= rect.x
                && y <= rect.y
                && x2() >= rect.x2()
                && y2() >= rect.y2();
    }

    /** Returns true if the rectangle contains the x,y coordinates, borders included. */
    public boolean contains(@Nullable Point p) {
        return p != null && contains(p.x, p.y);
    }

    /**
     * Moves this rectangle by setting it's x,y coordinates to the new values.
     * @return Returns self, for chaining.
     */
    @NonNull
    public Rect moveTo(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    /**
     * Offsets this rectangle by adding the given x,y deltas to the x,y coordinates.
     * @return Returns self, for chaining.
     */
    @NonNull
    public Rect offsetBy(int x, int y) {
        this.x += x;
        this.y += y;
        return this;
    }

    @NonNull
    public Point getCenter() {
        return new Point(x + (w > 0 ? w / 2 : 0),
                         y + (h > 0 ? h / 2 : 0));
    }

    @NonNull
    public Point getTopLeft() {
        return new Point(x, y);
    }

    @NonNull
    public Point getBottomLeft() {
        return new Point(x,
                         y + (h > 0 ? h : 0));
    }

    @NonNull
    public Point getTopRight() {
        return new Point(x + (w > 0 ? w : 0),
                         y);
    }

    @NonNull
    public Point getBottomRight() {
        return new Point(x + (w > 0 ? w : 0),
                         y + (h > 0 ? h : 0));
    }

    /**
     * Returns the X coordinate of the right hand side of the rectangle
     *
     * @return the X coordinate of the right hand side of the rectangle
     */
    public int x2() {
        return x + w;
    }

    /**
     * Returns the Y coordinate of the bottom of the rectangle
     *
     * @return the Y coordinate of the bottom of the rectangle
     */
    public int y2() {
        return y + h;
    }

    /**
     * Returns the X coordinate of the center of the rectangle
     *
     * @return the X coordinate of the center of the rectangle
     */
    public int centerX() {
        return x + w / 2;
    }

    /**
     * Returns the Y coordinate of the center of the rectangle
     *
     * @return the Y coordinate of the center of the rectangle
     */
    public int centerY() {
        return y + h / 2;
    }

    @Override
    public String toString() {
        return String.format("Rect [(%d,%d)-(%d,%d): %dx%d]", x, y, x + w, y + h, w, h);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Rect) {
            Rect rhs = (Rect) obj;
            // validity must be equal on both sides.
            if (isValid() != rhs.isValid()) {
                return false;
            }
            // an invalid rect is equal to any other invalid rect regardless of coordinates
            if (!isValid() && !rhs.isValid()) {
                return true;
            }

            return this.x == rhs.x && this.y == rhs.y && this.w == rhs.w && this.h == rhs.h;
        }

        return false;
    }

    @Override
    public int hashCode() {
        int hc = x;
        hc ^= ((y >>  8) & 0x0FFFFFF) | ((y & 0x00000FF) << 24);
        hc ^= ((w >> 16) & 0x000FFFF) | ((w & 0x000FFFF) << 16);
        hc ^= ((h >> 24) & 0x00000FF) | ((h & 0x0FFFFFF) <<  8);
        return hc;
    }

    /**
     * Returns the center point in the rectangle
     *
     * @return the center point in the rectangle
     */
    @NonNull
    public Point center() {
        return new Point(x + w / 2, y + h / 2);
    }
}
