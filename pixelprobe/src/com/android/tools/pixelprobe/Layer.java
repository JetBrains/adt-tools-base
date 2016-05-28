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
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A layer in an Image. The values returned by a layer depends on
 * its type. Make sure to query getType() before you call type
 * specific getters (getTextInfo() for instance).
 */
public final class Layer {
    private final String mName;
    private final Type mType;

    private final Rectangle2D.Float mBounds = new Rectangle2D.Float();

    private float mOpacity = 1.0f;
    private BlendMode mBlendMode = BlendMode.NORMAL;

    private List<Layer> mChildren = Collections.emptyList();

    private BufferedImage mBitmap;

    private Path2D mPath;
    private Color mPathColor = Color.WHITE;

    private TextInfo mTextInfo;

    private boolean mOpened = true;
    private boolean mVisible = true;

    private final Effects mEffects = new Effects();

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
        BITMAP,
        /**
         * Contains children layers but does not hold data.
         */
        GROUP,
        /**
         * Vector mask or path.
         */
        PATH,
        /**
         * Text layer.
         */
        TEXT,
    }

    /**
     * Creates a new layer with the specified name and type.
     */
    Layer(String name, Type type) {
        mName = name;
        mType = type;
    }

    /**
     * Returns this layer's name. Never null.
     */
    public String getName() {
        return mName;
    }

    /**
     * Returns this layer's type.
     */
    public Type getType() {
        return mType;
    }

    /**
     * Returns the list of children for GROUP layers.
     */
    public List<Layer> getChildren() {
        return Collections.unmodifiableList(mChildren);
    }

    /**
     * Returns the bounds of this layer, in pixels, in
     * absolute image coordinates.
     */
    public Rectangle2D getBounds() {
        return mBounds;
    }

    /**
     * Returns this layer's opacity between 0 and 1.
     */
    public float getOpacity() {
        return mOpacity;
    }

    /**
     * Returns this layer's blending mode.
     */
    public BlendMode getBlendMode() {
        return mBlendMode;
    }

    /**
     * Returns this layer's list of effects.
     */
    public Effects getEffects() {
        return mEffects;
    }

    /**
     * Returns this layer's bitmap representation for {@link Type#BITMAP} layers.
     * Can be null if the bounds are empty.
     */
    public BufferedImage getBitmap() {
        return mBitmap;
    }

    /**
     * Returns this layer's vector data for {@link Type#PATH} layers.
     */
    public Path2D getPath() {
        return mPath;
    }

    /**
     * Returns this layer's vector color for {@link Type#PATH} layers.
     */
    public Color getPathColor() {
        return mPathColor;
    }

    /**
     * Returns this layer's text information for {@link Type#TEXT} layers.
     */
    public TextInfo getTextInfo() {
        return mTextInfo;
    }

    /**
     * Indicates whether this layer is opened or closed, only for {@link Type#GROUP} layers.
     */
    public boolean isOpened() {
        return mOpened;
    }

    /**
     * Indicates whether this layer is visible.
     */
    public boolean isVisible() {
        return mVisible;
    }

    void addLayer(Layer layer) {
        if (mChildren.size() == 0) mChildren = new ArrayList<>();
        mChildren.add(layer);
    }

    void setBounds(long left, long top, long right, long bottom) {
        mBounds.setRect(left, top, right - left, bottom - top);
    }

    void setOpacity(float opacity) {
        mOpacity = opacity;
    }

    void setBlendMode(BlendMode blendMode) {
        mBlendMode = blendMode;
    }

    void setBitmap(BufferedImage bitmap) {
        mBitmap = bitmap;
    }

    void setPath(Path2D path) {
        mPath = path;
    }

    void setPathColor(Color color) {
        mPathColor = color;
    }

    void setTextInfo(TextInfo textInfo) {
        mTextInfo = textInfo;
    }

    void setOpened(boolean opened) {
        mOpened = opened;
    }

    void setVisible(boolean visible) {
        mVisible = visible;
    }

    @Override
    public String toString() {
        return mName;
    }
}
