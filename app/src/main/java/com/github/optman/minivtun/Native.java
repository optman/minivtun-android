package com.github.optman.minivtun;

import com.example.android.toyvpn.ToyVpnConfig;

public class Native {
    private final ToyVpnConfig mConfig;
    static {
        System.loadLibrary("minivtun");
    }

    public Native(ToyVpnConfig config){
        mConfig = config;
    }
    public void Run(int tun){
        run(    tun,
                mConfig.server,
                mConfig.rndzServer,
                mConfig.rndzRemoteId,
                mConfig.rndzLocalId,
                mConfig.localIpv4,
                mConfig.localIpv6,
                mConfig.secret,
                mConfig.cipher
                );
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


}
