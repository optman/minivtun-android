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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.TextView;


public class ToyVpnClient extends Activity {
    public interface Prefs {
        String NAME = "connection";
        String SERVER_ADDRESS = "server.address";
        String RNDZ_SERVER_ADDRESS= "rndz.server.address";
        String RNDZ_REMOTE_ID = "rndz.remote.id";
        String RNDZ_LOCAL_ID = "rndz.local_id";
        String CIPHER = "cipher";
        String SHARED_SECRET = "shared.secret";
        String LOCAL_IPv4 = "local.ipv4";
        String LOCAL_IPv6 = "local.ipv6";
        String ROUTES = "routes";
        String DNS = "dns";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.form);

        final TextView serverAddress = findViewById(R.id.server_address);
        final TextView rndzServerAddress = findViewById(R.id.rndz_server_address);
        final TextView rndzRemoteId = findViewById(R.id.rndz_remote_id);
        final TextView rndzLocalId = findViewById(R.id.rndz_local_id);
        final TextView cipher = findViewById(R.id.cipher);
        final TextView sharedSecret = findViewById(R.id.secret);
        final TextView localIpv4 = findViewById(R.id.local_ipv4);
        final TextView localIpv6 = findViewById(R.id.local_ipv6);
        final TextView routes = findViewById(R.id.routes);
        final TextView dns = findViewById(R.id.dns);

        final SharedPreferences prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        serverAddress.setText(prefs.getString(Prefs.SERVER_ADDRESS, ""));
        rndzServerAddress.setText(prefs.getString(Prefs.RNDZ_SERVER_ADDRESS, ""));
        rndzRemoteId.setText(prefs.getString(Prefs.RNDZ_REMOTE_ID, ""));
        rndzLocalId.setText(prefs.getString(Prefs.RNDZ_LOCAL_ID, ""));
        cipher.setText(prefs.getString(Prefs.CIPHER, ""));
        sharedSecret.setText(prefs.getString(Prefs.SHARED_SECRET, ""));
        localIpv4.setText(prefs.getString(Prefs.LOCAL_IPv4, ""));
        localIpv6.setText(prefs.getString(Prefs.LOCAL_IPv6, ""));
        routes.setText(prefs.getString(Prefs.ROUTES, ""));
        dns.setText(prefs.getString(Prefs.DNS, ""));

        findViewById(R.id.connect).setOnClickListener(v -> {
            prefs.edit()
                    .putString(Prefs.SERVER_ADDRESS, serverAddress.getText().toString())
                    .putString(Prefs.RNDZ_SERVER_ADDRESS, rndzServerAddress.getText().toString())
                    .putString(Prefs.RNDZ_REMOTE_ID, rndzRemoteId.getText().toString())
                    .putString(Prefs.RNDZ_LOCAL_ID, rndzLocalId.getText().toString())
                    .putString(Prefs.CIPHER, cipher.getText().toString())
                    .putString(Prefs.SHARED_SECRET, sharedSecret.getText().toString())
                    .putString(Prefs.LOCAL_IPv4, localIpv4.getText().toString())
                    .putString(Prefs.LOCAL_IPv6, localIpv6.getText().toString())
                    .putString(Prefs.ROUTES, routes.getText().toString())
                    .putString(Prefs.DNS, dns.getText().toString())
                    .commit();

            Intent intent = VpnService.prepare(ToyVpnClient.this);
            if (intent != null) {
                startActivityForResult(intent, 0);
            } else {
                onActivityResult(0, RESULT_OK, null);
            }
        });
        findViewById(R.id.disconnect).setOnClickListener(v -> {
            startService(getServiceIntent().setAction(ToyVpnService.ACTION_DISCONNECT));
        });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (result == RESULT_OK) {
            startService(getServiceIntent().setAction(ToyVpnService.ACTION_CONNECT));
        }
    }

    private Intent getServiceIntent() {
        return new Intent(this, ToyVpnService.class);
    }
}
