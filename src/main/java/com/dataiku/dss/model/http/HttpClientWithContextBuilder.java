package com.dataiku.dss.model.http;

import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.SSLInitializationException;

import com.dataiku.dss.model.dss.DssException;

public class HttpClientWithContextBuilder {
    private final String baseUrl;
    private final boolean noCheckCertificate;

    public HttpClientWithContextBuilder(String baseUrl, boolean noCheckCertificate) {
        this.baseUrl = baseUrl;
        this.noCheckCertificate = noCheckCertificate;
    }

    public HttpClientWithContext build() throws DssException {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        HttpClientContext context = null;

        ProxyConfiguration proxyConfig = ProxyConfigurationFactory.getProxyConfiguration(baseUrl);
        boolean useProxy = proxyConfig.isEnabled();
        if (!useProxy) {
            httpClientBuilder.setSSLSocketFactory(new SSLConnectionSocketFactory(buildSSLContext(), buildHostNameVerifier()));
        } else {
            if (proxyConfig.isSock()) {
                if (proxyConfig.hasAuthentication()) {
                    throw new DssException("Socks proxy with authentication is not supported by DSS plugin");
                }
                Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", new ConnectionSocketViaProxyFactory())
                        .register("https", new SSLConnectionSocketViaProxyFactory(buildSSLContext(), buildHostNameVerifier()))
                        .build();
                PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
                httpClientBuilder.setConnectionManager(connectionManager);

                context = HttpClientContext.create();
                context.setAttribute("socks.address", new InetSocketAddress(proxyConfig.host, proxyConfig.port));
            } else {
                httpClientBuilder.setSSLSocketFactory(new SSLConnectionSocketFactory(buildSSLContext(), buildHostNameVerifier()));
                httpClientBuilder.setProxy(new HttpHost(proxyConfig.host, proxyConfig.port));
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider(proxyConfig));
            }
        }
        return new HttpClientWithContext(httpClientBuilder.build(), context, useProxy);
    }

    private CredentialsProvider credentialsProvider(ProxyConfiguration proxyConfig) {
        if (proxyConfig.username != null) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(new AuthScope(proxyConfig.host, proxyConfig.port), new UsernamePasswordCredentials(proxyConfig.username, proxyConfig.password));
            return credentialsProvider;
        } else {
            return null;
        }
    }

    private HostnameVerifier buildHostNameVerifier() {
        return noCheckCertificate ? NoopHostnameVerifier.INSTANCE : null;
    }

    private SSLContext buildSSLContext() {
        if (noCheckCertificate) {
            try {
                SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
                sslContextBuilder.loadTrustMaterial(null, (TrustStrategy) (chain, authType) -> true);
                return sslContextBuilder.build();
            } catch (KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
                throw new SSLInitializationException(e.getMessage(), e);
            }
        } else {
            return SSLContexts.createSystemDefault();
        }
    }
}
