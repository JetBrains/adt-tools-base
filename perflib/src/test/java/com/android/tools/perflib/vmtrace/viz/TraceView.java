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

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;

import com.android.tools.perflib.vmtrace.Call;
import com.android.tools.perflib.vmtrace.VmTraceData;
import com.android.tools.perflib.vmtrace.VmTraceParser;
import com.android.utils.SparseArray;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import javax.swing.*;

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
        frame.setSize(800, 600);
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
        private final TraceViewCanvas mTraceViewCanvas;
        private JComboBox mThreadCombo;

        public TraceViewPanel() {
            setLayout(new BorderLayout());

            add(createControlPanel(), BorderLayout.NORTH);

            mTraceViewCanvas = new TraceViewCanvas();
            add(mTraceViewCanvas, BorderLayout.CENTER);
        }

        private JPanel createControlPanel() {
            JPanel p = new JPanel();

            JLabel l = new JLabel("Thread: ");
            p.add(l);

            mThreadCombo = new JComboBox();
            mThreadCombo.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    assert mTraceViewCanvas != null;
                    mTraceViewCanvas.displayThread((String)mThreadCombo.getSelectedItem());
                }
            });
            p.add(mThreadCombo);

            return p;
        }

        public void setTrace(VmTraceData traceData) {
            SparseArray<String> threads = traceData.getThreads();
            java.util.List<String> threadNames = new ArrayList<String>(threads.size());
            for (int i = 0; i < threads.size(); i++) {
                Call topLevelCall = traceData.getTopLevelCall(threads.keyAt(i));
                if (topLevelCall != null) {
                    threadNames.add(threads.valueAt(i));
                }
            }

            mThreadCombo.setModel(new DefaultComboBoxModel(threadNames.toArray()));
            mThreadCombo.setEnabled(true);

            mTraceViewCanvas.setTrace(traceData, DEFAULT_THREAD_NAME);
        }
    }
}
