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

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A layer can hold different data depending on its type.
 * Make sure to query {@link #getType()} before you call
 * type-specific getters {@link #getTextInfo()} for instance).
 */
public final class Layer {
    private final String name;
    private final Type type;

    private final Rectangle2D.Float bounds;

    private final float opacity;
    private final BlendMode blendMode;
    private final boolean clipBase;

    private final List<Layer> children;

    private final BufferedImage image;

    private final ShapeInfo shapeInfo;
    private final TextInfo textInfo;

    private final Effects effects;

    private final boolean open;
    private final boolean visible;

    /**
     * Available layer types.
     */
    public enum Type {
        /**
         * Adjustment layer.
         */
        ADJUSTMENT,
        /**
         * Bitmap or raster layer. Only contains raw pixels.
         */
        IMAGE,
        /**
         * Contains children layers but does not hold data.
         */
        GROUP,
        /**
         * Vector/shape/path layer.
         */
        SHAPE,
        /**
         * Text layer.
         */
        TEXT,
    }

    Layer(Builder builder) {
        name = builder.name;
        type = builder.type;

        bounds = builder.bounds;

        opacity = builder.opacity;
        blendMode = builder.blendMode;
        clipBase = builder.clipBase;

        children = Lists.immutableCopy(builder.children);

        image = builder.image;

        shapeInfo = builder.shapeInfo;
        textInfo = builder.textInfo;

        effects = builder.effects;

        open = builder.open;
        visible = builder.visible;
    }

    /**
     * Returns this layer's name. Never null.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns this layer's type.
     */
    public Type getType() {
        return type;
    }

    /**
     * Returns the list of children for {@link Type#GROUP} layers.
     */
    public List<Layer> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Returns the bounds of this layer, in pixels, in
     * absolute image coordinates.
     */
    public Rectangle2D getBounds() {
        return new Rectangle2D.Float(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /**
     * Returns this layer's opacity between 0 and 1.
     */
    public float getOpacity() {
        return opacity;
    }

    /**
     * Returns this layer's blending mode.
     */
    public BlendMode getBlendMode() {
        return blendMode;
    }

    /**
     * Returns whether this layer is a clipping base or not. Layers
     * that are not clipping bases are clipped by the first clipping
     * base layer under them, as long as it is within the same group.
     */
    public boolean isClipBase() {
        return clipBase;
    }

    /**
     * Returns this layer's list of effects.
     */
    public Effects getEffects() {
        return effects;
    }

    /**
     * Returns this layer's image representation for {@link Type#IMAGE} layers.
     * Can be null if the bounds are empty. Make sure to check the color model
     * and/or color space of this image before using it in high performance code
     * paths. The color model/color space might not be suitable for direct
     * rendering.
     */
    public BufferedImage getImage() {
        return image;
    }

    /**
     * Returns this layer's shape information for {@link Type#SHAPE} layers.
     */
    public ShapeInfo getShapeInfo() {
        return shapeInfo;
    }

    /**
     * Returns this layer's text information for {@link Type#TEXT} layers.
     */
    public TextInfo getTextInfo() {
        return textInfo;
    }

    /**
     * Indicates whether this layer is opened or closed, only for {@link Type#GROUP} layers.
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * Indicates whether this layer is visible.
     */
    public boolean isVisible() {
        return visible;
    }

    @Override
    public String toString() {
        return "Layer{" +
               "name='" + name + '\'' +
               ", type=" + type +
               ", bounds=" + bounds +
               ", opacity=" + opacity +
               ", blendMode=" + blendMode +
               ", children=" + children.size() +
               ", image=" + (image != null) +
               ", text=" + (textInfo != null) +
               ", shape=" + (shapeInfo != null) +
               ", effects=" + (effects != null) +
               ", open=" + open +
               ", visible=" + visible +
               ", clipBase=" + clipBase +
               '}';
    }

    @SuppressWarnings("UseJBColor")
    public static final class Builder {
        final String name;
        final Type type;

        final Rectangle2D.Float bounds = new Rectangle2D.Float();

        float opacity = 1.0f;
        BlendMode blendMode = BlendMode.NORMAL;
        boolean clipBase = true;

        final List<Layer> children = new ArrayList<>();

        BufferedImage image;
        ShapeInfo shapeInfo;
        TextInfo textInfo;

        Effects effects;

        private boolean open = true;
        private boolean visible = true;

        public Builder(String name, Type type) {
            this.name = name;
            this.type = type;
        }

        public Rectangle2D bounds() {
            return new Rectangle2D.Float(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        public Builder bounds(int left, int top, int width, int height) {
            this.bounds.setRect(left, top, width, height);
            return this;
        }

        public Builder opacity(float opacity) {
            this.opacity = opacity;
            return this;
        }

        public Builder blendMode(BlendMode blendMode) {
            this.blendMode = blendMode;
            return this;
        }

        public Builder clipBase(boolean clipBase) {
            this.clipBase = clipBase;
            return this;
        }

        public Builder addLayer(Layer layer) {
            children.add(layer);
            return this;
        }

        public Builder image(BufferedImage image) {
            this.image = image;
            return this;
        }

        public Builder shapeInfo(ShapeInfo info) {
            shapeInfo = info;
            return this;
        }

        public Builder textInfo(TextInfo info) {
            textInfo = info;
            return this;
        }

        public Builder effects(Effects effects) {
            this.effects = effects;
            return this;
        }

        public Builder open(boolean open) {
            this.open = open;
            return this;
        }

        public Builder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public Layer build() {
            return new Layer(this);
        }
    }
}
