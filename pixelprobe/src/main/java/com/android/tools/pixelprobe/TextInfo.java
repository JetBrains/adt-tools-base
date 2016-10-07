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

import com.android.tools.pixelprobe.util.Lists;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The text information for an image's text layer.
 * Contains information about the data (text) as well as styling.
 */
public final class TextInfo {
    private final String text;

    private final AffineTransform transform;
    private final Rectangle2D.Double bounds;

    private final List<StyleRun> styleRuns;
    private final List<ParagraphRun> paragraphRuns;

    /**
     * Different types of alignments for paragraphs.
     */
    public enum Alignment {
        /**
         * The paragraph is left-aligned.
         */
        LEFT,
        /**
         * The paragraph is right-aligned.
         */
        RIGHT,
        /**
         * The paragraph is centered.
         */
        CENTER,
        /**
         * The paragraph is justified.
         */
        JUSTIFY
    }

    /**
     * A run represents a range of characters in a string.
     */
    public static abstract class Run {
        private int start;
        private int end;
        private int length;

        Run(int start, int end) {
            this.start = start;
            this.end = end;
            length = end - start;
        }

        /**
         * Returns the start of the run, as an offset in the
         * text String.
         */
        public int getStart() {
            return start;
        }

        /**
         * Returns the end of the run, as an offset in the
         * text String.
         */
        public int getEnd() {
            return end;
        }

        /**
         * Returns the length of the run.
         */
        public int getLength() {
            return length;
        }
    }

    /**
     * A style run describes the style of a range of characters
     * in the text data. This allows to vary typeface, font size,
     * etc. in a single text element.
     */
    public static final class StyleRun extends Run {
        private final String font;
        private final float fontSize;
        private final Paint paint;
        private final float tracking;

        StyleRun(Builder builder) {
            super(builder.start, builder.end);

            font = builder.font;
            fontSize = builder.fontSize;
            paint = builder.paint;
            tracking = builder.tracking;
        }

        /**
         * Returns the name of the font for this run.
         */
        public String getFont() {
            return font;
        }

        /**
         * Returns the font size for this run, in points.
         * To convert from points to pixels you must take into
         * account the Image's vertical resolution.
         */
        public float getFontSize() {
            return fontSize;
        }

        /**
         * Returns the paint of this run.
         */
        public Paint getPaint() {
            return paint;
        }

        /**
         * Returns the tracking of this run.
         */
        public float getTracking() {
            return tracking;
        }

        @SuppressWarnings("UseJBColor")
        public static final class Builder {
            final int start;
            final int end;

            String font = "arial";
            float fontSize = 12.0f;
            Paint paint = Color.BLACK;
            float tracking = 0.0f;

            public Builder(int start, int end) {
                this.start = start;
                this.end = end;
            }

            public Builder font(String font) {
                this.font = font;
                return this;
            }

            public Builder fontSize(float fontSize) {
                this.fontSize = fontSize;
                return this;
            }

            public Builder paint(Paint paint) {
                this.paint = paint;
                return this;
            }

            public Builder tracking(float tracking) {
                this.tracking = tracking;
                return this;
            }

            public StyleRun build() {
                return new StyleRun(this);
            }
        }
    }

    /**
     * A paragraph run describes the style of a paragraph,
     * identified by range of characters in the text data.
     */
    public static final class ParagraphRun extends Run {
        private final Alignment alignment;

        ParagraphRun(Builder builder) {
            super(builder.start, builder.end);
            alignment = builder.alignment;
        }

        /**
         * Returns the alignment of this paragraph.
         */
        public Alignment getAlignment() {
            return alignment;
        }

        public static final class Builder {
            final int start;
            final int end;

            private Alignment alignment = Alignment.LEFT;

            public Builder(int start, int end) {
                this.start = start;
                this.end = end;
            }

            public Builder alignment(Alignment alignment) {
                this.alignment = alignment;
                return this;
            }

            public ParagraphRun build() {
                return new ParagraphRun(this);
            }
        }
    }

    TextInfo(Builder builder) {
        text = builder.text;

        transform = builder.transform;
        bounds = builder.bounds;

        styleRuns = Lists.immutableCopy(builder.styleRuns);
        paragraphRuns = Lists.immutableCopy(builder.paragraphRuns);
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
        return new AffineTransform(transform);
    }

    /**
     * Returns the actual text for this text element as a String.
     */
    public String getText() {
        return text;
    }

    /**
     * Returns the list of style runs for this text element. Each run
     * describes the style of a range of the text String.
     */
    public List<StyleRun> getStyleRuns() {
        return styleRuns;
    }

    /**
     * Returns the list of paragraph runs for this text element. Each run
     * describes the style of a paragraph of the text String, identified
     * by a character range.
     */
    public List<ParagraphRun> getParagraphRuns() {
        return paragraphRuns;
    }

    /**
     * Returns the bounds of this text elements, in pixels. The bounds are
     * relative to the translation transform returned by {@link #getTransform()}.
     */
    public Rectangle2D getBounds() {
        return new Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    public String toString() {
        return text;
    }

    public static final class Builder {
        String text = "";

        final AffineTransform transform = new AffineTransform();
        final Rectangle2D.Double bounds = new Rectangle2D.Double();

        final List<StyleRun> styleRuns = new ArrayList<>();
        final List<ParagraphRun> paragraphRuns = new ArrayList<>();

        public Builder transform(AffineTransform transform) {
            this.transform.setTransform(transform);
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder bounds(double left, double top, double right, double bottom) {
            bounds.setRect(left, top, right - left, bottom - top);
            return this;
        }

        public Builder addStyleRun(StyleRun run) {
            styleRuns.add(run);
            return this;
        }

        public Builder addParagraphRun(ParagraphRun run) {
            paragraphRuns.add(run);
            return this;
        }

        public TextInfo build() {
            return new TextInfo(this);
        }
    }
}
