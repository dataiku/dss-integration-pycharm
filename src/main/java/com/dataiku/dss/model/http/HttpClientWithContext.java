package com.dataiku.dss.model.http;

import java.io.Closeable;
import java.io.IOException;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;

public class HttpClientWithContext implements Closeable {
    public final CloseableHttpClient client;
    public final HttpContext context;
    public final boolean useProxy;

    public HttpClientWithContext(CloseableHttpClient client, HttpContext context, boolean useProxy) {
        this.client = client;
        this.context = context;
        this.useProxy = useProxy;
    }

    @Override
    public void close() throws IOException {
        this.client.close();
    }
}
