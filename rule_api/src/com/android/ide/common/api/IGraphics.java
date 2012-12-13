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

import com.android.annotations.NonNull;
import com.google.common.annotations.Beta;

import java.util.List;

/**
 * Represents a graphical context that rules can use to draw on the canvas.
 * <p/>
 * The wrapper GC is only valid during the context of a paint operation.
 * This means {@link IViewRule}s should not cache this object and call it at
 * just about any time, it is only valid during a call that actually receives
 * the GC wrapper.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
@Beta
public interface IGraphics {

    /**
     * Draws a line between 2 points, using the current foreground color and
     * alpha.
     */
    void drawLine(int x1, int y1, int x2, int y2);

    /**
     * Draws a line between 2 points, using the current foreground color and
     * alpha.
     */
    void drawLine(@NonNull Point p1, @NonNull Point p2);

    /**
     * Draws an arrow from (x1, y1) to (x2, y2).
     *
     * @param x1 The x coordinate of the beginning of the arrow
     * @param y1 The y coordinate of the beginning of the arrow
     * @param x2 The x coordinate of the end (point) of the arrow
     * @param y2 The y coordinate of the end (point) of the arrow
     * @param size The size of the arrowhead
     */
    void drawArrow(int x1, int y1, int x2, int y2, int size);

    /**
     * Draws a dot at the given position.
     *
     * @param x The x coordinate of the dot
     * @param y The y coordinate of the dot
     */
    void drawPoint(int x, int y);

    /**
     * Draws a rectangle outline between 2 points, using the current foreground
     * color and alpha.
     */
    void drawRect(int x1, int y1, int x2, int y2);

    /**
     * Draws a rectangle outline between 2 points, using the current foreground
     * color and alpha.
     */
    void drawRect(@NonNull Point p1, @NonNull Point p2);

    /**
     * Draws a rectangle outline between 2 points, using the current foreground
     * color and alpha.
     */
    void drawRect(@NonNull Rect r);

    /**
     * Fills a rectangle outline between 2 points, using the current background
     * color and alpha.
     */
    void fillRect(int x1, int y1, int x2, int y2);

    /**
     * Fills a rectangle outline between 2 points, using the current background
     * color and alpha.
     */
    void fillRect(@NonNull Point p1, @NonNull Point p2);

    /**
     * Fills a rectangle outline between 2 points, using the current background
     * color and alpha.
     */
    void fillRect(@NonNull Rect r);

    /**
     * Draws the given string, using the current foreground color. No tab
     * expansion or carriage return processing will be performed.
     *
     * @param string the string to be drawn.
     * @param x the x coordinate of the top left corner of the text.
     * @param y the y coordinate of the top left corner of the text.
     */
    void drawString(@NonNull String string, int x, int y);

    /**
     * Draws the given string, using the current foreground color. No tab
     * expansion or carriage return processing will be performed.
     *
     * @param string the string to be drawn.
     * @param topLeft the top left corner of the text.
     */
    void drawString(@NonNull String string, @NonNull Point topLeft);

    /**
     * Draw the given strings, using the current stroke color and alpha for the
     * text, and the current fill color and alpha for a rectangle behind the
     * bounding box fitting all the lines of text. Each subsequent string is
     * drawn on consecutive lines below the previous string.
     *
     * @param x The left edge to start each string at
     * @param y The top position of the first string; subsequent strings are
     *            painted on lines below
     * @param strings An array of labels to be displayed (should not be null).
     *            The actual String used is the {@link Object#toString()} value
     *            of each list item.
     */
    void drawBoxedStrings(int x, int y, @NonNull List<?> strings);

    /**
     * Set up the graphics context to use the given style for subsequent drawing
     * operations.
     *
     * @param style The drawing style to be used. May not be null.
     */
    void useStyle(@NonNull DrawingStyle style);

    /**
     * Registers a color using 0x00rrggbb where each component is 0..0xFF.
     * <p/>
     * Transparency is handled separately using {@link #setAlpha(int)}.
     * <p/>
     * If the same color is registered twice, the same object will be returned.
     * <p/>
     * NOTE: It's preferable to use {@link #useStyle(DrawingStyle)} if possible
     * to ensure that your colors work properly across multiple current and
     * future themes.
     */
    @NonNull
    IColor registerColor(int rgb);

    /**
     * Returns the height, in pixels, of the default font.
     */
    int getFontHeight();

    /**
     * Returns the current foreground color.
     * The foreground color is used for drawing operations including when text is drawn.
     */
    @NonNull
    IColor getForeground();

    /**
     * Sets the foreground color. The foreground color is used for drawing
     * operations including when text is drawn.
     */
    void setForeground(@NonNull IColor color);

    /**
     * Returns the current background color. The background color is used for
     * fill operations.
     */
    @NonNull
    IColor getBackground();

    /**
     * Sets the background color. The background color is used for fill
     * operations.
     */
    void setBackground(@NonNull IColor color);

    /**
     * Returns the current alpha value (varies between 0 for transparent and 255
     * for opaque).
     *
     * @return The current alpha value in use
     */
    int getAlpha();

    /**
     * Sets the receiver's alpha value which must be
     * between 0 (transparent) and 255 (opaque).
     * <p>
     * This operation requires the operating system's advanced
     * graphics subsystem which may not be available on some
     * platforms.
     * <p>
     * TODO: Consider removing this method; it will usually be ignored because
     * most graphics operations apply the alpha from the current drawing style
     */
    void setAlpha(int alpha);

    /**
     * A line style for {@link IGraphics#setLineStyle(LineStyle)}.
     */
    enum LineStyle {
        /** Style for solid lines. */
        LINE_SOLID,
        /** Style for dashed lines. */
        LINE_DASH,
        /** Style for dotted lines. */
        LINE_DOT,
        /** Style for alternating dash-dot lines. */
        LINE_DASHDOT,
        /** Style for dash-dot-dot lines. */
        LINE_DASHDOTDOT
    }

    /**
     * Sets the current line style.
     */
    void setLineStyle(@NonNull LineStyle style);

    /**
     * Sets the width that will be used when drawing lines.
     * The operation is ignored if <var>width</var> is less than 1.
     */
    void setLineWidth(int width);
}
