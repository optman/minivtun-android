package com.example.android.toyvpn;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Build;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import android.content.BroadcastReceiver;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class ServerListActivity extends AppCompatActivity {

    private static final int REQUEST_SEVER_INFO = 0;
    private static final int REQUEST_CONNECT = 1;
    private static final int REQUEST_EXPORT_PICKFILE = 2;
    private static final int REQUEST_IMPORT_PICKFILE = 3;

    private BroadcastReceiver vpnDisconnectReceiver;

    public interface Config {
        String CONFIG_NAME = "CONFIG";
        String SERVER_LIST = "SEVER_LIST";
    }

    class ServerListAdapter extends ArrayAdapter<JSONObject> {

        ServerListActivity parent;

        public ServerListAdapter(Context context, ArrayList<JSONObject> svrs, ServerListActivity parent) {
            super(context, 0, svrs);
            this.parent = parent;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            JSONObject svr = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.server_item, parent, false);
            }

            TextView serverName = (TextView) convertView.findViewById(R.id.server_name);
            try {
                serverName.setText(svr.getString(ToyVpnConfig.json.SERVER_NAME));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            Switch connSwitch = convertView.findViewById(R.id.conn_switch);
            connSwitch.setOnCheckedChangeListener(null); // Remove previous listener
            connSwitch.setChecked(false); // Reset switch state

            connSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    ServerListAdapter.this.parent.OnCheckedChanged(position, isChecked);
                }
            });

            // Make the entire item clickable
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ServerListAdapter.this.parent.onItemClick(position);
                }
            });

            return convertView;
        }

    }

    ArrayList<JSONObject> serverList;
    ServerListAdapter adapter;
    int currentPosition;

    private ActivityResultLauncher<Intent> connectLauncher;
    private ActivityResultLauncher<Intent> serverInfoLauncher;
    private ActivityResultLauncher<Intent> exportFileLauncher;
    private ActivityResultLauncher<Intent> importFileLauncher;

    private void OnCheckedChanged(int position, boolean isChecked) {
        if (isChecked) {
            currentPosition = position;
            Intent intent = VpnService.prepare(ServerListActivity.this);
            if (intent != null) {
                connectLauncher.launch(intent);
            } else {
                connectVpn();
            }
        } else {
            disconnectVpn();
        }
    }

    private void updateServerList(JSONArray json) throws JSONException {
        for (int i = 0; i < json.length(); i++) {
            serverList.add(json.getJSONObject(i));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.server_list);

        serverList = new ArrayList<>();

        final SharedPreferences prefs = getSharedPreferences(Config.CONFIG_NAME, MODE_PRIVATE);
        try {
            JSONArray json = new JSONArray(prefs.getString(Config.SERVER_LIST, "[]"));
            updateServerList(json);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        adapter = new ServerListAdapter(getApplicationContext(), serverList, this);

        ListView lv = (ListView) findViewById(R.id.serverListView);
        lv.setAdapter(adapter);
        lv.setDividerHeight(1); // Add a small divider between items

        // Remove the previous setOnItemClickListener
        // lv.setOnItemClickListener(...);

        findViewById(R.id.status).setOnClickListener(v -> {
            Intent intent = new Intent(ServerListActivity.this, StatusActivity.class);
            startActivity(intent);
        });
        FloatingActionButton addButton = findViewById(R.id.add);
        addButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.colorAccent));
        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(ServerListActivity.this, ServerInfoActivity.class);
            intent.putExtra(ServerInfoActivity.PARAM_POSITION, -1);

            serverInfoLauncher.launch(intent);
        });

        // Register broadcast receiver
        vpnDisconnectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ToyVpnConnection.ACTION_VPN_DISCONNECTED.equals(intent.getAction())) {
                    uncheckCurrentItem();
                }
            }
        };

        IntentFilter filter = new IntentFilter(ToyVpnConnection.ACTION_VPN_DISCONNECTED);
        ContextCompat.registerReceiver(this, vpnDisconnectReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        // Register activity result launchers
        connectLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        connectVpn();
                    }
                });

        serverInfoLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        handleServerInfoResult(result.getData());
                    }
                });

        exportFileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        handleExportResult(result.getData());
                    }
                });

        importFileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        handleImportResult(result.getData());
                    }
                });
    }

    // Add this method to handle item clicks
    private void onItemClick(int position) {
        JSONObject svr = adapter.getItem(position);
        Intent intent = new Intent(this, ServerInfoActivity.class);
        intent.putExtra(ServerInfoActivity.PARAM_POSITION, position);
        intent.putExtra(ServerInfoActivity.PARAM_DATA, svr.toString());

        serverInfoLauncher.launch(intent);
    }

    // Replace onActivityResult with separate methods
    private void handleServerInfoResult(Intent data) {
        int result = data.getIntExtra(ServerInfoActivity.RESULT_CODE, ServerInfoActivity.RESULT_NONE);
        int position = data.getIntExtra(ServerInfoActivity.PARAM_POSITION, -1);
        boolean save = false;
        switch (result) {
            case ServerInfoActivity.RESULT_SAVE:
                // add or update.
                try {
                    JSONObject svr = new JSONObject(data.getStringExtra(ServerInfoActivity.PARAM_DATA));
                    if (position >= 0) {
                        this.serverList.set(position, svr);
                    } else {
                        this.serverList.add(svr);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }
                adapter.notifyDataSetChanged();
                save = true;
                break;
            case ServerInfoActivity.RESULT_DELETE:
                this.serverList.remove(position);
                save = true;
                break;
            default:
                break;
        }
        if (save) {
            savePrefs();
        }
    }

    private void handleExportResult(Intent data) {
        JSONArray j = new JSONArray();
        for (int i = 0; i < serverList.size(); i++) {
            j.put(serverList.get(i));
        }
        try {
            Uri dest = data.getData();
            OutputStream fos = this.getContentResolver().openOutputStream(dest);
            fos.write(j.toString(4).getBytes());
            fos.close();

            Toast.makeText(this, "saved!", Toast.LENGTH_SHORT).show();

        } catch (JSONException | IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleImportResult(Intent data) {
        try {
            Uri dest = data.getData();
            InputStream in = this.getContentResolver().openInputStream(dest);
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] buf = new byte[4096];
                while ((nRead = in.read(buf)) != -1) {
                    buffer.write(buf, 0, nRead);
                }
                JSONArray json = new JSONArray(buffer.toString());
                updateServerList(json);
                adapter.notifyDataSetChanged();
                savePrefs();
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // Log the exception
                    }
                }
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }

    private Intent getServiceIntent() {
        return new Intent(this, ToyVpnService.class);
    }

    private void connectVpn() {
        Intent intent = getServiceIntent();
        intent.putExtra(ToyVpnService.PARAM_VPN_CONFIG, serverList.get(currentPosition).toString());
        startService(intent.setAction(ToyVpnService.ACTION_CONNECT));
    }

    private void disconnectVpn() {
        startService(getServiceIntent().setAction(ToyVpnService.ACTION_DISCONNECT));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.export_list) {
            exportServerList();
            return true;
        } else if (itemId == R.id.import_list) {
            importServerList();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportServerList() {
        Intent chooseFile = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType("text/json");
        exportFileLauncher.launch(chooseFile);
    }

    private void importServerList() {
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType("text/json");
        importFileLauncher.launch(chooseFile);
    }

    private void savePrefs() {
        final SharedPreferences prefs = getSharedPreferences(Config.CONFIG_NAME, MODE_PRIVATE);
        prefs.edit().putString(Config.SERVER_LIST, new JSONArray(this.serverList).toString()).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister broadcast receiver
        if (vpnDisconnectReceiver != null) {
            unregisterReceiver(vpnDisconnectReceiver);
        }
    }

    private void uncheckCurrentItem() {
        runOnUiThread(() -> {
            ListView lv = findViewById(R.id.serverListView);
            View view = lv.getChildAt(currentPosition - lv.getFirstVisiblePosition());
            if (view != null) {
                Switch connSwitch = view.findViewById(R.id.conn_switch);
                if (connSwitch != null) {
                    connSwitch.setChecked(false);
                }
            }
        });
    }

}
