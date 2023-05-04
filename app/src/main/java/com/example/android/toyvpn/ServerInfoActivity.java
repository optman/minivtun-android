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
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;


public class ServerInfoActivity extends Activity {

    public static final String RESULT_CODE = "result_code";
    public static final int RESULT_NONE = 0;
    public static final int RESULT_SAVE = 1;
    public static final int RESULT_DELETE = 2;
    public static final String PARAM_POSITION = "position";
    public static final String PARAM_DATA = "data";



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.server_info);


        final TextView serverName = findViewById(R.id.server_name);
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

        int position = getIntent().getIntExtra(PARAM_POSITION, -1);
        JSONObject data;
        try {
            if (position >= 0) {
                data = new JSONObject(getIntent().getStringExtra(PARAM_DATA));
                serverName.setText(data.getString(Prefs.SERVER_NAME));
                serverAddress.setText(data.getString(Prefs.SERVER_ADDRESS));
                rndzServerAddress.setText(data.getString(Prefs.RNDZ_SERVER_ADDRESS));
                rndzRemoteId.setText(data.getString(Prefs.RNDZ_REMOTE_ID));
                rndzLocalId.setText(data.getString(Prefs.RNDZ_LOCAL_ID));
                cipher.setText(data.getString(Prefs.CIPHER));
                sharedSecret.setText(data.getString(Prefs.SHARED_SECRET));
                localIpv4.setText(data.getString(Prefs.LOCAL_IPv4));
                localIpv6.setText(data.getString(Prefs.LOCAL_IPv6));
                routes.setText(data.getString(Prefs.ROUTES));
                dns.setText(data.getString(Prefs.DNS));
            } else {
                data = new JSONObject();
                serverName.setText("server name");
            }

        } catch (JSONException e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_LONG);
            return;
        }

        findViewById(R.id.save).setOnClickListener(v -> {
            try {
                data.put(Prefs.SERVER_NAME, serverName.getText().toString());
                data.put(Prefs.SERVER_ADDRESS, serverAddress.getText().toString());
                data.put(Prefs.RNDZ_SERVER_ADDRESS, rndzServerAddress.getText().toString());
                data.put(Prefs.RNDZ_REMOTE_ID, rndzRemoteId.getText().toString());
                data.put(Prefs.RNDZ_LOCAL_ID, rndzLocalId.getText().toString());
                data.put(Prefs.CIPHER, cipher.getText().toString());
                data.put(Prefs.SHARED_SECRET, sharedSecret.getText().toString());
                data.put(Prefs.LOCAL_IPv4, localIpv4.getText().toString());
                data.put(Prefs.LOCAL_IPv6, localIpv6.getText().toString());
                data.put(Prefs.ROUTES, routes.getText().toString());
                data.put(Prefs.DNS, dns.getText().toString());

            } catch (JSONException e) {
                e.printStackTrace();
            }

            Intent result = new Intent();
            result.putExtra(RESULT_CODE, RESULT_SAVE);
            result.putExtra(PARAM_POSITION, position);
            result.putExtra(PARAM_DATA, data.toString());

            setResult(RESULT_OK, result);
            finish();
        });


        findViewById(R.id.delete).setOnClickListener(v -> {
                    Intent result = new Intent();
                    result.putExtra(RESULT_CODE, RESULT_DELETE);
                    result.putExtra(PARAM_POSITION, position);
                    setResult(RESULT_OK, result);
                    finish();
                }
        );


    }


}
