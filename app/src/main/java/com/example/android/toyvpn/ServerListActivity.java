package com.example.android.toyvpn;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class ServerListActivity extends AppCompatActivity {

    private static final int REQUEST_SEVER_INFO = 0;
    private static final int REQUEST_CONNECT = 1;
    private static final int REQUEST_EXPORT_PICKFILE = 2;
    private static final int REQUEST_IMPORT_PICKFILE = 3;

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
                serverName.setText(svr.getString(Prefs.SERVER_NAME));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            ((Switch) convertView.findViewById(R.id.conn_switch)).setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            ServerListAdapter.this.parent.OnCheckedChanged(position, isChecked);
                        }
                    }
            );

            return convertView;
        }

    }

    ArrayList<JSONObject> serverList;
    ServerListAdapter adapter;
    int currentPosition;

    private void OnCheckedChanged(int position, boolean isChecked) {
        if (isChecked) {
            currentPosition = position;
            Intent intent = VpnService.prepare(ServerListActivity.this);
            if (intent != null) {
                startActivityForResult(intent, REQUEST_CONNECT);
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

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                JSONObject svr = adapter.getItem(position);
                Intent intent = new Intent(parent.getContext(), ServerInfoActivity.class);
                intent.putExtra(ServerInfoActivity.PARAM_POSITION, position);
                intent.putExtra(ServerInfoActivity.PARAM_DATA, svr.toString());

                startActivityForResult(intent, REQUEST_SEVER_INFO);
            }
        });
        findViewById(R.id.status).setOnClickListener(v -> {
            Intent intent = new Intent(ServerListActivity.this, StatusActivity.class);
            startActivity(intent);
        });
        findViewById(R.id.add).setOnClickListener(v -> {
            Intent intent = new Intent(ServerListActivity.this, ServerInfoActivity.class);
            intent.putExtra(ServerInfoActivity.PARAM_POSITION, -1);

            startActivityForResult(intent, REQUEST_SEVER_INFO);
        });

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SEVER_INFO && resultCode == RESULT_OK) {
            int result = data.getIntExtra(ServerInfoActivity.RESULT_CODE, ServerInfoActivity.RESULT_NONE);
            int position = data.getIntExtra(ServerInfoActivity.PARAM_POSITION, -1);
            boolean save = false;
            switch (result) {
                case ServerInfoActivity.RESULT_SAVE:
                    //add or update.
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
        } else if (requestCode == REQUEST_CONNECT && resultCode == RESULT_OK) {
            connectVpn();
        } else if (requestCode == REQUEST_IMPORT_PICKFILE && resultCode == RESULT_OK) {
            try {
                Uri dest = data.getData();
                InputStream in = this.getContentResolver().openInputStream(dest);
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
            } catch (JSONException | IOException e) {
                e.printStackTrace();
            }

        } else if (requestCode == REQUEST_EXPORT_PICKFILE && resultCode == RESULT_OK) {

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
        switch (item.getItemId()) {
            case R.id.export_list:
                return export_list();
            case R.id.import_list:
                return import_list();
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private boolean export_list() {
        Intent chooseFile = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType("text/json");
        startActivityForResult(
                Intent.createChooser(chooseFile, "Choose destination file"),
                REQUEST_EXPORT_PICKFILE
        );
        return true;
    }

    private boolean import_list() {
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType("text/json");
        startActivityForResult(
                Intent.createChooser(chooseFile, "Choose source file"),
                REQUEST_IMPORT_PICKFILE
        );

        return true;
    }

    private void savePrefs() {
        final SharedPreferences prefs = getSharedPreferences(Config.CONFIG_NAME, MODE_PRIVATE);
        prefs.edit().putString(Config.SERVER_LIST, new JSONArray(this.serverList).toString()).commit();
    }
}



