package com.dataiku.dss.intellij;

import java.io.IOException;
import java.util.List;

import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.dataiku.dss.model.metadata.DssPluginFileMetadata;
import com.dataiku.dss.model.metadata.DssRecipeMetadata;

public class SynchronizerUtils {

    public static void savePluginFileToDss(DssSettings dssSettings, MonitoredPlugin monitoredPlugin, String path, byte[] fileContent) throws IOException {
        DSSClient dssClient = dssSettings.getDssClient(monitoredPlugin.plugin.instance);
        savePluginFileToDss(dssClient, monitoredPlugin, path, fileContent);
    }

    public static void saveRecipeToDss(DssSettings dssSettings, MonitoredRecipeFile monitoredFile, String fileContent) throws IOException {
        DSSClient dssClient = dssSettings.getDssClient(monitoredFile.recipe.instance);
        saveRecipeToDss(dssClient, monitoredFile, fileContent, true);
    }

    public static void saveRecipeToDss(DssServer dssInstance, MonitoredRecipeFile monitoredFile, String fileContent) throws IOException {
        saveRecipeToDss(dssInstance.createClient(), monitoredFile, fileContent, true);
    }

    public static void savePluginFileToDss(DSSClient dssClient, MonitoredPlugin monitoredPlugin, String path, byte[] fileContent) throws IOException {
        DssPluginFileMetadata file = findFile(monitoredPlugin.plugin.files, path);
        dssClient.uploadPluginFile(monitoredPlugin.plugin.pluginId, path, fileContent);
        if (file == null) {
            file = new DssPluginFileMetadata();
            file.pluginId = monitoredPlugin.plugin.pluginId;
            file.instance = monitoredPlugin.plugin.instance;
            file.path = monitoredPlugin.plugin.pluginId + "/" + path;
            file.remotePath = path;
            monitoredPlugin.plugin.files.add(file);
        }
        file.contentHash = VirtualFileUtils.getContentHash(fileContent);
        monitoredPlugin.metadataFile.flush();
    }

    public static void saveRecipeToDss(DSSClient dssClient, MonitoredRecipeFile monitoredFile, String fileContent, boolean flushMetadata) throws IOException {
        // File has been updated locally, it needs to be sent to DSS.
        DssRecipeMetadata recipe = monitoredFile.recipe;
        RecipeAndPayload existingRecipe = dssClient.loadRecipe(recipe.projectKey, recipe.recipeName);
        if (existingRecipe != null && !fileContent.equals(existingRecipe.payload)) {
            dssClient.saveRecipeContent(recipe.projectKey, recipe.recipeName, fileContent);
            RecipeAndPayload updatedRecipe = dssClient.loadRecipe(recipe.projectKey, recipe.recipeName);

            // Update metadata & schedule associated metadata file to be updated
            recipe.versionNumber = updatedRecipe.recipe.versionTag.versionNumber;
            recipe.contentHash = VirtualFileUtils.getContentHash(fileContent);

            if (flushMetadata) {
                monitoredFile.metadataFile.flush();
            }
        }
    }

    // TODO Factorize
    private static DssPluginFileMetadata findFile(List<DssPluginFileMetadata> files, String path) {
        for (DssPluginFileMetadata file : files) {
            if (path.equals(file.remotePath)) {
                return file;
            }
        }
        return null;
    }
}
