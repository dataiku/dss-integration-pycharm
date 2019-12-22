package com.dataiku.dss.model;

import static com.google.common.base.Charsets.ISO_8859_1;
import static com.google.common.base.Charsets.UTF_8;
import static java.util.Arrays.asList;
import static org.apache.commons.codec.binary.Base64.encodeBase64;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.Logger;
import com.dataiku.dss.model.dss.DssException;
import com.dataiku.dss.model.dss.FolderContent;
import com.dataiku.dss.model.dss.Plugin;
import com.dataiku.dss.model.dss.Project;
import com.dataiku.dss.model.dss.Recipe;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.net.HttpConfigurable;

@SuppressWarnings("UnstableApiUsage")
public class DSSClient {
    private static final String PUBLIC_API = "public/api";
    private static final String PROJECTS = "projects";
    private static final String RECIPES = "recipes";
    private static final String PLUGINS = "plugins";
    private static final String CONTENTS = "contents";

    private static final Logger log = Logger.getInstance(DSSClient.class);

    private final String baseUrl;
    private final String apiKey;
    private final boolean noCheckCertificate;

    public DSSClient(String baseUrl, String apiKey, boolean noCheckCertificate) {
        this.baseUrl = fixBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.noCheckCertificate = noCheckCertificate;
    }

    public void checkConnection() throws DssException {
        try {
            listProjects();
        } catch (DssException e) {
            log.error("Unable to connect to DSS", e);
            throw e;
        }
    }

    public String getDssVersion() {
        try (CloseableHttpClient client = createHttpClient()) {
            URI url = buildUrl(PROJECTS, "");
            HttpResponse response = executeRequest(new HttpGet(url), client);
            Header header = response.getFirstHeader("DSS-Version");
            if (header != null) {
                return header.getValue();
            }
        } catch (IOException | GeneralSecurityException e) {
            return null;
        }
        return null;
    }

    public List<Project> listProjects(String... tags) throws DssException {
        String tagPart = Joiner.on(',').join(tags);
        URI url = buildUrl(PROJECTS, tagPart);

        return asList(executeGet(url, Project[].class));
    }

    public List<Recipe> listRecipes(String projectKey) throws DssException {
        URI url = buildUrl(PROJECTS, projectKey, RECIPES, "");
        return asList(executeGet(url, Recipe[].class));
    }

    public RecipeAndPayload loadRecipe(String projectKey, String recipeName) throws DssException {
        URI url = buildUrl(PROJECTS, projectKey, RECIPES, recipeName);
        return executeGet(url, RecipeAndPayload.class);
    }

    public void saveRecipeContent(String projectKey, String recipeName, String payload) throws DssException {
        URI url = buildUrl(PROJECTS, projectKey, RECIPES, recipeName);

        // Load existing recipe & change only the payload (this way we are compatible with all versions of DSS).
        String existingRecipeJSon = executeGet(url);
        JsonParser parser = new JsonParser();
        JsonObject existingRecipe = parser.parse(existingRecipeJSon).getAsJsonObject();
        JsonPrimitive existingPayload = existingRecipe.getAsJsonPrimitive("payload");

        // If the new payload is different from the existing payload, upload the new payload
        if (existingPayload == null || !Objects.equals(existingPayload.getAsString(), payload)) {
            existingRecipe.remove("payload");
            existingRecipe.add("payload", new JsonPrimitive(payload));

            String body = new GsonBuilder().create().toJson(existingRecipe);
            executePut(url, body);
        }
    }

    public List<Plugin> listPlugins() throws DssException {
        URI url = buildUrl(PLUGINS, "");

        try {
            return asList(executeGet(url, Plugin[].class));
        } catch (IOException e) {
            throw new DssException(e);
        }
    }

    public List<Plugin> listPluginsInDevelopment() throws DssException {
        return listPlugins().stream().filter(plugin -> plugin.isDev).collect(Collectors.toList());
    }

    public List<FolderContent> listPluginFiles(String pluginId) throws DssException {
        URI url = buildUrl(PLUGINS, pluginId, CONTENTS, "");
        return asList(executeGet(url, FolderContent[].class));
    }

    public byte[] downloadPluginFile(String pluginId, String path) throws DssException {
        URI url = buildUrl(PLUGINS, pluginId, CONTENTS, path);
        return executeGetAndReturnByteArray(url);
    }

    public void deletePluginFile(String pluginId, String path) throws DssException {
        URI url = buildUrl(PLUGINS, pluginId, CONTENTS, path);
        executeDelete(url);
    }

    public void uploadPluginFile(String pluginId, String path, byte[] content) throws DssException {
        URI url = buildUrl(PLUGINS, pluginId, CONTENTS, path);
        try (CloseableHttpClient client = createHttpClient()) {
            HttpPost request = new HttpPost(url);
            request.setEntity(new ByteArrayEntity(content));
            executeRequest(request, client);
        } catch (DssException e) {
            throw e;
        } catch (IOException | GeneralSecurityException e) {
            throw new DssException(e);
        }
    }

    public void createPluginFolder(String pluginId, String path) throws DssException {
        String dummyFilePath = path + "/dummy" + UUID.randomUUID();
        uploadPluginFile(pluginId, dummyFilePath, new byte[0]);
        deletePluginFile(pluginId, dummyFilePath);
    }

