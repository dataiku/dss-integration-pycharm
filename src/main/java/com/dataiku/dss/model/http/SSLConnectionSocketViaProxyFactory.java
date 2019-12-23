package com.dataiku.dss.model.http;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;
import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.Logger;

public class SSLConnectionSocketViaProxyFactory extends SSLConnectionSocketFactory {

    private static final Logger log = Logger.getInstance(SSLConnectionSocketViaProxyFactory.class);

    public SSLConnectionSocketViaProxyFactory(SSLContext sslContext, @Nullable HostnameVerifier hostnameVerifier) {
        super(checkNotNull(sslContext, "sslContext"), hostnameVerifier);
    }

    @Override
    public Socket createSocket(HttpContext context) {
        InetSocketAddress socketAddress = (InetSocketAddress) context.getAttribute("socks.address");
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, socketAddress);
        log.info("Creating HTTPS socket configured with proxy");
        return new Socket(proxy);
    }
}
