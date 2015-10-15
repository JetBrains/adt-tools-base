/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.perflib.vmtrace.viz;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.perflib.vmtrace.SearchResult;
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.perflib.vmtrace.VmTraceData;
import com.android.tools.perflib.vmtrace.VmTraceParser;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * This is just a simple test application that loads a particular trace file,
 * and displays the stackchart view it within a JFrame.
 */
public class TraceView {
    private static final String TRACE_FILE_NAME = "/play.dalvik.trace";
    private static final String DEFAULT_THREAD_NAME = "main";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                createAndShowUI();
            }
        });
    }

    private static void createAndShowUI() {
        final TraceViewPanel traceViewPanel = new TraceViewPanel();
        final VmTraceData traceData = getVmTraceData(TRACE_FILE_NAME);

        JFrame frame = new JFrame("TraceViewTestApplication");
        frame.setLayout(new BorderLayout());
        frame.add(traceViewPanel, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setSize(1200, 800);
        frame.setVisible(true);

        traceViewPanel.setTrace(traceData);
    }

    private static VmTraceData getVmTraceData(String tracePath) {
        VmTraceParser parser = new VmTraceParser(getFile(tracePath));
        try {
            parser.parse();
        } catch (IOException e) {
            fail("Unexpected error while reading tracing file: " + tracePath);
        }

        return parser.getTraceData();
    }

    private static File getFile(String path) {
        URL resource = TraceView.class.getResource(path);
        // Note: When running from an IntelliJ, make sure the IntelliJ compiler settings treats
        // *.trace files as resources, otherwise they are excluded from compiler output
        // resulting in a NPE.
        assertNotNull(path + " not found", resource);
        return new File(resource.getFile());
    }

    public static class TraceViewPanel extends JPanel {
        private VmTraceData mTraceData;

        private final TraceViewCanvas mTraceViewCanvas;
        private JComboBox mThreadCombo;
        private JCheckBox mClockSelector;
        private JCheckBox mUseInclusiveTimeForColor;
        private JTextField mSearchField;
        private JLabel mSearchResults;

        public TraceViewPanel() {
            setLayout(new BorderLayout());

            add(createControlPanel(), BorderLayout.NORTH);

            mTraceViewCanvas = new TraceViewCanvas();
            add(mTraceViewCanvas, BorderLayout.CENTER);
        }

        private JPanel createControlPanel() {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));

            JLabel l = new JLabel("Thread: ");
            p.add(l);

            mThreadCombo = new JComboBox();
            p.add(mThreadCombo);
            mThreadCombo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList list, Object value, int index,
                        boolean isSelected, boolean cellHasFocus) {
                    Object v = value instanceof ThreadInfo ? ((ThreadInfo) value).getName() : value;
                    return super.getListCellRendererComponent(list, v, index, isSelected,
                            cellHasFocus);
                }
            });

            mClockSelector = new JCheckBox("Use Wallclock Time");
            mClockSelector.setSelected(true);
            p.add(mClockSelector);

            mUseInclusiveTimeForColor = new JCheckBox("Color by inclusive time");
            p.add(mUseInclusiveTimeForColor);

            ActionListener listener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    assert mTraceViewCanvas != null;

                    if (e.getSource() == mThreadCombo) {
                        mTraceViewCanvas.displayThread((ThreadInfo) mThreadCombo.getSelectedItem());
                    } else if (e.getSource() == mClockSelector) {
                        mTraceViewCanvas.setRenderClock(mClockSelector.isSelected() ?
                                ClockType.GLOBAL : ClockType.THREAD);
                    } else if (e.getSource() == mUseInclusiveTimeForColor) {
                        mTraceViewCanvas.setUseInclusiveTimeForColorAssignment(
                                mUseInclusiveTimeForColor.isSelected());
                    }
                }
            };
            mThreadCombo.addActionListener(listener);
            mClockSelector.addActionListener(listener);
            mUseInclusiveTimeForColor.addActionListener(listener);

            l = new JLabel("Find: ");
            p.add(l);

            mSearchField = new JTextField(20);
            p.add(mSearchField);
            mSearchField.setEnabled(false);
            mSearchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    searchTextUpdated();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    searchTextUpdated();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    searchTextUpdated();
                }

                private void searchTextUpdated() {
                    if (mTraceData == null) {
                        return;
                    }

                    String pattern = getText(mSearchField.getDocument());
                    if (pattern.length() < 3) {
                        mTraceViewCanvas.setHighlightMethods(null);
                        mSearchResults.setText("");
                        return;
                    }

                    ThreadInfo thread = (ThreadInfo) mThreadCombo.getSelectedItem();
                    SearchResult results = mTraceData.searchFor(pattern, thread);
                    mTraceViewCanvas.setHighlightMethods(results.getMethods());

                    String result = String.format("%1$d methods, %2$d instances",
                            results.getMethods().size(), results.getInstances().size());
                    mSearchResults.setText(result);
                }

                private String getText(Document document) {
                    try {
                        return document.getText(0, document.getLength());
                    } catch (BadLocationException e) {
                        return "";
                    }
                }
            });

            mSearchResults = new JLabel();
            p.add(mSearchResults);

            return p;
        }

        public void setTrace(VmTraceData traceData) {
            mTraceData = traceData;

            List<ThreadInfo> threads = traceData.getThreads(true);
            ThreadInfo defaultThread = Iterables.find(threads, new Predicate<ThreadInfo>() {
                @Override
                public boolean apply(ThreadInfo input) {
                    return DEFAULT_THREAD_NAME.equals(input.getName());
                }
            }, threads.get(0));

            mThreadCombo.setModel(new DefaultComboBoxModel(threads.toArray()));
            mThreadCombo.setEnabled(true);
            mSearchField.setEnabled(true);

            mTraceViewCanvas.setTrace(traceData, defaultThread, ClockType.GLOBAL);
            mThreadCombo.setSelectedItem(defaultThread);
        }
    }
}
