package com.example.android.toyvpn;

import android.content.SharedPreferences;

public class ToyVpnConfig {

        public  String server;
        public  String rndzServer;
        public  String rndzRemoteId;
        public  String rndzLocalId;
        public  String cipher;
        public  String secret;
        public  String localIpv4;
        public  String localIpv6;
        public  String routes;
        public  String dns;

    public ToyVpnConfig(final SharedPreferences prefs){
        // Extract information from the shared preferences.
        server = prefs.getString(ToyVpnClient.Prefs.SERVER_ADDRESS, "");
        rndzServer = prefs.getString(ToyVpnClient.Prefs.RNDZ_SERVER_ADDRESS, "");
        rndzRemoteId = prefs.getString(ToyVpnClient.Prefs.RNDZ_REMOTE_ID, "");
        rndzLocalId = prefs.getString(ToyVpnClient.Prefs.RNDZ_LOCAL_ID, "");
        cipher = prefs.getString(ToyVpnClient.Prefs.CIPHER, "");
        secret = prefs.getString(ToyVpnClient.Prefs.SHARED_SECRET, "");
        localIpv4 = prefs.getString(ToyVpnClient.Prefs.LOCAL_IPv4, "");
        localIpv6 = prefs.getString(ToyVpnClient.Prefs.LOCAL_IPv6, "");
        routes = prefs.getString(ToyVpnClient.Prefs.ROUTES, "");
        dns = prefs.getString(ToyVpnClient.Prefs.DNS, "");
    }
}