    @NotNull
    @SuppressWarnings("UnusedReturnValue")
    private String executePut(URI url, String body) throws DssException {
        return new String(executePutAndReturnByteArray(url, body), UTF_8);
    }

    private byte[] executePutAndReturnByteArray(URI url, String body) throws DssException {
        log.debug("Executing PUT request to " + url);

        try (CloseableHttpClient client = createHttpClient()) {
            HttpPut request = new HttpPut(url);
            request.setEntity(new StringEntity(body));
            HttpResponse response = executeRequest(request, client);
            return ByteStreams.toByteArray(response.getEntity().getContent());
        } catch (DssException e) {
            throw e;
        } catch (IOException | GeneralSecurityException e) {
            throw new DssException(e);
        }
    }

    private <T> T executeGet(URI url, Class<T> clazz) throws DssException {
        String body = executeGet(url);
        try {
            return new GsonBuilder().create().fromJson(body, clazz);
        } catch (RuntimeException e) {
            String errorMsg = "Unable to parse response returned by DSS as " + clazz + ":\n" + body;
            log.warn(errorMsg);
            throw new DssException(errorMsg);
        }
    }

    @NotNull
    private String executeGet(URI url) throws DssException {
        return new String(executeGetAndReturnByteArray(url), UTF_8);
    }

    @NotNull
    private byte[] executeGetAndReturnByteArray(URI url) throws DssException {
        log.debug("Executing GET request to " + url);
        try {
            try (CloseableHttpClient client = createHttpClient()) {
                HttpResponse response = executeRequest(new HttpGet(url), client);
                return ByteStreams.toByteArray(response.getEntity().getContent());
            }
        } catch (DssException e) {
            throw e;
        } catch (IOException | GeneralSecurityException e) {
            throw new DssException(e);
        }
    }

    private void executeDelete(URI url) throws DssException {
        log.debug("Executing DELETE request to " + url);
        try {
            try (CloseableHttpClient client = createHttpClient()) {
                executeRequest(new HttpDelete(url), client);
            }
        } catch (DssException e) {
            throw e;
        } catch (IOException | GeneralSecurityException e) {
            throw new DssException(e);
        }
    }

    private CloseableHttpClient createHttpClient() throws GeneralSecurityException, DssException {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        if (noCheckCertificate) {
            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
            sslContextBuilder.loadTrustMaterial(null, (TrustStrategy) (chain, authType) -> true);
            SSLContext sslContext = sslContextBuilder.build();

            httpClientBuilder.setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext));
            httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
            configureProxy(httpClientBuilder);
        }
        return httpClientBuilder.build();
    }

    private void configureProxy(HttpClientBuilder httpClientBuilder) throws DssException {
        HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
        if (!isHttpProxyEnabledForUrl(httpConfigurable, baseUrl)) {
            return;
        }
        if (httpConfigurable.PROXY_TYPE_IS_SOCKS) {
            throw new DssException("Socks proxy is not supported by DSS plugin");
        }

        HttpHost httpHost = new HttpHost(httpConfigurable.PROXY_HOST, httpConfigurable.PROXY_PORT);
        httpClientBuilder.setProxy(httpHost);

        if (httpConfigurable.PROXY_AUTHENTICATION) {
            // Different ways to fetch login based on runtime version (SLI-95)
            try {
                Object proxyLogin = getProxyLogin(httpConfigurable);
                if (proxyLogin != null) {
                    String proxyUsername = proxyLogin.toString();
                    String proxyPassword = httpConfigurable.getPlainProxyPassword();

                    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    credentialsProvider.setCredentials(new AuthScope(httpConfigurable.PROXY_HOST, httpConfigurable.PROXY_PORT),
                            new UsernamePasswordCredentials(proxyUsername, proxyPassword));
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                }
            } catch (Exception e) {
                throw new DssException("Could not fetch value for proxy login", e);
            }
        }
    }

    private boolean isProxyEnabled() {
        try {
            return isHttpProxyEnabledForUrl(HttpConfigurable.getInstance(), baseUrl);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @NotNull
    private HttpResponse executeRequest(HttpRequestBase request, HttpClient client) throws DssException {
        addJsonContentTypeHeader(request);
        addAuthorizationHeader(request);
        HttpResponse response;
        try {
            response = client.execute(request);
        } catch (IOException e) {
            throw new DssException(e);
        }
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            throw new DssException(statusCode, "DSS" + (isProxyEnabled() ? " or HTTP proxy" : "") + " returned error code " + statusCode + ".");
        }
        return response;
    }

    private void addJsonContentTypeHeader(HttpRequestBase request) {
        request.addHeader("content-type", "application/json");
    }

    private void addAuthorizationHeader(HttpRequestBase request) {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + new String(encodeBase64((apiKey + ":").getBytes(ISO_8859_1))));
    }

    @NotNull
    private URI buildUrl(String... parts) {
        try {
            URI baseUri = new URI(baseUrl + PUBLIC_API);
            return new URI(
                    baseUri.getScheme(),
                    baseUri.getAuthority(),
                    baseUri.getPath() + '/' + Joiner.on('/').join(parts),
                    null,
                    null);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URL", e);
        }
    }

    private static String fixBaseUrl(String url) {
        String result = url;
        if (!result.endsWith("/")) {
            result += '/';
        }
        return result;
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
