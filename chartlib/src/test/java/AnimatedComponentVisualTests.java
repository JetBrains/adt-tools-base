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

import com.android.tools.chartlib.AnimatedComponent;
import com.android.tools.chartlib.EventData;
import com.android.tools.chartlib.TimelineComponent;
import com.android.tools.chartlib.TimelineData;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class AnimatedComponentVisualTests extends JDialog {

    private List<AnimatedComponent> mComponents = new LinkedList<AnimatedComponent>();

    public AnimatedComponentVisualTests() {
        JPanel contentPane = new JPanel(new BorderLayout());
        JButton close = new JButton("Close");
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Timeline", getTimelineExample());

        contentPane.setPreferredSize(new Dimension(1280, 1024));
        contentPane.add(tabs, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(close, BorderLayout.EAST);
        contentPane.add(bottom, BorderLayout.SOUTH);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.add(Box.createRigidArea(new Dimension(100, 20)));
        final JCheckBox debug = new JCheckBox("Debug");
        debug.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                for (AnimatedComponent component : mComponents) {
                    component.setDrawDebugInfo(debug.isSelected());
                }
            }
        });
        controls.add(debug);

        final JCheckBox update = new JCheckBox("Update");
        update.setSelected(true);
        update.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                for (AnimatedComponent component : mComponents) {
                    component.setUpdateData(update.isSelected());
                }
            }
        });
        controls.add(update);

        final JButton step = new JButton("Step");
        step.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                for (AnimatedComponent component : mComponents) {
                    component.step();
                }
            }
        });
        controls.add(step);

        contentPane.add(controls, BorderLayout.WEST);

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(close);

        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    static interface Value {

        void set(int v);

        int get();
    }

    private static JPanel createVaribleSlider(String name, final int a, final int b,
            final Value value) {
        JPanel panel = new JPanel(new BorderLayout());
        final JLabel text = new JLabel();
        final JSlider slider = new JSlider(a, b);
        ChangeListener listener = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent changeEvent) {
                value.set(slider.getValue());
                text.setText(String.format("%d [%d,%d]", slider.getValue(), a, b));
            }
        };
        slider.setValue(value.get());
        listener.stateChanged(null);
        slider.addChangeListener(listener);
        panel.add(slider, BorderLayout.CENTER);
        panel.add(new JLabel(name + ": "), BorderLayout.WEST);
        panel.add(text, BorderLayout.EAST);
        return panel;
    }

    private JPanel createControlledPane(JPanel panel, AnimatedComponent animated) {
        panel.setLayout(new BorderLayout());
        mComponents.add(animated);
        panel.add(animated, BorderLayout.CENTER);

        JPanel controls = new JPanel();
        LayoutManager manager;
        try {
            manager = (LayoutManager) Class.forName("sun.awt.VerticalBagLayout").newInstance();
        } catch (Exception e) {
            manager = new BoxLayout(controls, BoxLayout.Y_AXIS);
        }
        controls.setLayout(manager);
        controls.setPreferredSize(new Dimension(300, 800));
        panel.add(controls, BorderLayout.WEST);
        return controls;
    }

    private static Component createButton(String label, ActionListener action) {
        JButton button = new JButton(label);
        button.setPreferredSize(button.getPreferredSize());
        button.addActionListener(action);
        return button;
    }

    private JPanel getTimelineExample() {
        final TimelineData data = new TimelineData(2, 2000);
        final EventData events = new EventData();
        final int streams = 2;
        final AtomicInteger variance = new AtomicInteger(10);
        final AtomicInteger delay = new AtomicInteger(100);
        final AtomicInteger type = new AtomicInteger(0);
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    float[] values = new float[streams];
                    while (true) {
                        int v = variance.get();
                        for (int i = 0; i < streams; i++) {
                            float delta = (float) Math.random() * variance.get() - v * 0.5f;
                            values[i] = Math.max(0, delta + values[i]);
                        }
                        synchronized (data) {
                            data.add(System.currentTimeMillis(), type.get() + (v == 0 ? 1 : 0), Arrays.copyOf(values,
                                    streams));
                        }
                        Thread.sleep(delay.get());
                    }
                } catch (InterruptedException e) {
                }
            }
        }.start();
        final TimelineComponent timeline = new TimelineComponent(data, events, 1.0f, 10.0f, 1000.0f,
                10.0f);
        timeline.configureStream(0, "Data 0", new Color(0x78abd9));
        timeline.configureStream(1, "Data 1", new Color(0xbaccdc));

        timeline.configureUnits("@");
        timeline.configureEvent(1, 0, UIManager.getIcon("Tree.leafIcon"),
                new Color(0x92ADC6),
                new Color(0x2B4E8C), false);
        timeline.configureEvent(2, 1, UIManager.getIcon("Tree.leafIcon"),
                new Color(255, 191, 176),
                new Color(76, 14, 29), true);
        timeline.configureType(1, TimelineComponent.Style.SOLID);
        timeline.configureType(2, TimelineComponent.Style.DASHED);

        final JPanel panel = new JPanel();
        final JPanel controls = createControlledPane(panel, timeline);
        controls.add(createVaribleSlider("Delay", 10, 5000, new Value() {
            @Override
            public void set(int v) {
                delay.set(v);
            }

            @Override
            public int get() {
                return delay.get();
            }
        }));
        controls.add(createVaribleSlider("Variance", 0, 50, new Value() {
            @Override
            public void set(int v) {
                variance.set(v);
            }

            @Override
            public int get() {
                return variance.get();
            }
        }));
        controls.add(createVaribleSlider("Type", 0, 2, new Value() {
            @Override
            public void set(int v) {
                type.set(v);
            }

            @Override
            public int get() {
                return type.get();
            }
        }));
        controls.add(createEventButton(1, events, variance));
        controls.add(createEventButton(1, events, null));
        controls.add(createEventButton(2, events, variance));

        panel.add(timeline, BorderLayout.CENTER);
        return panel;
    }

    private Component createEventButton(final int type, final EventData events,
            final AtomicInteger variance) {
        final String start = "Start " + (variance != null ? "blocking " : "") + "event type " + type;
        final String stop = "Stop event type " + type;
        return createButton(start, new ActionListener() {
            EventData.Event event = null;
            int var = 0;

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JButton button = (JButton) actionEvent.getSource();
                if (event != null) {
                    event.stop(System.currentTimeMillis());
                    event = null;
                    if (variance != null) {
                        variance.set(var);
                    }
                    button.setText(start);
                } else {
                    event = events.start(System.currentTimeMillis(), type);
                    if (variance != null) {
                        var = variance.get();
                        variance.set(0);
                    }
                    button.setText(stop);
                }
            }
        });
    }

    public static void main(String[] args) {
        AnimatedComponentVisualTests dialog = new AnimatedComponentVisualTests();

        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }
}
