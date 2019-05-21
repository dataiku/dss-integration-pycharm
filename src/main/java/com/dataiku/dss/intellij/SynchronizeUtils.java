package com.dataiku.dss.intellij;

import static com.google.common.base.Charsets.UTF_8;

import java.io.IOException;

import com.dataiku.dss.intellij.config.DssInstance;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.intellij.utils.VirtualFileManager;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.dataiku.dss.model.metadata.DssPluginFileMetadata;
import com.dataiku.dss.model.metadata.DssRecipeMetadata;
import com.google.common.base.Preconditions;
import com.intellij.openapi.vfs.VirtualFile;

public class SynchronizeUtils {

    public static void savePluginFileToDss(DssSettings dssSettings, MonitoredPlugin monitoredPlugin, String path, byte[] fileContent, boolean flushMetadata) throws IOException {
        DSSClient dssClient = dssSettings.getDssClient(monitoredPlugin.plugin.instance);
        savePluginFileToDss(dssClient, monitoredPlugin, path, fileContent, flushMetadata);
    }

    public static void saveRecipeToDss(DssSettings dssSettings, MonitoredRecipeFile monitoredFile, String fileContent) throws IOException {
        DSSClient dssClient = dssSettings.getDssClient(monitoredFile.recipe.instance);
        saveRecipeToDss(dssClient, monitoredFile, fileContent, true);
    }

    public static void saveRecipeToDss(DssInstance dssInstance, MonitoredRecipeFile monitoredFile, String fileContent) throws IOException {
        saveRecipeToDss(dssInstance.createClient(), monitoredFile, fileContent, true);
    }

    public static void savePluginFileToDss(DSSClient dssClient, MonitoredPlugin monitoredPlugin, String path, byte[] fileContent, boolean flushMetadata) throws IOException {
        dssClient.uploadPluginFile(monitoredPlugin.plugin.pluginId, path, fileContent);
        DssPluginFileMetadata pluginFileMetadata = new DssPluginFileMetadata(
                monitoredPlugin.plugin.instance,
                monitoredPlugin.plugin.pluginId,
                monitoredPlugin.plugin.pluginId + "/" + path,
                path,
                VirtualFileManager.getContentHash(fileContent),
                fileContent);
        monitoredPlugin.metadataFile.addOrUpdatePluginFile(pluginFileMetadata, flushMetadata);
    }

    public static void saveRecipeToDss(DSSClient dssClient, MonitoredRecipeFile monitoredFile, String fileContent, boolean flushMetadata) throws IOException {
        DssRecipeMetadata recipe = monitoredFile.recipe;
        RecipeAndPayload remoteRecipe = dssClient.loadRecipe(recipe.projectKey, recipe.recipeName);
        if (remoteRecipe != null) {
            saveRecipeToDss(dssClient, monitoredFile, fileContent, flushMetadata, remoteRecipe);
        }
    }

    public static void saveRecipeToDss(DSSClient dssClient, MonitoredRecipeFile monitoredFile, String fileContent, boolean flushMetadata, RecipeAndPayload remoteRecipe) throws IOException {
        Preconditions.checkNotNull(remoteRecipe);
        if (fileContent.equals(remoteRecipe.payload)) {
            return;
        }

        // File has been updated locally, it needs to be sent to DSS.
        DssRecipeMetadata recipe = monitoredFile.recipe;
        dssClient.saveRecipeContent(recipe.projectKey, recipe.recipeName, fileContent);
        RecipeAndPayload updatedRecipe = dssClient.loadRecipe(recipe.projectKey, recipe.recipeName);

        // Update metadata & schedule associated metadata file to be updated
        recipe.versionNumber = updatedRecipe.recipe.versionTag.versionNumber;
        recipe.contentHash = VirtualFileManager.getContentHash(fileContent);
        recipe.data = fileContent.getBytes(UTF_8);

        if (flushMetadata) {
            monitoredFile.metadataFile.flush();
        }
    }

    public static void renameRecipeFile(MonitoredRecipeFile monitoredFile, VirtualFile newFile, boolean flushMetadata) throws IOException {
        String oldName = monitoredFile.file.getName();
        String newName = newFile.getName();

        // Update metadata & schedule associated metadata file to be updated
        monitoredFile.file = newFile;
        monitoredFile.recipe.path = monitoredFile.recipe.path.substring(0, oldName.length()) + newName;

        if (flushMetadata) {
            monitoredFile.metadataFile.flush();
        }
    }
}
