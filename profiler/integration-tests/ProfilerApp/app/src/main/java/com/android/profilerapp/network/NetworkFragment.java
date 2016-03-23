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

package com.android.profilerapp.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.support.v4.app.Fragment;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.android.profilerapp.R;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class NetworkFragment extends Fragment {

    static {
        System.loadLibrary("profilermodule");
    }

    private static final String DOWNLOAD_URL = "http://ipv4.download.thinkbroadband.com:8080/5MB.zip";

    private native int getConnectionCount(String uidString);

    private final int myUid = Process.myUid();
    private View myFragmentView;
    private ConnectivityManager myConnectivityManager;
    private Thread myStatisticsThread;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        myFragmentView = inflater.inflate(R.layout.fragment_network, container, false);
        myConnectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);

        Button downloadButton = (Button) myFragmentView.findViewById(R.id.download);
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                download();
            }
        });

        if (myStatisticsThread == null) {
            myStatisticsThread = getStatisticThread();
            myStatisticsThread.start();
        }

        return myFragmentView;
    }

    @Override
    public void onDestroy() {
        if (myStatisticsThread != null) {
            myStatisticsThread.interrupt();
            myStatisticsThread = null;
        }
    }

    private Thread getStatisticThread() {
        return new Thread(new Runnable() {

            private Statistics statistics;
            private long myStartSendBytes;
            private long myStartReceiveBytes;

            private void initialize() {
                statistics = new Statistics();
                myStartSendBytes = TrafficStats.getUidTxBytes(myUid);
                myStartReceiveBytes = TrafficStats.getUidRxBytes(myUid);
            }

            @Override
            public void run() {
                initialize();
                while (!Thread.currentThread().isInterrupted()) {
                    statistics.sendBytes = TrafficStats.getUidTxBytes(myUid) - myStartSendBytes;
                    statistics.receiveBytes = TrafficStats.getUidRxBytes(myUid) - myStartReceiveBytes;
                    NetworkInfo networkInfo = myConnectivityManager.getActiveNetworkInfo();
                    statistics.networkName = networkInfo != null && networkInfo.getSubtype() != TelephonyManager.NETWORK_TYPE_UNKNOWN
                            ? networkInfo.getSubtypeName() : networkInfo.getTypeName();
                    statistics.radioStatus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            ? myConnectivityManager.isDefaultNetworkActive()
                            ? "Radio high power" : "Radio not high power" : "Radio status unknown";
                    statistics.openConnectionCount = getConnectionCount("" + myUid);
                    postStatistics(statistics);
                    try {
                        Thread.currentThread().sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void postStatistics(final Statistics statistics) {
        myFragmentView.post(new Runnable() {
            @Override
            public void run() {
                TextView bytesView = (TextView) myFragmentView.findViewById(R.id.bytes);
                String bytesText = String.format("Sent / Received : %1$d / %2$d bytes",
                        statistics.sendBytes, statistics.receiveBytes);
                bytesView.setText(bytesText);
                TextView networkNameView = (TextView) myFragmentView.findViewById(R.id.networkType);
                networkNameView.setText("Network type: " + statistics.networkName);
                TextView radioStatusView = (TextView) myFragmentView.findViewById(R.id.radioStatus);
                radioStatusView.setText(statistics.radioStatus);
                TextView openConnectionsView = (TextView) myFragmentView.findViewById(R.id.openConnections);
                openConnectionsView.setText("Open connections count: " + statistics.openConnectionCount);

            }
        });
    }

    private void download() {
        Thread downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                URL url;
                try {
                    url = new URL(DOWNLOAD_URL);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    return;
                }

                HttpURLConnection connection;
                try {
                    connection = (HttpURLConnection) url.openConnection();
                    BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                    while(in.read() != -1) {}
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
                connection.disconnect();
            }
        });
        downloadThread.start();
    }

    private static class Statistics {
        long sendBytes;
        long receiveBytes;
        String networkName;
        String radioStatus;
        int openConnectionCount;
    }
}
