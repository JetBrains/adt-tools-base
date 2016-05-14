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

package com.android.tools.pixelprobe;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The text information for an Image's text layer.
 * Contains information about the data (text) as well as styling.
 */
public final class TextInfo {
    private String mText = "";

    private AffineTransform mTransform = new AffineTransform();
    private final Rectangle2D.Double mBounds = new Rectangle2D.Double();

    private final List<StyleRun> mStyleRuns = new ArrayList<>();
    private final List<ParagraphRun> mParagraphRuns = new ArrayList<>();

    /**
     * A run represents a range of characters in a string.
     */
    public static abstract class Run {
        private int mStart;
        private int mEnd;
        private int mLength;

        private Run(int start, int end) {
            mStart = start;
            mEnd = end;
            mLength = end - start;
        }

        /**
         * Returns the start of the run, as an offset in the
         * text String.
         */
        public int getStart() {
            return mStart;
        }

        /**
         * Returns the end of the run, as an offset in the
         * text String.
         */
        public int getEnd() {
            return mEnd;
        }

        /**
         * Returns the length of the run.
         */
        public int getLength() {
            return mLength;
        }
    }

    /**
     * A style run describes the style of a range of characters
     * in the text data. This allows to vary typeface, font size,
     * etc. in a single text element.
     */
    public static final class StyleRun extends Run {
        private String mFont;
        private float mFontSize;
        private Color mColor;
        private float mTracking;

        private StyleRun(int start, int end) {
            super(start, end);
        }

        /**
         * Returns the name of the font for this run.
         */
        public String getFont() {
            return mFont;
        }

        /**
         * Returns the font size for this run, in points.
         * To convert from points to pixels you must take into
         * account the Image's vertical resolution.
         */
        public float getFontSize() {
            return mFontSize;
        }

        /**
         * Returns the color of this run.
         */
        public Color getColor() {
            return mColor;
        }

        void setStyle(String font, float fontSize, Color color) {
            mFont = font;
            mFontSize = fontSize;
            mColor = color;
        }

        /**
         * Returns the tracking of this run.
         */
        public float getTracking() {
            return mTracking;
        }

        void setTracking(float tracking) {
            mTracking = tracking;
        }
    }

    /**
     * A paragraph run describes the style of a paragraph,
     * identified by range of characters in the text data.
     */
    public static final class ParagraphRun extends Run {
        private Alignment mAlignment = Alignment.LEFT;

        /**
         * The paragraph justification.
         */
        public enum Alignment {
            /** The paragraph is left-aligned. */
            LEFT,
            /** The paragraph is right-aligned. */
            RIGHT,
            /** The paragraph is centered. */
            CENTER,
            /** The paragraph is justified. */
            JUSTIFY
        }

        private ParagraphRun(int start, int end) {
            super(start, end);
        }

        /**
         * Returns the alignment of this paragraph.
         */
        public Alignment getAlignment() {
            return mAlignment;
        }

        void setAlignment(Alignment alignment) {
            mAlignment = alignment;
        }
    }

    TextInfo() {
    }

    /**
     * Returns the transformation associated to this text element.
     * The transformation contains a translation, a scale and a shear.
     * The translation component of this transform describes the origin
     * of the text element in pixels, in absolute Image coordinates.
     * The translation Y component of the transform is relative to the
     * baseline of this text element.
     */
    public AffineTransform getTransform() {
        return mTransform;
    }

    void setTransform(AffineTransform transform) {
        mTransform = transform;
    }

    /**
     * Returns the actual text for this text element as a String.
     */
    public String getText() {
        return mText;
    }

    void setText(String text) {
        mText = text;
    }

    /**
     * Returns the list of style runs for this text element. Each run
     * describes the style of a range of the text String.
     */
    public List<StyleRun> getStyleRuns() {
        return Collections.unmodifiableList(mStyleRuns);
    }

    StyleRun addStyleRun(int start, int end) {
        StyleRun run = new StyleRun(start, end);
        mStyleRuns.add(run);
        return run;
    }

    /**
     * Returns the list of paragraph runs for this text element. Each run
     * describes the style of a paragraph of the text String, identified
     * by a character range.
     */
    public List<ParagraphRun> getParagraphRuns() {
        return Collections.unmodifiableList(mParagraphRuns);
    }

    ParagraphRun addParagraphRun(int start, int end) {
        ParagraphRun run = new ParagraphRun(start, end);
        mParagraphRuns.add(run);
        return run;
    }

    /**
     * Returns the bounds of this text elements, in pixels. The bounds are
     * relative to the translation transform returned by {@link #getTransform()}.
     */
    public Rectangle2D getBounds() {
        return mBounds;
    }

    void setBounds(double left, double top, double right, double bottom) {
        mBounds.setRect(left, top, right - left, bottom - top);
    }

    @Override
    public String toString() {
        return mText;
    }
}
