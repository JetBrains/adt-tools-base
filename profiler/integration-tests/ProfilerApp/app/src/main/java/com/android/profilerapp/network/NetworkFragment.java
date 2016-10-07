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
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.profilerapp.R;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

public class NetworkFragment extends Fragment {

    private static final String TAG = "NetworkFragment";

    static {
        System.loadLibrary("profilermodule");
    }

    private native int getConnectionCount(String uidString);
    private native long[] getTrafficBytes(String uidString);

    private final AtomicInteger myNumDownloadsTotal = new AtomicInteger();
    private final AtomicInteger myNumDownloadsSoFar = new AtomicInteger();

    private final int myUid = Process.myUid();
    private View myFragmentView;
    private ConnectivityManager myConnectivityManager;
    private Thread myStatisticsThread;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        myFragmentView = inflater.inflate(R.layout.fragment_network, container, false);
        myConnectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);

        final SeekBar numDownloadsSeekBar = ((SeekBar) myFragmentView.findViewById(R.id.seekNumDownloads));
        final Button downloadButton = (Button) myFragmentView.findViewById(R.id.download);
        updateButtonText(numDownloadsSeekBar, downloadButton);
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int numSimultaneousDownloads = numDownloadsSeekBar.getProgress() + 1;
                for (int i = 0; i < numSimultaneousDownloads; i++) {
                    download();
                }
            }
        });
        numDownloadsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateButtonText(numDownloadsSeekBar, downloadButton);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        if (myStatisticsThread == null) {
            myStatisticsThread = getStatisticThread();
            myStatisticsThread.start();
        }

        return myFragmentView;
    }

    private void updateButtonText(SeekBar numDownloadsSeekBar, Button downloadButton) {
        downloadButton.setText(String.format(getString(R.string.network_download_images),
            numDownloadsSeekBar.getProgress() + 1));
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
            private long[] myStartBytes;
            private long[] myBytes;

            private void initialize() {
                statistics = new Statistics();
                myStartBytes = getTrafficBytes(Integer.toString(myUid));
            }

            @Override
            public void run() {
                initialize();
                while (!Thread.currentThread().isInterrupted()) {
                    myBytes = getTrafficBytes(Integer.toString(myUid));
                    statistics.sendBytesFromFile = myBytes[0] - myStartBytes[0];
                    statistics.receiveBytesFromFile = myBytes[1] - myStartBytes[1];

                    // Gets the bytes from API too, because API read is later than file read, API results may be a little larger.
                    myBytes[0] = TrafficStats.getUidTxBytes(myUid) - myStartBytes[0];
                    myBytes[1] = TrafficStats.getUidRxBytes(myUid) - myStartBytes[1];
                    if (statistics.sendBytesFromFile > myBytes[0] || statistics.receiveBytesFromFile > myBytes[1]) {
                        Log.d(TAG, String.format("Bytes reported not in sync. TrafficStats: %1$d, %2$d, getTrafficBytes: %3$d, %4$d",
                                myBytes[0], myBytes[1], statistics.sendBytesFromFile, statistics.receiveBytesFromFile));
                    }

                    NetworkInfo networkInfo = myConnectivityManager.getActiveNetworkInfo();
                    statistics.networkName = networkInfo != null && networkInfo.getSubtype() != TelephonyManager.NETWORK_TYPE_UNKNOWN
                            ? networkInfo.getSubtypeName() : networkInfo.getTypeName();
                    statistics.radioStatus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            ? myConnectivityManager.isDefaultNetworkActive()
                            ? "Radio high power" : "Radio not high power" : "Radio status unknown";
                    statistics.openConnectionCount = getConnectionCount(Integer.toString(myUid));
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
                TextView bytesFromFileView = (TextView) myFragmentView.findViewById(R.id.bytesFromFile);
                String bytesText = String.format("Bytes from file: sent %1$d, received %2$d",
                        statistics.sendBytesFromFile, statistics.receiveBytesFromFile);
                bytesFromFileView.setText(bytesText);
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
                long timeStart = SystemClock.elapsedRealtime();
                URL url;
                try {
                    int next = myNumDownloadsTotal.getAndIncrement() % DownloadUrls.IMAGE_URLS.length;
                    url = new URL(DownloadUrls.IMAGE_URLS[next]);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    return;
                }

                HttpURLConnection connection;
                try {
                    connection = (HttpURLConnection) url.openConnection();
                    // Client closes the socket on its end once it receives the server response. We do not need connection reusing.
                    connection.setRequestProperty("Connection", "Close");
                    String urlString = url.toString();
                    Log.d(TAG, String.format("Connection opened [%d %d]: %s", myUid, Thread.currentThread().getId(),
                        urlString));
                    BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                    while(in.read() != -1) {}
                    Log.d(TAG, String.format("Finished %d / %d (time %d ms): %s", myNumDownloadsSoFar.incrementAndGet(),
                            myNumDownloadsTotal.get(), SystemClock.elapsedRealtime() - timeStart, urlString));
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
        long sendBytesFromFile;
        long receiveBytesFromFile;
        String networkName;
        String radioStatus;
        int openConnectionCount;
    }
}
