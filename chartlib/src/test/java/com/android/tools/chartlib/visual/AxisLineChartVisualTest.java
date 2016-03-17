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
import com.android.tools.chartlib.*;
import com.android.tools.chartlib.model.LineChartData;
import com.android.tools.chartlib.model.Range;
import com.android.tools.chartlib.model.RangedSeries;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AxisLineChartVisualTest extends VisualTest {

    private static final float ZOOM_FACTOR = 0.1f;

    private static final int AXIS_SIZE = 100;

    private final long mStartTimeMs;

    @NonNull
    private final Range mXRange;

    @NonNull
    private final Range mXGlobalRange;

    @NonNull
    private final LineChart mLineChart;

    @NonNull
    private final LineChartData mData;

    @NonNull
    private AxisComponent mMemoryAxis1;

    @NonNull
    private AxisComponent mMemoryAxis2;

    @NonNull
    private AxisComponent mTimeAxis;

    @NonNull
    private GridComponent mGrid;

    @NonNull
    private final RangeScrollbar mScrollbar;

    public AxisLineChartVisualTest(Choreographer choreographer) {
        mData = new LineChartData();
        mLineChart = new LineChart(mData);

        mStartTimeMs = System.currentTimeMillis();
        mXRange = new Range(0, 0);
        mXGlobalRange = new Range(0, 0);
        mScrollbar = new RangeScrollbar(mXGlobalRange, mXRange);

        // add horizontal time axis
        mTimeAxis = new AxisComponent(mXRange, "TIME", AxisComponent.AxisOrientation.BOTTOM,
                                      1000, 1, AXIS_SIZE, AXIS_SIZE, new AxisComponent.MarkerFormatter() {
            @Override
            public String getFormattedString(double value) {
                return String.format("%.2f%s", value / 1000f, "s");
            }
        });

        // left memory data + axis
        Range yRange1 = new Range(0f, 100f);
        mMemoryAxis1 = new AxisComponent(yRange1, "MEMORY1", AxisComponent.AxisOrientation.LEFT,
                                         2, 1, AXIS_SIZE, AXIS_SIZE, new AxisComponent.MarkerFormatter() {
            @Override
            public String getFormattedString(double value) {
                return String.format("%.2f%s", value, "KB");
            }
        });
        RangedSeries ranged1 = new RangedSeries(mXRange, yRange1);
        mData.add(ranged1);

        // right memory data + axis
        Range yRange2 = new Range(0f, 100f);
        mMemoryAxis2 = new AxisComponent(yRange2, "MEMORY2", AxisComponent.AxisOrientation.RIGHT,
                                         2, 1, AXIS_SIZE, AXIS_SIZE, new AxisComponent.MarkerFormatter() {
            @Override
            public String getFormattedString(double value) {
                return String.format("%.2f%s", value, "KB");
            }
        });
        RangedSeries ranged2 = new RangedSeries(mXRange, yRange2);
        mData.add(ranged2);

        mGrid = new GridComponent();
        mGrid.addAxis(mTimeAxis);
        mGrid.addAxis(mMemoryAxis1);
        mGrid.addAxis(mMemoryAxis2);

        choreographer.register(mScrollbar);
        choreographer.register(mLineChart);
        choreographer.register(mTimeAxis);
        choreographer.register(mMemoryAxis1);
        choreographer.register(mMemoryAxis2);
        choreographer.register(mGrid);
    }

    @Override
    void registerComponents(List<AnimatedComponent> components) {
        components.add(mLineChart);
        components.add(mTimeAxis);
        components.add(mMemoryAxis1);
        components.add(mMemoryAxis2);
        components.add(mGrid);
    }

    @Override
    public String getName() {
        return "AxisLineChart";
    }

    @Override
    public JPanel create() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JLayeredPane mockTimelinePane = createMockTimeline();
        panel.add(mockTimelinePane, BorderLayout.CENTER);

        final JPanel controls = new JPanel();
        LayoutManager manager = new BoxLayout(controls, BoxLayout.Y_AXIS);
        controls.setLayout(manager);
        panel.add(controls, BorderLayout.WEST);

        final AtomicInteger variance = new AtomicInteger(10);
        final AtomicInteger delay = new AtomicInteger(10);
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    while (true) {
                        // Moves time forward by updating global range to now.
                        long now = System.currentTimeMillis() - mStartTimeMs;
                        mXGlobalRange.setMax(now);

                        //  Insert new data point at now.
                        int v = variance.get();
                        for (RangedSeries rangedSeries : mData.series()) {
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
        controls.add(VisualTests.createCheckbox("Stable Scroll", new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                mScrollbar.setStableScrolling(itemEvent.getStateChange() == ItemEvent.SELECTED);
            }
        }));
        controls.add(VisualTests.createButton("Zoom In Test", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mScrollbar.zoom(-ZOOM_FACTOR);
            }
        }));
        controls.add(VisualTests.createButton("Zoom Out Test", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mScrollbar.zoom(ZOOM_FACTOR);
            }
        }));

        controls.add(
          new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                         new Dimension(300, Integer.MAX_VALUE)));

        return panel;
    }

    private JLayeredPane createMockTimeline() {
        JLayeredPane timelinePane = new JLayeredPane();

        timelinePane.add(mMemoryAxis1);
        timelinePane.add(mMemoryAxis2);
        timelinePane.add(mTimeAxis);
        timelinePane.add(mLineChart);
        timelinePane.add(mGrid);
        timelinePane.add(mScrollbar);
        timelinePane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                JLayeredPane host = (JLayeredPane)e.getComponent();
                if (host != null) {
                    Dimension dim = host.getSize();
                    for (Component c : host.getComponents()) {
                        if (c instanceof AxisComponent) {
                            AxisComponent axis = (AxisComponent)c;
                            switch (axis.getOrientation()) {
                                case LEFT:
                                    axis.setBounds(0, 0, AXIS_SIZE, dim.height);
                                    break;
                                case BOTTOM:
                                    axis.setBounds(0, dim.height - AXIS_SIZE, dim.width, AXIS_SIZE);
                                    break;
                                case RIGHT:
                                    axis.setBounds(dim.width - AXIS_SIZE, 0, AXIS_SIZE, dim.height);
                                    break;
                                case TOP:
                                    axis.setBounds(0, 0, dim.width, AXIS_SIZE);
                                    break;
                            }
                        } else if (c instanceof RangeScrollbar) {
                            int sbHeight = c.getPreferredSize().height;
                            c.setBounds(0, dim.height - sbHeight, dim.width, sbHeight);
                        } else {
                            c.setBounds(AXIS_SIZE, AXIS_SIZE, dim.width - AXIS_SIZE * 2, dim.height - AXIS_SIZE * 2);
                        }
                    }
                }
            }
        });

        return timelinePane;
    }
}