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

import com.android.tools.chartlib.AnimatedComponent;
import com.android.tools.chartlib.Choreographer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.util.LinkedList;
import java.util.List;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class VisualTests extends JDialog {

    private List<AnimatedComponent> mComponents;

    private List<VisualTest> mTests = new LinkedList<VisualTest>();

    private static final int CHOREOGRAPHER_FPS = 40;

    private Choreographer mChoreographer;

    private VisualTests() {
        final JPanel contentPane = new JPanel(new BorderLayout());
        final JTabbedPane tabs = new JTabbedPane();

        addTests(new Choreographer(CHOREOGRAPHER_FPS));
        resetTabs(tabs);

        contentPane.setPreferredSize(new Dimension(1280, 1024));
        contentPane.add(tabs, BorderLayout.CENTER);

        JButton close = new JButton("Close");
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        JButton reset = new JButton("Reset");
        reset.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetTests();
                resetTabs(tabs);
            }
        });
        JPanel bottom = new JPanel(new BorderLayout());
        Box bottomButtonsBox = Box.createHorizontalBox();
        bottomButtonsBox.add(reset);
        bottomButtonsBox.add(close);
        bottom.add(bottomButtonsBox, BorderLayout.EAST);
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

        final JButton step = new JButton("Step");
        step.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                mChoreographer.step();
            }
        });
        final JCheckBox update = new JCheckBox("Update");
        update.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                mChoreographer.setUpdate(update.isSelected());
                step.setEnabled(!update.isSelected());
            }
        });
        update.setSelected(true);
        step.setEnabled(false);
        final JCheckBox darcula = new JCheckBox("Darcula");
        darcula.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                setDarculaMode(darcula.isSelected());
            }
        });
        controls.add(darcula);
        controls.add(update);
        controls.add(step);
        contentPane.add(controls, BorderLayout.WEST);

        setDarculaMode(false);
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(close);
    }

    private void addTests(Choreographer choreographer) {
        mChoreographer = choreographer;
        mTests.add(new AxisLineChartVisualTest(choreographer));
        mTests.add(new StateChartVisualTest(choreographer));
        mTests.add(new LineChartVisualTest(choreographer));
        mTests.add(new SunburstVisualTest(choreographer));
        mTests.add(new TimelineVisualTest(choreographer));
    }

    private void resetTests() {
        mTests = new LinkedList<VisualTest>();
        addTests(new Choreographer(CHOREOGRAPHER_FPS));
    }

    private void resetTabs(JTabbedPane tabs) {
        int currentTabIndex = tabs.getSelectedIndex();
        tabs.removeAll();
        // Make sure to reset the components list
        mComponents = new LinkedList<AnimatedComponent>();
        for (VisualTest test : mTests) {
            test.registerComponents(mComponents);
            test.initialize();
            tabs.addTab(test.getName(), test.getPanel());
        }
        // Return to previous selected tab if there was one
        if (currentTabIndex != -1) {
            tabs.setSelectedIndex(currentTabIndex);
        }
    }

    private void setDarculaMode(boolean dark) {
        for (VisualTest c : mTests) {
            if (c.getPanel() != null) {
                c.getPanel().setBackground(dark ? new Color(60, 63, 65) : new Color(244, 244, 244));
            }
        }
    }

    interface Value {
        void set(int v);
        int get();
    }

    static JPanel createVaribleSlider(String name, final int a, final int b, final Value value) {
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
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    static JPanel createControlledPane(JPanel panel, AnimatedComponent animated) {
        panel.setLayout(new BorderLayout());
        panel.add(animated, BorderLayout.CENTER);

        JPanel controls = new JPanel();
        LayoutManager manager = new BoxLayout(controls, BoxLayout.Y_AXIS);
        controls.setLayout(manager);
        panel.add(controls, BorderLayout.WEST);
        return controls;
    }


    static Component createButton(String label, ActionListener action) {
        JButton button = createButton(label);
        button.addActionListener(action);
        return button;
    }

    static Component createCheckbox(String label, ItemListener action) {
        return createCheckbox(label, action, false);
    }

    static Component createCheckbox(String label, ItemListener action, boolean selected) {
        JCheckBox button = new JCheckBox(label);
        button.addItemListener(action);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getMaximumSize().height));
        button.setSelected(selected);
        return button;
    }

    static JButton createButton(String label) {
        JButton button = new JButton(label);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getMaximumSize().height));
        return button;
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                VisualTests dialog = new VisualTests();

                dialog.pack();
                dialog.setVisible(true);
            }
        });
        System.exit(0);
    }
}
