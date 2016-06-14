/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.tools.pixelprobe.util.Strings;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The shape information for an image's shape layer.
 * A shape can be made of one or more sub-paths. Each sub-path
 * contains a geometry (a {@link Path2D}, as well as an operator
 * that describes how to combine that sub-path with the previous
 * sub-paths that comprise the shape.
 */
public final class ShapeInfo {
    private final Style style;

    private final List<SubPath> paths;

    private final Paint fillPaint;
    private final float fillOpacity;

    private final Stroke stroke;
    private final Paint strokePaint;
    private final float strokeOpacity;
    private final BlendMode strokeBlendMode;
    private final Alignment strokeAlignment;

    /**
     * The shape style defines how the shape's path should be rendered.
     */
    public enum Style {
        /**
         * Fill the shape according to its winding rule.
         */
        FILL,
        /**
         * Apply a stroke on the shape but don't fill it.
         */
        STROKE,
        /**
         * Fill the shape and apply a stroke.
         */
        FILL_AND_STROKE,
        /**
         * Do not render the shape.
         */
        NONE;

        /**
         * Returns a style for the specified fill and stroke configuration.
         */
        public static Style from(boolean fillEnabled, boolean strokeEnabled) {
            if (fillEnabled) {
                return strokeEnabled ? FILL_AND_STROKE : FILL;
            } else if (strokeEnabled) {
                return STROKE;
            }
            return NONE;
        }
    }

    /**
     * Defines how the stroke should be aligned over the shape's path.
     */
    public enum Alignment {
        /**
         * The stroke is drawn inside the shape defined by the path.
         */
        INSIDE,
        /**
         * The stroke is drawn centered around the path of the shape.
         */
        CENTER,
        /**
         * The stroke is drawn outside the shape defined by the path.
         */
        OUTSIDE
    }

    /**
     * A path operation defines how a {@link Path2D} must be combined with
     * the previous {@link Path2D} instances of a shape to create the final
     * path representation of the shape.
     */
    public enum PathOp {
        /**
         * The path is added to (merged with) the previous paths.
         */
        ADD,
        /**
         * The path is subtracted from the previous paths.
         */
        SUBTRACT,
        /**
         * The final path is the intersection of the path and the previous paths.
         */
        INTERSECT,
        /**
         * The final path is the exclusive or of the path and the previous paths.
         */
        EXCLUSIVE_OR
    }

    /**
     * Indicates whether a sub-path is open or closed.
     */
    public enum PathType {
        /**
         * Open path, the last point is not connected to the first point.
         */
        OPEN,
        /**
         * Closed path, the last point is connected to the first point.
         */
        CLOSED,
        /**
         * The path contains multiple move commands and must be inspected
         * to determine whether each sub-sequence is open or closed.
         */
        NONE
    }

    /**
     * A sub-path contains a path and an operator that defines how to
     * combine that path with other paths contained in a shape.
     */
    public static final class SubPath {
        private final Path2D path;
        private final PathOp op;
        private final PathType type;

        SubPath(Builder builder) {
            path = builder.path;
            op = builder.op;
            type = builder.type;
        }

        /**
         * Returns the path contained in this sub-path. This path must be
         * combined with the shape's other sub-paths according to its operator.
         *
         * @see #getOp()
         */
        public Path2D getPath() {
            return (Path2D) path.clone();
        }

        /**
         * Describes how the path contained in this sub-path must be combined
         * with the shape's other sub-paths.
         *
         * @see #getPath()
         */
        public PathOp getOp() {
            return op;
        }

        /**
         * Returns whether the path contained in this sub-path is open or closed.
         */
        public PathType getType() {
            return type;
        }

        @Override
        public String toString() {
            return "SubPath{" +
                   "path=" + path +
                   ", type=" + type +
                   ", op=" + op +
                   '}';
        }

        public static final class Builder {
            private Path2D path = new GeneralPath();
            private PathOp op = PathOp.ADD;
            private PathType type = PathType.CLOSED;

            public Builder type(PathType type) {
                this.type = type;
                return this;
            }

            public Builder op(PathOp op) {
                this.op = op;
                return this;
            }

            public Builder path(Path2D path) {
                this.path = path;
                return this;
            }

            public SubPath build() {
                return new SubPath(this);
            }
        }
    }

    ShapeInfo(Builder builder) {
        style = builder.style;
        paths = Lists.immutableCopy(builder.paths);

        fillPaint = builder.fillPaint;
        fillOpacity = builder.fillOpacity;

        stroke = builder.stroke;
        strokePaint = builder.strokePaint;
        strokeOpacity = builder.strokeOpacity;
        strokeBlendMode = builder.strokeBlendMode;
        strokeAlignment = builder.strokeAlignment;
    }

    /**
     * Returns the style describing how this shape's path should be rendered.
     */
    public Style getStyle() {
        return style;
    }

    /**
     * Returns the list of sub-paths representing this shape.
     */
    public List<SubPath> getPaths() {
        return paths;
    }

    /**
     * Returns the paint that should be used to fill this shape.
     *
     * @see #getStyle()
     * @see #getFillOpacity()
     */
    public Paint getFillPaint() {
        return fillPaint;
    }

    /**
     * Returns the opacity of the fill paint for this shape as a value
     * between 0.0 and 1.0.
     *
     * @see #getStyle()
     * @see #getFillPaint()
     */
    public float getFillOpacity() {
        return fillOpacity;
    }

    /**
     * Returns the stroke properties for this shape.
     *
     * @see #getStyle()
     * @see #getStrokePaint()
     * @see #getStrokeOpacity()
     */
    public Stroke getStroke() {
        return stroke;
    }

    /**
     * Returns the paint that should be used to stroke this shape.
     *
     * @see #getStyle()
     * @see #getStroke()
     * @see #getStrokeOpacity()
     */
    public Paint getStrokePaint() {
        return strokePaint;
    }

    /**
     * Returns the opacity of the fill paint for this shape as a value
     * between 0.0 and 1.0.
     *
     * @see #getStyle()
     * @see #getStroke()
     * @see #getStrokePaint()
     */
    public float getStrokeOpacity() {
        return strokeOpacity;
    }

    /**
     * Returns the stroke's blending mode.
     */
    public BlendMode getStrokeBlendMode() {
        return strokeBlendMode;
    }

    /**
     * Returns the alignment of the stroke over this shape's path.
     */
    public Alignment getStrokeAlignment() {
        return strokeAlignment;
    }

    @Override
    public String toString() {
        return "ShapeInfo{" +
               "style=" + style +
               ", subPaths={" + Strings.join(paths, ",") + "}" +
               ", fillPaint=" + fillPaint +
               ", fillOpacity=" + fillOpacity +
               ", stroke=" + (stroke != null) +
               ", strokePaint=" + strokePaint +
               ", strokeOpacity=" + strokeOpacity +
               ", strokeBlendMode=" + strokeBlendMode +
               ", strokeAlignment=" + strokeAlignment +
               '}';
    }

    @SuppressWarnings("UseJBColor")
    public static final class Builder {
        Style style = Style.FILL;
        final List<SubPath> paths = new ArrayList<>();

        Paint fillPaint = Color.BLACK;
        float fillOpacity = 1.0f;

        Stroke stroke = new BasicStroke(0.0f);
        Paint strokePaint = Color.BLACK;
        float strokeOpacity = 1.0f;
        BlendMode strokeBlendMode = BlendMode.NORMAL;
        Alignment strokeAlignment = Alignment.CENTER;

        public Builder style(Style style) {
            this.style = style;
            return this;
        }

        public Builder addSubPath(SubPath subPath) {
            paths.add(subPath);
            return this;
        }

        public Builder fillPaint(Paint paint) {
            fillPaint = paint;
            return this;
        }

        public Builder fillOpacity(float opacity) {
            fillOpacity = opacity;
            return this;
        }

        public Builder stroke(Stroke stroke) {
            this.stroke = stroke;
            return this;
        }

        public Builder strokePaint(Paint paint) {
            strokePaint = paint;
            return this;
        }

        public Builder strokeOpacity(float opacity) {
            strokeOpacity = opacity;
            return this;
        }

        public Builder strokeBlendMode(BlendMode blendMode) {
            strokeBlendMode = blendMode;
            return this;
        }

        public Builder strokeAlignment(Alignment alignment) {
            strokeAlignment = alignment;
            return this;
        }

        public ShapeInfo build() {
            return new ShapeInfo(this);
        }
    }
}
