package com.github.optman.minivtun;

import com.example.android.toyvpn.ToyVpnConfig;

public class Native {
    static {
        System.loadLibrary("minivtun");
    }

    public Native(){
    }
    public void Run(int tun, ToyVpnConfig config){
        run(    tun,
                config.server,
                config.rndzServer,
                config.rndzRemoteId,
                config.rndzLocalId,
                config.localIpv4,
                config.localIpv6,
                config.secret,
                config.cipher
                );
    }

    public String Info(){
        return info();
    }


    private static native void run(int tun,
                                   String serverAddr,
                                   String rndzServer,
                                   String rndzRemoteId,
                                   String rndzLocalId,
                                   String localIp4,
                                   String localIp6,
                                   String secret,
                                   String cipher
                                   );

    private static native String info();


}
