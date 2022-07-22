package com.dataiku.dss.model;

import com.dataiku.dss.Logger;
import com.dataiku.dss.model.dss.*;
import com.dataiku.dss.model.http.HttpClientWithContext;
import com.dataiku.dss.model.http.HttpClientWithContextBuilder;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.google.common.base.Charsets.ISO_8859_1;
import static com.google.common.base.Charsets.UTF_8;
import static java.util.Arrays.asList;
import static org.apache.commons.codec.binary.Base64.encodeBase64;

@SuppressWarnings("UnstableApiUsage")
public class DSSClient {
    private static final String PUBLIC_API = "public/api";
    private static final String PROJECTS = "projects";
    private static final String RECIPES = "recipes";
    private static final String PLUGINS = "plugins";
    private static final String LIBRARIES = "libraries";
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
        try (HttpClientWithContext client = createHttpClient()) {
            URI url = buildUrl(PROJECTS, "");
            HttpResponse response = executeRequest(new HttpGet(url), client);
            Header header = response.getFirstHeader("DSS-Version");
            return header != null ? header.getValue() : null;
        } catch (IOException e) {
            log.info("Unable to retrieve DSS version", e);
            return null;
        }
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

    public List<FolderContent> listLibraryFiles(String projectKey) throws DssException {
        URI url = buildUrl(PROJECTS, projectKey, LIBRARIES, CONTENTS, "");
        return asList(executeGet(url, FolderContent[].class));
    }

    public FolderContent downloadLibraryFile(String projectKey, String path) throws DssException {
        URI url = buildUrl(PROJECTS, projectKey, LIBRARIES, CONTENTS, path);
        return executeGet(url, FolderContent.class);
    }

    public byte[] downloadPluginFile(String pluginId, String path) throws DssException {
        URI url = buildUrl(PLUGINS, pluginId, CONTENTS, path);
        return executeGetAndReturnByteArray(url);
    }

    public void deletePluginFile(String pluginId, String path) throws DssException {
        URI url = buildUrl(PLUGINS, pluginId, CONTENTS, path);
        executeDelete(url);
    }

    public void deleteLibraryFile(String projectKey, String path) throws DssException {
        URI url = buildUrl(PROJECTS, projectKey, LIBRARIES, CONTENTS, path);
        executeDelete(url);
    }

    public void uploadPluginFile(String pluginId, String path, byte[] content) throws DssException {
        URI url = buildUrl(PLUGINS, pluginId, CONTENTS, path);
        try (HttpClientWithContext client = createHttpClient()) {
            HttpPost request = new HttpPost(url);
            request.setEntity(new ByteArrayEntity(content));
            executeRequest(request, client);
        } catch (DssException e) {
            throw e;
        } catch (IOException e) {
            throw new DssException(e);
        }
    }

    public void uploadLibraryFile(String projectKey, String path, byte[] content) throws DssException {
        URI url = buildUrl(PROJECTS, projectKey, LIBRARIES, CONTENTS, path);
        try (HttpClientWithContext client = createHttpClient()) {
            HttpPost request = new HttpPost(url);
            request.setEntity(new ByteArrayEntity(content));
            executeRequest(request, client);
        } catch (DssException e) {
            throw e;
        } catch (IOException e) {
            throw new DssException(e);
        }
    }

    public void createPluginFolder(String pluginId, String path) throws DssException {
        String dummyFilePath = path + "/dummy" + UUID.randomUUID();
        uploadPluginFile(pluginId, dummyFilePath, new byte[0]);
        deletePluginFile(pluginId, dummyFilePath);
    }

    public void createLibraryFolder(String projectKey, String path) throws DssException {
        String dummyFilePath = path + "/dummy" + UUID.randomUUID();
        uploadLibraryFile(projectKey, dummyFilePath, new byte[0]);
        deleteLibraryFile(projectKey, dummyFilePath);
    }

    private HttpClientWithContext createHttpClient() throws DssException {
        return new HttpClientWithContextBuilder(baseUrl, noCheckCertificate).build();
    }

    @NotNull
    @SuppressWarnings("UnusedReturnValue")
    private String executePut(URI url, String body) throws DssException {
        return new String(executePutAndReturnByteArray(url, body), UTF_8);
    }

    private byte[] executePutAndReturnByteArray(URI url, String body) throws DssException {
        log.debug("Executing PUT request to " + url);

        try (HttpClientWithContext client = createHttpClient()) {
            HttpPut request = new HttpPut(url);
            request.setEntity(new StringEntity(body));
            HttpResponse response = executeRequest(request, client);
            return ByteStreams.toByteArray(response.getEntity().getContent());
        } catch (DssException e) {
            throw e;
        } catch (IOException e) {
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
            try (HttpClientWithContext client = createHttpClient()) {
                HttpResponse response = executeRequest(new HttpGet(url), client);
                return ByteStreams.toByteArray(response.getEntity().getContent());
            }
        } catch (DssException e) {
            throw e;
        } catch (IOException e) {
            throw new DssException(e);
        }
    }

    private void executeDelete(URI url) throws DssException {
        log.debug("Executing DELETE request to " + url);
        try {
            try (HttpClientWithContext client = createHttpClient()) {
                executeRequest(new HttpDelete(url), client);
            }
        } catch (DssException e) {
            throw e;
        } catch (IOException e) {
            throw new DssException(e);
        }
    }

    @NotNull
    private HttpResponse executeRequest(HttpRequestBase request, HttpClientWithContext client) throws DssException {
        addJsonContentTypeHeader(request);
        addAuthorizationHeader(request);
        HttpResponse response;
        try {
            response = client.client.execute(request, client.context);
        } catch (IOException e) {
            throw new DssException(e);
        }
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            throw new DssException(statusCode, "DSS" + (client.useProxy ? " or HTTP proxy" : "") + " returned error code " + statusCode + ".");
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
