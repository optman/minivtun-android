package com.example.android.toyvpn;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

public class ToyVpnConfig {

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

    public ToyVpnConfig(JSONObject svr) {
        try {
            server = svr.getString(Prefs.SERVER_ADDRESS);
            rndzServer = svr.getString(Prefs.RNDZ_SERVER_ADDRESS);
            rndzRemoteId = svr.getString(Prefs.RNDZ_REMOTE_ID);
            rndzLocalId = svr.getString(Prefs.RNDZ_LOCAL_ID);
            cipher = svr.getString(Prefs.CIPHER);
            secret = svr.getString(Prefs.SHARED_SECRET);
            localIpv4 = svr.getString(Prefs.LOCAL_IPv4);
            localIpv6 = svr.getString(Prefs.LOCAL_IPv6);
            routes = svr.getString(Prefs.ROUTES);
            dns = svr.getString(Prefs.DNS);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}