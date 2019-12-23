package com.dataiku.dss.model.http;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.jetbrains.annotations.Nullable;

public class ProxyConfiguration {
    public static final ProxyConfiguration NO_PROXY = new ProxyConfiguration(null, 0, false, null, null);

    public final String host;
    public final int port;
    public final boolean sockProxy;
    public final String username;
    public final String password;

    public ProxyConfiguration(String host, int port, boolean sockProxy, @Nullable String username, @Nullable String password) {
        this.host = host;
        this.port = port;
        this.sockProxy = sockProxy;
        this.username = username;
        this.password = password;
    }

    public boolean isEnabled() {
        return host != null && port != 0;
    }

    public boolean hasAuthentication() {
        return username != null;
    }

    public boolean isSock() {
        return sockProxy;
    }

}
