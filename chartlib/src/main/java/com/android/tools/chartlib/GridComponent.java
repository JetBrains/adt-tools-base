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
package com.android.tools.chartlib;

import com.android.annotations.NonNull;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;

/**
 * A component that draws grid lines within the given dimension.
 * The grid lines correspond to the tick markers of the {@link AxisComponent} that were added to this grid.
 */
public final class GridComponent extends AnimatedComponent {

    private static final Color GRID_COLOR = new Color(224, 224, 224);

    @NonNull
    private java.util.List<AxisComponent> mAxes;

    public GridComponent() {
        mAxes = new ArrayList();
    }

    public void addAxis(AxisComponent axis) {
        mAxes.add(axis);
    }

    @Override
    protected void updateData() {
    }

    @Override
    protected void draw(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(GRID_COLOR);

        Dimension dim = getSize();
        Path2D.Float path = new Path2D.Float();
        for (int i = 0; i < mAxes.size(); i++) {
            AxisComponent axis = mAxes.get(i);
            int numMarkers = axis.getNumMarkers();
            int interval = axis.getInterval();
            double firstOffset = axis.getFirstMarkerValue();

            switch (axis.getOrientation()) {
                case LEFT:
                case RIGHT:
                    for (int j = 0; j < numMarkers; j++) {
                        float offset = axis.getPositionAtValue(firstOffset + j * interval);
                        path.moveTo(0, offset);
                        path.lineTo(dim.width, offset);
                    }

                    break;
                case TOP:
                case BOTTOM:
                    for (int j = 0; j < numMarkers; j++) {
                        float offset = axis.getPositionAtValue(firstOffset + j * interval);
                        path.moveTo(offset, 0);
                        path.lineTo(offset, dim.height);
                    }
                    break;
            }
        }
        g.draw(path);
    }
}
