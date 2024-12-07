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

import androidx.appcompat.app.AppCompatActivity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.view.View;
import android.widget.AdapterView;
import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.content.Context;
import android.widget.Button;
import android.content.pm.PackageInfo;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import android.os.Build;
import java.util.Objects;

public class ServerInfoActivity extends AppCompatActivity {

    public static final String RESULT_CODE = "result_code";
    public static final int RESULT_NONE = 0;
    public static final int RESULT_SAVE = 1;
    public static final int RESULT_DELETE = 2;
    public static final String PARAM_POSITION = "position";
    public static final String PARAM_DATA = "data";

    private Spinner vpnModeSpinner;
    private List<String> selectedAppIds = new ArrayList<>();
    private Map<String, String> appIdToNameMap = new HashMap<>();
    private Map<String, String> appNameToIdMap = new HashMap<>();
    private Button selectAppsButton;

    private ActivityResultLauncher<Intent> activityResultLauncher;

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
        final TextView mtu = findViewById(R.id.mtu);

        vpnModeSpinner = findViewById(R.id.vpn_mode);
        selectAppsButton = findViewById(R.id.select_apps);

        setupActivityResultLauncher();
        setupVpnModeSpinner();
        setupSelectAppsButton();

        int position = getIntent().getIntExtra(PARAM_POSITION, -1);
        JSONObject data;
        try {
            if (position >= 0) {
                data = new JSONObject(getIntent().getStringExtra(PARAM_DATA));
                serverName.setText(data.optString(ToyVpnConfig.json.SERVER_NAME, ""));
                serverAddress.setText(data.optString(ToyVpnConfig.json.SERVER_ADDRESS, ""));
                rndzServerAddress.setText(data.optString(ToyVpnConfig.json.RNDZ_SERVER_ADDRESS, ""));
                rndzRemoteId.setText(data.optString(ToyVpnConfig.json.RNDZ_REMOTE_ID, ""));
                rndzLocalId.setText(data.optString(ToyVpnConfig.json.RNDZ_LOCAL_ID, ""));
                cipher.setText(data.optString(ToyVpnConfig.json.CIPHER, ""));
                sharedSecret.setText(data.optString(ToyVpnConfig.json.SHARED_SECRET, ""));
                localIpv4.setText(data.optString(ToyVpnConfig.json.LOCAL_IPv4, ""));
                localIpv6.setText(data.optString(ToyVpnConfig.json.LOCAL_IPv6, ""));
                routes.setText(data.optString(ToyVpnConfig.json.ROUTES, ""));
                dns.setText(data.optString(ToyVpnConfig.json.DNS, ""));
                mtu.setText(data.optString(ToyVpnConfig.json.MTU, ""));

                VpnMode vpnMode = VpnMode
                        .valueOf(data.optString(ToyVpnConfig.json.VPN_MODE, VpnMode.APPLY_TO_ALL.name()));
                selectedAppIds = data.optJSONArray(ToyVpnConfig.json.SELECTED_APPS) != null
                        ? Utils.jsonArrayToList(data.optJSONArray(ToyVpnConfig.json.SELECTED_APPS))
                        : new ArrayList<>();

                setSpinnerSelection(vpnMode);
            } else {
                data = new JSONObject();
                serverName.setText(R.string.default_server_name);
            }

        } catch (JSONException e) {
            Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_LONG).show();
            return;
        }

        findViewById(R.id.save).setOnClickListener(v -> {
            try {
                data.put(ToyVpnConfig.json.SERVER_NAME, serverName.getText().toString());
                data.put(ToyVpnConfig.json.SERVER_ADDRESS, serverAddress.getText().toString());
                data.put(ToyVpnConfig.json.RNDZ_SERVER_ADDRESS, rndzServerAddress.getText().toString());
                data.put(ToyVpnConfig.json.RNDZ_REMOTE_ID, rndzRemoteId.getText().toString());
                data.put(ToyVpnConfig.json.RNDZ_LOCAL_ID, rndzLocalId.getText().toString());
                data.put(ToyVpnConfig.json.CIPHER, cipher.getText().toString());
                data.put(ToyVpnConfig.json.SHARED_SECRET, sharedSecret.getText().toString());
                data.put(ToyVpnConfig.json.LOCAL_IPv4, localIpv4.getText().toString());
                data.put(ToyVpnConfig.json.LOCAL_IPv6, localIpv6.getText().toString());
                data.put(ToyVpnConfig.json.ROUTES, routes.getText().toString());
                data.put(ToyVpnConfig.json.DNS, dns.getText().toString());
                data.put(ToyVpnConfig.json.MTU, mtu.getText().toString());
                VpnMode currentMode = VpnMode.values()[vpnModeSpinner.getSelectedItemPosition()];
                data.put(ToyVpnConfig.json.VPN_MODE, currentMode.name());
                data.put(ToyVpnConfig.json.SELECTED_APPS, new JSONArray(selectedAppIds));

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
        });

    }

    private void setupActivityResultLauncher() {
        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // Handle the result
                    }
                });
    }

    private void setupVpnModeSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(ServerInfoActivity.this, android.R.layout.simple_spinner_item,
                new String[] { "Apply to all apps", "Only allow selected apps", "Only disallow selected apps" });
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vpnModeSpinner.setAdapter(adapter);
        vpnModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSelectAppsButtonVisibility(VpnMode.values()[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    private void setupSelectAppsButton() {
        selectAppsButton.setOnClickListener(v -> showAppSelectionDialog());
    }

    private void updateSelectAppsButtonVisibility(VpnMode mode) {
        selectAppsButton.setVisibility(mode != VpnMode.APPLY_TO_ALL ? View.VISIBLE : View.GONE);
    }

    private void showAppSelectionDialog() {
        PackageManager pm = getPackageManager();
        List<PackageInfo> packages;

        // Remove the version check and always use the new method
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        packages = pm.queryIntentActivities(mainIntent, PackageManager.GET_META_DATA)
                .stream()
                .map(resolveInfo -> {
                    try {
                        return pm.getPackageInfo(resolveInfo.activityInfo.packageName,
                                PackageManager.GET_META_DATA);
                    } catch (PackageManager.NameNotFoundException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<PackageInfo> installedApps = packages.stream()
                .filter(pkg -> pm.getLaunchIntentForPackage(pkg.packageName) != null)
                .collect(Collectors.toList());

        appIdToNameMap.clear();
        appNameToIdMap.clear();
        List<String> appNames = new ArrayList<>();

        for (PackageInfo pkg : installedApps) {
            String appName = pm.getApplicationLabel(pkg.applicationInfo).toString();
            String appId = pkg.packageName;
            appNames.add(appName);
            appIdToNameMap.put(appId, appName);
            appNameToIdMap.put(appName, appId);
        }

        // remove selectedAppIds which are not in appIdToNameMap
        selectedAppIds.removeIf(appId -> !appIdToNameMap.containsKey(appId));

        boolean[] checkedItems = new boolean[appNames.size()];
        for (int i = 0; i < appNames.size(); i++) {
            checkedItems[i] = selectedAppIds.contains(appNameToIdMap.get(appNames.get(i)));
        }

        new AlertDialog.Builder(ServerInfoActivity.this)
                .setTitle("Select Apps")
                .setMultiChoiceItems(appNames.toArray(new String[0]), checkedItems, (dialog, which, isChecked) -> {
                    String appId = appNameToIdMap.get(appNames.get(which));
                    if (isChecked) {
                        selectedAppIds.add(appId);
                    } else {
                        selectedAppIds.remove(appId);
                    }
                })
                .setPositiveButton("OK", (dialog, which) -> updateSpinnerText())
                .show();
    }

    private void setSpinnerSelection(VpnMode mode) {
        vpnModeSpinner.setSelection(mode.ordinal());
        updateSelectAppsButtonVisibility(mode);

        if (mode != VpnMode.APPLY_TO_ALL) {
            updateSpinnerText();
        }
    }

    private void updateSpinnerText() {
        updateSpinnerText(null);
    }

    private void updateSpinnerText(String savedVpnMode) {
        if (vpnModeSpinner == null || vpnModeSpinner.getSelectedItem() == null) {
            return;
        }

        VpnMode currentMode = VpnMode.values()[vpnModeSpinner.getSelectedItemPosition()];
        if (currentMode != VpnMode.APPLY_TO_ALL) {
            String newText;
            if (savedVpnMode != null) {
                newText = savedVpnMode;
            } else {
                String baseText = currentMode == VpnMode.ALLOW_SELECTED ? "Only allow selected apps"
                        : "Only disallow selected apps";
                newText = baseText + " (" + selectedAppIds.size() + ")";
            }

            String[] options = new String[3];
            options[0] = "Apply to all apps";
            options[1] = currentMode == VpnMode.ALLOW_SELECTED ? newText : "Only allow selected apps";
            options[2] = currentMode == VpnMode.DISALLOW_SELECTED ? newText : "Only disallow selected apps";

            ArrayAdapter<String> newAdapter = new ArrayAdapter<>(ServerInfoActivity.this,
                    android.R.layout.simple_spinner_item, options);
            newAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            vpnModeSpinner.setAdapter(newAdapter);
            vpnModeSpinner.setSelection(currentMode.ordinal());
        }
    }

}
