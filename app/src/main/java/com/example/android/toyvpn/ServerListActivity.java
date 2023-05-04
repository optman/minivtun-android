package com.example.android.toyvpn;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class ServerListActivity extends AppCompatActivity {

    private final int REQUEST_SEVER_INFO = 0;
    private final int REQUEST_CONNECT = 1;

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
            }else{
                connectVpn();
            }
        } else {
            disconnectVpn();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.server_list);

        serverList = new ArrayList<>();

        final SharedPreferences prefs = getSharedPreferences(Config.CONFIG_NAME, MODE_PRIVATE);
        try {
            JSONArray objs = new JSONArray(prefs.getString(Config.SERVER_LIST, "[]"));
            for (int i = 0; i < objs.length(); i++) {
                serverList.add(objs.getJSONObject(i));
            }
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
                    save = true;
                    break;
                case ServerInfoActivity.RESULT_DELETE:
                    this.serverList.remove(position);
                    save = true;
                    break;
                default:
                    break;
            }
            final SharedPreferences prefs = getSharedPreferences(Config.CONFIG_NAME, MODE_PRIVATE);
            prefs.edit().putString(Config.SERVER_LIST, new JSONArray(this.serverList).toString()).commit();
        } else if (requestCode == REQUEST_CONNECT && resultCode == RESULT_OK) {
            connectVpn();
        }

    }

    private Intent getServiceIntent() {
        return new Intent(this, ToyVpnService.class);
    }

    private void connectVpn(){
        Intent intent = getServiceIntent();
        intent.putExtra(ToyVpnService.PARAM_VPN_CONFIG, serverList.get(currentPosition).toString());
        startService(intent.setAction(ToyVpnService.ACTION_CONNECT));
    }
    private void disconnectVpn() {
        startService(getServiceIntent().setAction(ToyVpnService.ACTION_DISCONNECT));
    }
}



