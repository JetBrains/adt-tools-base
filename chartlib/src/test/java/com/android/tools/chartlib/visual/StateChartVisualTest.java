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
import com.android.tools.chartlib.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StateChartVisualTest extends VisualTest {

    private static final Color[] MOCK_COLORS_1 = {
        new Color(0,0,0,0),
        new Color(0x993333),
        new Color(0xcc33cc),
        new Color(0xcc6600),
        new Color(0xcccc33),
    };

    private static final Color[] MOCK_COLORS_2 = {
        new Color(0,0,0,0),
        new Color(0xc6dbef),
        new Color(0x6baed6)
    };

    public enum MockFruitState {
        NONE,
        APPLE,
        GRAPE,
        ORANGE,
        BANANA
    }

    public enum MockStrengthState {
        NONE,
        WEAK,
        STRONG
    }

    private static final int AXIS_SIZE = 100;

    private final long mStartTimeMs;

    @NonNull
    private final Range mXRange;

    @NonNull
    private final AnimatedTimeRange mAnimatedRange;

    @NonNull
    private final StateChart mNetworkStatusChart;

    @NonNull
    private final StateChart mRadioStateChart;

    @NonNull
    private final StateChartData mNetworkData;

    @NonNull
    private final StateChartData mRadioData;

    public StateChartVisualTest(Choreographer choreographer) {
        mNetworkData = new StateChartData();
        mRadioData = new StateChartData();
        mStartTimeMs = System.currentTimeMillis();
        mXRange = new Range(0, 10000);
        mAnimatedRange = new AnimatedTimeRange(mXRange, mStartTimeMs);

        mNetworkData.add(new RangedDiscreteSeries(MockFruitState.class, mXRange));
        mRadioData.add(new RangedDiscreteSeries(MockStrengthState.class, mXRange));

        mNetworkStatusChart = new StateChart(mNetworkData, MOCK_COLORS_1);
        mRadioStateChart = new StateChart(mRadioData, MOCK_COLORS_2);

        choreographer.register(mAnimatedRange);
        choreographer.register(mNetworkStatusChart);
        choreographer.register(mRadioStateChart);
    }

    @Override
    void registerComponents(List<AnimatedComponent> components) {
        components.add(mNetworkStatusChart);
        components.add(mRadioStateChart);
    }

    @Override
    public String getName() {
        return "StateChart";
    }

    @Override
    public JPanel create() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JLayeredPane timelinePane = createMockTimeline();
        panel.add(timelinePane, BorderLayout.CENTER);

        final JPanel controls = new JPanel();
        LayoutManager manager = new BoxLayout(controls, BoxLayout.Y_AXIS);
        controls.setLayout(manager);
        panel.add(controls, BorderLayout.WEST);

        final AtomicInteger networkVariance = new AtomicInteger(MockFruitState.values().length);
        final AtomicInteger radioVariance = new AtomicInteger(MockStrengthState.values().length);
        final AtomicInteger delay = new AtomicInteger(100);
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    while (true) {
                        long now = System.currentTimeMillis() - mStartTimeMs;

                        int v = networkVariance.get();
                        for (RangedDiscreteSeries series : mNetworkData.series()) {
                            if (Math.random() > 0.5f) {
                                series.getSeries().add(now, MockFruitState.values()[(int)(Math.random() * v)]);
                            }
                        }

                        v = radioVariance.get();
                        for (RangedDiscreteSeries series : mRadioData.series()) {
                            if (Math.random() > 0.5f) {
                                series.getSeries().add(now, MockFruitState.values()[(int)(Math.random() * v)]);
                            }
                        }

                        Thread.sleep(delay.get());
                    }
                } catch (InterruptedException e) {
                }
            }
        }.start();

        controls.add(VisualTests.createVaribleSlider("ArcWidth", 0, 100, new VisualTests.Value() {
            @Override
            public void set(int v) {
                mNetworkStatusChart.setArcWidth(v / 100f);
                mRadioStateChart.setArcWidth(v / 100f);
            }

            @Override
            public int get() {
                return -1; // unused
            }
        }));
        controls.add(VisualTests.createVaribleSlider("ArcHeight", 0, 100, new VisualTests.Value() {
            @Override
            public void set(int v) {
                mNetworkStatusChart.setArcHeight(v / 100f);
                mRadioStateChart.setArcHeight(v / 100f);
            }

            @Override
            public int get() {
                return -1; // unused
            }
        }));
        controls.add(VisualTests.createVaribleSlider("Gap", 0, 100, new VisualTests.Value() {
            @Override
            public void set(int v) {
                mNetworkStatusChart.setHeightGap(v / 100f);
                mRadioStateChart.setHeightGap(v / 100f);
            }

            @Override
            public int get() {
                return -1; // unused
            }
        }));
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
        controls.add(VisualTests.createVaribleSlider("Fruit Variance", 1, MockFruitState.values().length, new VisualTests.Value() {
            @Override
            public void set(int v) {
                networkVariance.set(v);
            }

            @Override
            public int get() {
                return networkVariance.get();
            }
        }));
        controls.add(VisualTests.createVaribleSlider("Strength Variance", 1, MockStrengthState.values().length, new VisualTests.Value() {
            @Override
            public void set(int v) {
                radioVariance.set(v);
            }

            @Override
            public int get() {
                return radioVariance.get();
            }
        }));
        controls.add(VisualTests.createButton("Add Fruit Series", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mNetworkData.add(new RangedDiscreteSeries(MockFruitState.class, mXRange));
            }
        }));
        controls.add(VisualTests.createButton("Add Strength Series", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mRadioData.add(new RangedDiscreteSeries(MockStrengthState.class, mXRange));
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

    private JLayeredPane createMockTimeline() {
        JLayeredPane timelinePane = new JLayeredPane();

        timelinePane.add(mNetworkStatusChart);
        timelinePane.add(mRadioStateChart);
        timelinePane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                JLayeredPane host = (JLayeredPane)e.getComponent();
                if (host != null) {
                    Dimension dim = host.getSize();
                    int numChart = 0;
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
                        } else if (c instanceof StateChart){
                            int y = numChart % 2 == 0 ? AXIS_SIZE : dim.height - AXIS_SIZE * 2;
                            c.setBounds(AXIS_SIZE, y, dim.width - AXIS_SIZE * 2, AXIS_SIZE);
                            numChart++;
                        }
                    }
                }
            }
        });

        return timelinePane;
    }
}
