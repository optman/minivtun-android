package com.github.optman.minivtun;

import com.example.android.toyvpn.ToyVpnConfig;

import org.json.JSONException;
import org.json.JSONObject;

public class Native {
    static {
        System.loadLibrary("minivtun");
    }

    public Native() {
    }

    public static Client prepare(ToyVpnConfig config) {
        try {
            JSONObject params = new JSONObject();
            params.put("svr_addr", config.server);
            params.put("rndz_svr_addr", config.rndzServer);
            params.put("rndz_remote_id", config.rndzRemoteId);
            params.put("rndz_local_id", config.rndzLocalId);
            params.put("local_ip_v4", config.localIpv4);
            params.put("local_ip_v6", config.localIpv6);
            params.put("secret", config.secret);
            params.put("cipher", config.cipher);

            return prepare(params.toString());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static String Info() {
        return info();
    }

    private static native Client prepare(String params);

    public static native void run(Object vpnService, long context, int tun);

    private static native String info();

    public static native void free(long context);

    public static native void stop(long stop);
}
