package com.github.optman.minivtun;

import com.example.android.toyvpn.ToyVpnConfig;

import org.json.JSONException;
import org.json.JSONObject;

public class Native {
    static {
        System.loadLibrary("minivtun");
    }

    public Native(){
    }
    public void Run(int tun, ToyVpnConfig config){

		try{

		JSONObject params = new JSONObject();
		params.put("tun", tun);
		params.put("svr_addr", config.server);
		params.put("rndz_svr_addr", config.rndzServer);
		params.put("rndz_remote_id", config.rndzRemoteId);
		params.put("rndz_local_id", config.rndzLocalId);
		params.put("local_ip_v4", config.localIpv4);
		params.put("local_ip_v6",config.localIpv6);
		params.put("secret", config.secret);
		params.put("cipher", config.cipher);

        run(params.toString());

		}catch(JSONException e){ }

    }

    public String Info(){
        return info();
    }


    private static native void run(String params);

    private static native String info();


}
