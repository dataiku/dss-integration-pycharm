package com.dataiku.dss.model.http;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

import com.dataiku.dss.Logger;
import com.dataiku.dss.model.DSSClient;

public class ConnectionSocketViaProxyFactory extends PlainConnectionSocketFactory {

    private static final Logger log = Logger.getInstance(ConnectionSocketViaProxyFactory.class);

    public ConnectionSocketViaProxyFactory() {
        super();
    }

    @Override
    public Socket createSocket(HttpContext context) {
        InetSocketAddress socketAddress = (InetSocketAddress) context.getAttribute("socks.address");
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, socketAddress);
        log.info("Creating HTTP socket configured with proxy");
        return new Socket(proxy);
    }
}
