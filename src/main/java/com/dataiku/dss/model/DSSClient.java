package com.dataiku.dss.model;

import static com.google.common.base.Charsets.ISO_8859_1;
import static com.google.common.base.Charsets.UTF_8;
import static java.util.Arrays.asList;
import static org.apache.commons.codec.binary.Base64.encodeBase64;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
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
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.model.dss.DssException;
import com.dataiku.dss.model.dss.FolderContent;
import com.dataiku.dss.model.dss.Plugin;
import com.dataiku.dss.model.dss.Project;
import com.dataiku.dss.model.dss.Recipe;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.gson.GsonBuilder;

public class DSSClient {
    private static final String PUBLIC_API = "public/api";
    private static final String PROJECTS = "projects";
    private static final String RECIPES = "recipes";
    private static final String PLUGINS = "plugins";
    private static final String CONTENTS = "contents";

    private final String baseUrl;
    private final String apiKey;
    private final boolean noCheckCertificate;

    public DSSClient(String baseUrl, String apiKey, boolean noCheckCertificate) {
        this.baseUrl = fixBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.noCheckCertificate = noCheckCertificate;
    }

    public boolean canConnect() {
        try {
            listProjects();
            return true;
        } catch (DssException e) {
            return false;
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

    public Recipe getRecipe(String projectKey, String recipeName) throws DssException {
        return listRecipes(projectKey).stream().filter(r -> recipeName.equals(r.name)).findFirst().orElse(null);
    }

    public void saveRecipeContent(String projectKey, String recipeName, String payload) throws DssException {
        Recipe recipe = getRecipe(projectKey, recipeName);
        if (recipe == null) {
            throw new IllegalArgumentException(String.format("No recipe named %s found in project %s.", recipeName, projectKey));
        }
        RecipeAndPayload recipeAndPayload = new RecipeAndPayload();
        recipeAndPayload.recipe = recipe;
        recipeAndPayload.payload = payload;
        String body = new GsonBuilder().setPrettyPrinting().create().toJson(recipeAndPayload);

        URI url = buildUrl(PROJECTS, recipe.projectKey, RECIPES, recipe.name);
        executePut(url, body);
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
        try (CloseableHttpClient client = createHttpClient()) {
            HttpPut request = new HttpPut(url);
            request.setEntity(new StringEntity(body));
            HttpResponse response = executeRequest(request, client);
            return ByteStreams.toByteArray(response.getEntity().getContent());
        } catch (IOException | GeneralSecurityException e) {
            throw new DssException(e);
        }
    }

    private <T> T executeGet(URI url, Class<T> clazz) throws DssException {
        String body = executeGet(url);
        return new GsonBuilder().create().fromJson(body, clazz);
    }

    @NotNull
    private String executeGet(URI url) throws DssException {
        return new String(executeGetAndReturnByteArray(url), UTF_8);
    }

    @NotNull
    private byte[] executeGetAndReturnByteArray(URI url) throws DssException {
        try {
            try (CloseableHttpClient client = createHttpClient()) {
                HttpResponse response = executeRequest(new HttpGet(url), client);
                return ByteStreams.toByteArray(response.getEntity().getContent());
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new DssException(e);
        }
    }

    private void executeDelete(URI url) throws DssException {
        try {
            try (CloseableHttpClient client = createHttpClient()) {
                executeRequest(new HttpDelete(url), client);
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new DssException(e);
        }
    }

    private CloseableHttpClient createHttpClient() throws GeneralSecurityException {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        if (noCheckCertificate) {
            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
            sslContextBuilder.loadTrustMaterial(null, (TrustStrategy) (chain, authType) -> true);
            SSLContext sslContext = sslContextBuilder.build();

            httpClientBuilder.setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext));
            httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        }
        return httpClientBuilder.build();
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
            throw new DssException(statusCode, "DSS returned error code " + statusCode);
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
}