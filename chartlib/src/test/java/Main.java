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
import com.android.tools.chartlib.TimelineComponent;
import com.android.tools.chartlib.TimelineData;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Main extends JDialog {

  public Main() {
    JPanel contentPane = new JPanel(new BorderLayout());
    JButton close = new JButton("Close");
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Timeline", getTimelineExample());
    tabs.addTab("PieChart", getPieChartExample());

    contentPane.setPreferredSize(new Dimension(1280, 1024));
    contentPane.add(tabs, BorderLayout.CENTER);

    JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(close, BorderLayout.EAST);
    contentPane.add(bottom, BorderLayout.SOUTH);

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

  private JPanel getPieChartExample() {
    // TODO
    return new JPanel();
  }

  private JPanel getTimelineExample() {
    TimelineData data = new TimelineData(2, 2000);
    data.add(100, 1, 0, 10.0f, 5.0f);
    data.add(200, 1, 0, 20.0f, 5.0f);
    TimelineComponent timeline = new TimelineComponent(data, 100.0f, 100.0f, 1000.0f, 10.0f);
    timeline.setPreferredSize(new Dimension(1200, 600));
    JPanel panel = new JPanel();
    panel.add(timeline);
    return panel;
  }

  public static void main(String[] args) {
    Main dialog = new Main();
    dialog.pack();
    dialog.setVisible(true);
    System.exit(0);
  }
}
