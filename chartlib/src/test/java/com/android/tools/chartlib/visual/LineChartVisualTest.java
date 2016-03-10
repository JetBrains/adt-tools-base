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

package com.android.tools.chartlib.visual;

import com.android.annotations.NonNull;
import com.android.tools.chartlib.AnimatedComponent;
import com.android.tools.chartlib.AnimatedTimeRange;
import com.android.tools.chartlib.Choreographer;
import com.android.tools.chartlib.LineChart;
import com.android.tools.chartlib.model.LineChartData;
import com.android.tools.chartlib.model.Range;
import com.android.tools.chartlib.model.RangedContinuousSeries;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LineChartVisualTest extends VisualTest {

    @NonNull
    private final LineChart mLineChart;

    @NonNull
    private final LineChartData mData;

    private final AnimatedTimeRange mAnimatedRange;

    public LineChartVisualTest(Choreographer choreographer) {
        mData = new LineChartData();

        long now = System.currentTimeMillis();
        Range xRange = new Range(now, now + 60000);
        Range yRange = null;
        for (int i = 0; i < 4; i++) {
            yRange = i % 2 == 0 ? new Range(0.0, 100.0) : yRange;
            RangedContinuousSeries ranged = new RangedContinuousSeries(xRange, yRange);
            mData.add(ranged);
        }
        mLineChart = new LineChart(mData);
        mAnimatedRange = new AnimatedTimeRange(xRange, 0);

        // Set up the scene
        choreographer.register(mAnimatedRange);
        choreographer.register(mLineChart);
    }

    @Override
    void registerComponents(List<AnimatedComponent> components) {
        components.add(mLineChart);
    }

    @Override
    public String getName() {
        return "LineChart";
    }

    @Override
    public JPanel create() {
        JPanel panel = new JPanel();
        JPanel controls = VisualTests.createControlledPane(panel, mLineChart);
        mLineChart.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        final AtomicInteger variance = new AtomicInteger(10);
        final AtomicInteger delay = new AtomicInteger(100);
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    while (true) {
                        int v = variance.get();
                        long now = System.currentTimeMillis();
                        for (RangedContinuousSeries rangedSeries : mData.series()) {
                            int size = rangedSeries.getSeries().size();
                            long last = size > 0 ? rangedSeries.getSeries().getY(size - 1) : 0;
                            float delta = (float) Math.random() * variance.get() - v * 0.45f;
                            rangedSeries.getSeries().add(now, last + (long) delta);
                        }
                        Thread.sleep(delay.get());
                    }
                } catch (InterruptedException e) {
                }
            }
        }.start();

        controls.add(VisualTests.createVaribleSlider("Delay", 10, 5000, new VisualTests.Value() {
            @Override
            public void set(int v) {
                delay.set(v);
            }

            @Override
            public int get() {
                return delay.get();
            }
        }));
        controls.add(VisualTests.createVaribleSlider("Variance", 0, 50, new VisualTests.Value() {
            @Override
            public void set(int v) {
                variance.set(v);
            }

            @Override
            public int get() {
                return variance.get();
            }
        }));
        controls.add(VisualTests.createCheckbox("Shift xRange Min", new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                mAnimatedRange.setShift(itemEvent.getStateChange() == ItemEvent.SELECTED);
            }
        }));

        controls.add(
                new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                        new Dimension(300, Integer.MAX_VALUE)));
        return panel;
    }
}
