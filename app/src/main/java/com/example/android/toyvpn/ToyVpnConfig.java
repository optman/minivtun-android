package com.example.android.toyvpn;

import org.json.JSONException;
import org.json.JSONObject;

public class ToyVpnConfig {

    public String name;
    public String server;
    public String rndzServer;
    public String rndzRemoteId;
    public String rndzLocalId;
    public String cipher;
    public String secret;
    public String localIpv4;
    public String localIpv6;
    public String routes;
    public String dns;

    public static interface json {
        String SERVER_NAME = "server_name";
        String SERVER_ADDRESS = "server_address";
        String RNDZ_SERVER_ADDRESS = "rndz_server_address";
        String RNDZ_REMOTE_ID = "rndz_remote_id";
        String RNDZ_LOCAL_ID = "rndz_local_id";
        String CIPHER = "cipher";
        String SHARED_SECRET = "shared_secret";
        String LOCAL_IPv4 = "local_ipv4";
        String LOCAL_IPv6 = "local_ipv6";
        String ROUTES = "routes";
        String DNS = "dns";
    }

    public ToyVpnConfig(JSONObject svr) {
        try {
            name = svr.getString(json.SERVER_NAME);
            server = svr.getString(json.SERVER_ADDRESS);
            rndzServer = svr.getString(json.RNDZ_SERVER_ADDRESS);
            rndzRemoteId = svr.getString(json.RNDZ_REMOTE_ID);
            rndzLocalId = svr.getString(json.RNDZ_LOCAL_ID);
            cipher = svr.getString(json.CIPHER);
            secret = svr.getString(json.SHARED_SECRET);
            localIpv4 = svr.getString(json.LOCAL_IPv4);
            localIpv6 = svr.getString(json.LOCAL_IPv6);
            routes = svr.getString(json.ROUTES);
            dns = svr.getString(json.DNS);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


}