/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.example.android.toyvpn;

/*import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
 */

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class ToyVpnService extends VpnService implements Handler.Callback {
    private static final String TAG = ToyVpnService.class.getSimpleName();

    public static final String ACTION_CONNECT = "com.example.android.toyvpn.START";
    public static final String ACTION_DISCONNECT = "com.example.android.toyvpn.STOP";

    public static final String PARAM_VPN_CONFIG = "vpn_config";

    private Handler mHandler;

    private static class Connection {
        Thread thread;
        ParcelFileDescriptor pfd;
        ToyVpnConnection connection;

        public Connection(Thread thread, ParcelFileDescriptor pfd, ToyVpnConnection connection) {
            this.thread = thread;
            this.pfd = pfd;
            this.connection = connection;
        }
    }

    private final AtomicReference<Thread> mConnectingThread = new AtomicReference<>();
    private final AtomicReference<Connection> mConnection = new AtomicReference<>();

    private PendingIntent mConfigureIntent;

    private JSONObject vpnConfig;

    @Override
    public void onCreate() {
        // The handler is only used to show messages.
        if (mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }

        // Create the intent to "configure" the connection (just start ToyVpnClient).
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        mConfigureIntent = PendingIntent.getActivity(this, 0, new Intent(this, ServerInfoActivity.class), flags);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            disconnect();
            return START_NOT_STICKY;
        } else {

            try {
                vpnConfig = new JSONObject(intent.getStringExtra(PARAM_VPN_CONFIG));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            connect();
            return START_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        disconnect();
    }

    @Override
    public boolean handleMessage(Message message) {
        Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show();
        if (message.what != R.string.disconnected) {
            // updateForegroundNotification(message.what);
        }
        return true;
    }

    private void connect() {
        // Become a foreground service. Background services can be VPN services too, but
        // they can
        // be killed by background check before getting a chance to receive onRevoke().
        // updateForegroundNotification(R.string.connecting);
        mHandler.sendEmptyMessage(R.string.connecting);

        startConnection(new ToyVpnConnection(this, new ToyVpnConfig(vpnConfig)));
    }

    private void startConnection(final ToyVpnConnection connection) {
        // Replace any existing connecting thread with the new one.
        final Thread thread = new Thread(connection, "ToyVpnThread");
        setConnectingThread(thread);

        // Handler to mark as connected once onEstablish is called.
        connection.setConfigureIntent(mConfigureIntent);
        connection.setOnEstablishListener(tunInterface -> {
            mHandler.sendEmptyMessage(R.string.connected);

            mConnectingThread.compareAndSet(thread, null);
            setConnection(new Connection(thread, tunInterface, connection));
        });
        thread.start();
    }

    private void setConnectingThread(final Thread thread) {
        final Thread oldThread = mConnectingThread.getAndSet(thread);
        if (oldThread != null) {
            oldThread.interrupt();
        }
    }

    private void setConnection(final Connection connection) {
        final Connection oldConnection = mConnection.getAndSet(connection);
        if (oldConnection != null) {
            try {
                oldConnection.connection.stop();
                oldConnection.pfd.close();
                oldConnection.thread.interrupt();
            } catch (IOException e) {
                Log.e(TAG, "Closing VPN interface", e);
            }
        }
    }

    private void disconnect() {
        mHandler.sendEmptyMessage(R.string.disconnected);
        setConnectingThread(null);
        setConnection(null);
        stopForegroundService();
    }

    private void stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE);
    }
    /*
     * private void updateForegroundNotification(final int message) {
     * final String NOTIFICATION_CHANNEL_ID = "ToyVpn";
     * NotificationManager mNotificationManager = (NotificationManager)
     * getSystemService(
     * NOTIFICATION_SERVICE);
     * mNotificationManager.createNotificationChannel(new NotificationChannel(
     * NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
     * NotificationManager.IMPORTANCE_DEFAULT));
     * startForeground(1, new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
     * .setSmallIcon(R.drawable.ic_vpn)
     * .setContentText(getString(message))
     * .setContentIntent(mConfigureIntent)
     * .build());
     * }
     */
}
