package com.github.optman.minivtun;

public class Client {
    public long config;
    public int socket;
    public long stop;

    // Add this constructor
    public Client(long config, int socket, long stop) {
        this.config = config;
        this.socket = socket;
        this.stop = stop;
    }

    public void free() {
        if (config != 0) {
            Native.freeConfig(config);
            config = 0;
        }
        if (stop != 0) {
            Native.stop(stop);
            stop = 0;
        }
    }

    public void run(int tun) {
        if (config == 0) {
            throw new RuntimeException("config is not prepared");
        }
        Native.run(config, tun);
        config = 0;

    }

    public void stop() {
        if (stop != 0) {
            Native.stop(stop);
            stop = 0;
        }
    }
}