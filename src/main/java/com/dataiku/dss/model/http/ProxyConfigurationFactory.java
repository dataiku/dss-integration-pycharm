package com.dataiku.dss.model.http;

import static com.dataiku.dss.model.http.ProxyConfiguration.NO_PROXY;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.model.dss.DssException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.net.HttpConfigurable;

public class ProxyConfigurationFactory {

    public static ProxyConfiguration getProxyConfiguration(String baseUrl) throws DssException {
        HttpConfigurable httpConfig = HttpConfigurable.getInstance();
        if (!isHttpProxyEnabledForUrl(httpConfig, baseUrl)) {
            return NO_PROXY;
        }

        boolean sockProxy = httpConfig.PROXY_TYPE_IS_SOCKS;
        String proxyHost = httpConfig.PROXY_HOST;
        int proxyPort = httpConfig.PROXY_PORT;
        String proxyUsername = null;
        String proxyPassword = null;
        if (httpConfig.PROXY_AUTHENTICATION) {
            try {
                Object proxyLogin = getProxyLogin(httpConfig);
                if (proxyLogin != null) {
                    proxyUsername = proxyLogin.toString();
                    proxyPassword = httpConfig.getPlainProxyPassword();
                }
            } catch (Exception e) {
                throw new DssException("Could not fetch value for proxy login", e);
            }
        }
        return new ProxyConfiguration(proxyHost, proxyPort, sockProxy, proxyUsername, proxyPassword);
    }

    /**
     * Copy of {@link HttpConfigurable#isHttpProxyEnabledForUrl(String)}, which doesn't exist in IDEA 14.
     */
    private static boolean isHttpProxyEnabledForUrl(HttpConfigurable httpConfigurable, @Nullable String url) {
        if (!httpConfigurable.USE_HTTP_PROXY) {
            return false;
        }
        URI uri = url != null ? VfsUtil.toUri(url) : null;
        return uri == null || !isProxyException(httpConfigurable, uri.getHost());
    }

    private static boolean isProxyException(HttpConfigurable httpConfigurable, @Nullable String uriHost) {
        if (StringUtil.isEmptyOrSpaces(uriHost) || StringUtil.isEmptyOrSpaces(httpConfigurable.PROXY_EXCEPTIONS)) {
            return false;
        }

        List<String> hosts = StringUtil.split(httpConfigurable.PROXY_EXCEPTIONS, ",");
        for (String hostPattern : hosts) {
            String regexpPattern = StringUtil.escapeToRegexp(hostPattern.trim()).replace("\\*", ".*");
            if (Pattern.compile(regexpPattern).matcher(uriHost).matches()) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private static Object getProxyLogin(HttpConfigurable httpConfigurable) throws Exception {
        try {
            Field proxyLoginField = HttpConfigurable.class.getField("PROXY_LOGIN");
            return proxyLoginField.get(httpConfigurable);
        } catch (NoSuchFieldException ex) {
            // field doesn't exist -> we are in version >= 2016.2
            Method proxyLoginMethod = HttpConfigurable.class.getMethod("getProxyLogin");
            return proxyLoginMethod.invoke(httpConfigurable);
        }
    }
}
