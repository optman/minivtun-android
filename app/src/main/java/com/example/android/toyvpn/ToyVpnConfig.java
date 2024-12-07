package com.example.android.toyvpn;

import java.util.ArrayList;
import java.util.List;

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
    public String mtu;
    public VpnMode vpnMode;
    public List<String> selectedApps;

    public interface json {
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
        String MTU = "mtu";
        String VPN_MODE = "vpn_mode";
        String SELECTED_APPS = "selected_apps";
    }

    public ToyVpnConfig(JSONObject svr) {
        try {
            name = svr.optString(json.SERVER_NAME, "");
            server = svr.optString(json.SERVER_ADDRESS, "");
            rndzServer = svr.optString(json.RNDZ_SERVER_ADDRESS, "");
            rndzRemoteId = svr.optString(json.RNDZ_REMOTE_ID, "");
            rndzLocalId = svr.optString(json.RNDZ_LOCAL_ID, "");
            cipher = svr.optString(json.CIPHER, "");
            secret = svr.optString(json.SHARED_SECRET, "");
            localIpv4 = svr.optString(json.LOCAL_IPv4, "");
            localIpv6 = svr.optString(json.LOCAL_IPv6, "");
            routes = svr.optString(json.ROUTES, "");
            dns = svr.optString(json.DNS, "");
            mtu = svr.optString(json.MTU, "");

            vpnMode = VpnMode.valueOf(svr.optString(json.VPN_MODE, VpnMode.APPLY_TO_ALL.name()));
            selectedApps = svr.optJSONArray(json.SELECTED_APPS) != null
                    ? Utils.jsonArrayToList(svr.optJSONArray(json.SELECTED_APPS))
                    : new ArrayList<>();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
