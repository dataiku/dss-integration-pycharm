package com.dataiku.dss.model;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.apache.commons.codec.Charsets.UTF_8;
import static org.apache.commons.codec.binary.Base64.encodeBase64;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.model.dss.DssException;
import com.dataiku.dss.model.dss.Project;
import com.dataiku.dss.model.dss.Recipe;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.gson.GsonBuilder;

public class DSSClient {

    private final String baseUrl;
    private final String apiKey;

    public DSSClient(String baseUrl, String apiKey) {
        this.baseUrl = fixBaseUrl(baseUrl);
        this.apiKey = apiKey;
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
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            HttpGet request = new HttpGet(baseUrl + "public/api/projects/");
            HttpResponse response = executeRequest(client, request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new DssException(statusCode, "DSS returned error code " + statusCode);
            }
            Header header = response.getFirstHeader("DSS-Version");
            if (header != null) {
                return header.getValue();
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    public List<Project> listProjects(String... tags) throws DssException {
        String tagPart = Joiner.on(",").join(tags);
        String url = baseUrl + "public/api/" + "projects/" + tagPart;

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            Project[] projects = executeGet(client, url, Project[].class);
            return Arrays.asList(projects);
        } catch (IOException e) {
            throw new DssException(e);
        }
    }

    public List<Recipe> listRecipes(String projectKey) throws DssException {
        String url = baseUrl + "public/api/" + "projects/" + projectKey + "/recipes/";

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            Recipe[] recipes = executeGet(client, url, Recipe[].class);
            return Arrays.asList(recipes);
        } catch (IOException e) {
            throw new DssException(e);
        }
    }

    public RecipeAndPayload loadRecipe(String projectKey, String recipeName) throws DssException {
        String url = baseUrl + "public/api/" + "projects/" + projectKey + "/recipes/" + recipeName;

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            return executeGet(client, url, RecipeAndPayload.class);
        } catch (IOException e) {
            throw new DssException(e);
        }
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

        String url = baseUrl + "public/api/projects/" + recipe.projectKey + "/recipes/" + recipe.name;
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            executePut(client, url, body);
        } catch (IOException e) {
            throw new DssException(e);
        }
    }

    private <T> T executeGet(HttpClient client, String url, Class<T> clazz) throws IOException {
        String body = executeGet(client, url);
        return new GsonBuilder().create().fromJson(body, clazz);
    }

    @NotNull
    private String executeGet(HttpClient client, String url) throws IOException {
        HttpGet request = new HttpGet(url);
        HttpResponse response = executeRequest(client, request);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            throw new DssException(statusCode, "DSS returned error code " + statusCode);
        }

        return new String(ByteStreams.toByteArray(response.getEntity().getContent()), UTF_8);
    }

    private HttpResponse executeRequest(HttpClient client, HttpGet request) throws IOException {
        request.addHeader("content-type", "application/json");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + new String(encodeBase64((apiKey + ":").getBytes(ISO_8859_1))));
        return client.execute(request);
    }

    @NotNull
    private String executePut(HttpClient client, String url, String body) throws IOException {
        HttpPut request = new HttpPut(url);
        request.addHeader("content-type", "application/json");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + new String(encodeBase64((apiKey + ":").getBytes(ISO_8859_1))));
        request.setEntity(new StringEntity(body));
        HttpResponse response = client.execute(request);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            throw new DssException(statusCode, "DSS returned error code " + statusCode);
        }

        return new String(ByteStreams.toByteArray(response.getEntity().getContent()), UTF_8);
    }

    private static String fixBaseUrl(String url) {
        String result = url;
        if (!result.endsWith("/")) {
            result += "/";
        }
        return result;
    }

}
