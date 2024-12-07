package com.github.optman.minivtun;

public class Client {
    public long context;
    public long stop;

    // Add this constructor
    public Client(long context, long stop) {
        this.context = context;
        this.stop = stop;
    }

    public void free() {
        if (stop != 0) {
            Native.stop(stop);
            stop = 0;
        }
        if (context != 0) {
            Native.free(context);
            context = 0;
        }
    }

    public void run(Object vpnService, int tun) {
        if (context == 0) {
            throw new RuntimeException("context is not prepared");
        }
        Native.run(vpnService, context, tun);
        context = 0;

    }

    public void stop() {
        if (stop != 0) {
            Native.stop(stop);
            stop = 0;
        }
    }
}