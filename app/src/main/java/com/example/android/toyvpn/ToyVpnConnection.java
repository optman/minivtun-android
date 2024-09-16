/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.github.optman.minivtun.Native;
import com.github.optman.minivtun.Client;

public class ToyVpnConnection implements Runnable {
    /**
     * Callback interface to let the {@link ToyVpnService} know about new
     * connections
     * and update the foreground notification with connection status.
     */
    public interface OnEstablishListener {
        void onEstablish(ParcelFileDescriptor tunInterface);
    }

    private final VpnService mService;
    private final ToyVpnConfig mConfig;

    private PendingIntent mConfigureIntent;
    private OnEstablishListener mOnEstablishListener;

    private Client client;

    public static final String ACTION_VPN_DISCONNECTED = "com.example.android.toyvpn.VPN_DISCONNECTED";

    public ToyVpnConnection(final VpnService service, final ToyVpnConfig config) {
        mService = service;
        mConfig = config;
    }

    /**
     * Optionally, set an intent to configure the VPN. This is {@code null} by
     * default.
     */
    public void setConfigureIntent(PendingIntent intent) {
        mConfigureIntent = intent;
    }

    public void setOnEstablishListener(OnEstablishListener listener) {
        mOnEstablishListener = listener;
    }

    public void stop() {
        if (client != null) {
            client.stop();
        }
    }

    @Override
    public void run() {
        Log.i(getTag(), "Starting");
        Intent intent = new Intent(ACTION_VPN_DISCONNECTED)
                .setPackage(mService.getPackageName());
        ParcelFileDescriptor iface = null;
        client = Native.prepare(mConfig);
        if (client == null) {
            Log.e(getTag(), "prepare config fail");
            mService.sendBroadcast(intent);
            return;
        }
        try {
            iface = configure();
            mService.protect(client.socket);
            client.run(iface.detachFd());
        } catch (Exception e) {
            Log.e(getTag(), "vpn exit", e);
        } finally {
            client.free();
            if (iface != null) {
                try {
                    iface.close();
                } catch (IOException e) {
                    Log.e(getTag(), "Unable to close interface", e);
                }
            }
        }
        mService.sendBroadcast(intent);
    }

    private ParcelFileDescriptor configure() throws PackageManager.NameNotFoundException {
        // Configure a builder while parsing the parameters.
        VpnService.Builder builder = mService.new Builder();

        for (String ip : new String[] { mConfig.localIpv4, mConfig.localIpv6 }) {
            String[] parts = ip.split("/");
            if (parts.length == 2) {
                builder.addAddress(parts[0], Integer.parseInt(parts[1]));
            }
        }

        for (String route : mConfig.routes.split(",")) {
            String[] parts = route.split("/");
            if (parts.length == 2) {
                builder.addRoute(parts[0], Integer.parseInt(parts[1]));
            }
        }

        for (String dns : mConfig.dns.split(",")) {
            if (!mConfig.dns.isEmpty())
                builder.addDnsServer(mConfig.dns);
        }

        builder.setMtu(1300);

        if (mConfig.vpnMode.equals(VpnMode.ALLOW_SELECTED)) {
            for (String app : mConfig.selectedApps) {
                builder.addAllowedApplication(app);
            }
        } else if (mConfig.vpnMode.equals(VpnMode.DISALLOW_SELECTED)) {
            for (String app : mConfig.selectedApps) {
                builder.addDisallowedApplication(app);
            }
        }

        builder.setSession(mConfig.server).setConfigureIntent(mConfigureIntent);
        ParcelFileDescriptor vpnInterface;
        synchronized (mService) {
            vpnInterface = builder.establish();
            if (mOnEstablishListener != null) {
                mOnEstablishListener.onEstablish(vpnInterface);
            }
        }
        Log.i(getTag(), "New interface: " + vpnInterface);
        return vpnInterface;
    }

    private final String getTag() {
        return ToyVpnConnection.class.getSimpleName();
    }
}
