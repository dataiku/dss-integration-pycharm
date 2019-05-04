package com.dataiku.dss.intellij.actions.checkout;

import static com.dataiku.dss.intellij.VirtualFileUtils.getContentHash;
import static com.dataiku.dss.intellij.VirtualFileUtils.getOrCreateVirtualDirectory;
import static com.dataiku.dss.intellij.VirtualFileUtils.getOrCreateVirtualFile;
import static com.google.common.base.Charsets.UTF_8;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.MetadataFile;
import com.dataiku.dss.intellij.MetadataFilesIndex;
import com.dataiku.dss.intellij.MonitoredFilesIndex;
import com.dataiku.dss.intellij.RecipeUtils;
import com.dataiku.dss.intellij.VirtualFileUtils;
import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.FolderContent;
import com.dataiku.dss.model.dss.Plugin;
import com.dataiku.dss.model.dss.Recipe;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.dataiku.dss.model.metadata.DssPluginFileMetadata;
import com.dataiku.dss.model.metadata.DssPluginMetadata;
import com.dataiku.dss.model.metadata.DssRecipeMetadata;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;

public class CheckoutWorker {
    private static final Logger log = Logger.getInstance(CheckoutWorker.class);

    private final CheckoutModel model;

    CheckoutWorker(CheckoutModel model) {
        Preconditions.checkNotNull(model, "model");
        this.model = model;
    }

    public List<VirtualFile> checkout() throws IOException {
        if (model.itemType == CheckoutModel.ItemType.RECIPE) {
            return checkoutRecipe(model);
        } else {
            return checkoutPlugin(model);
        }
    }

    private List<VirtualFile> checkoutRecipe(CheckoutModel model) throws IOException {
        // Retrieve project key
        String projectKey = model.projectKey;

        // Retrieve recipe & its payload
        DssServer dssServer = model.server;

        String[] checkoutLocation = model.checkoutLocation.isEmpty() ? new String[0] : model.checkoutLocation.split("/");
        List<VirtualFile> createdFileList = new ArrayList<>();
        List<Recipe> recipes = model.recipes;
        for (Recipe recipe : recipes) {
            RecipeAndPayload recipeAndPayload = model.serverClient.loadRecipe(projectKey, recipe.name);
            String recipeContent = recipeAndPayload.payload;
            if (recipeContent == null) {
                recipeContent = "";
            }

            // Write recipe file
            VirtualFile moduleRootFolder = getModuleRootFolder(ModuleRootManager.getInstance(model.module));
            String serverName = model.server.name;
            String filename = getFilename(recipe);
            Object requestor = this;
            String[] path = appendToArray(checkoutLocation, filename);
            VirtualFile file = getOrCreateVirtualFile(requestor, moduleRootFolder, path);
            VirtualFileUtils.writeToVirtualFile(file, recipeContent);

            // Write metadata
            MetadataFile metadata = MetadataFilesIndex.getInstance().getOrCreateMetadata(moduleRootFolder);
            DssRecipeMetadata recipeMetadata = new DssRecipeMetadata();
            recipeMetadata.path = Joiner.on("/").join(path);
            recipeMetadata.versionNumber = recipeAndPayload.recipe.versionTag.versionNumber;
            recipeMetadata.contentHash = getContentHash(recipeContent);
            recipeMetadata.projectKey = projectKey;
            recipeMetadata.recipeName = recipe.name;
            recipeMetadata.instance = serverName;
            metadata.addOrUpdateRecipe(recipeMetadata);

            // Monitor the file so that if the underlying recipe is edited on DSS side, the file is updated and vice-versa.
            MonitoredFilesIndex.getInstance().index(file, metadata, recipeMetadata);

            // Create the associated run configuration
            new RunConfigurationGenerator().createScriptRunConfiguration(model.module, file, dssServer, model.projectKey, recipe.name);
            createdFileList.add(file);
        }

        NonProjectFileWritingAccessProvider.allowWriting(createdFileList);
        return createdFileList;
    }

    private List<VirtualFile> checkoutPlugin(CheckoutModel model) throws IOException {
        Preconditions.checkNotNull(model, "item");

        // Retrieve recipe & its payload
        DSSClient dssClient = model.serverClient;

        VirtualFile moduleRootFolder = getModuleRootFolder(ModuleRootManager.getInstance(model.module));

        MetadataFile metadata = MetadataFilesIndex.getInstance().getOrCreateMetadata(moduleRootFolder);

        List<VirtualFile> createdFileList = new ArrayList<>();
        List<Plugin> plugins = model.plugins;
        for (Plugin plugin : plugins) {
            // Track plugin
            DssPluginMetadata pluginMetadata = new DssPluginMetadata(model.server.name, plugin.id, plugin.id);

            // Create folder for plugin
            VirtualFile folder = getOrCreateVirtualDirectory(this, moduleRootFolder, plugin.id);

            // Checkout plugin files
            List<FolderContent> folderContents = dssClient.listPluginFiles(plugin.id);
            checkoutFolder(pluginMetadata, plugin.id, createdFileList, folder, folderContents);

            metadata.addOrUpdatePlugin(pluginMetadata);
        }

        NonProjectFileWritingAccessProvider.allowWriting(createdFileList);
        List<VirtualFile> importantFiles = createdFileList.stream().filter(file -> file.getName().equals("plugin.json")).collect(Collectors.toList());
        return importantFiles.isEmpty() ? createdFileList : importantFiles;
    }

    private void checkoutFolder(DssPluginMetadata pluginMetadata, String pluginId, List<VirtualFile> createdFileList, VirtualFile parent, List<FolderContent> folderContents) throws IOException {
        Object requestor = this;
        for (FolderContent pluginFile : folderContents) {
            if (pluginFile.mimeType == null || "null".equals(pluginFile.mimeType)) {
                // Folder
                log.info(String.format("Checkout plugin folder '%s' (path=%s)", pluginFile.name, pluginFile.path));

                // Create folder
                VirtualFile file = getOrCreateVirtualDirectory(requestor, parent, pluginFile.name);

                // Write metadata
                DssPluginFileMetadata pluginFileMetadata = new DssPluginFileMetadata();
                pluginFileMetadata.pluginId = pluginId;
                pluginFileMetadata.instance = model.server.name;
                pluginFileMetadata.path = pluginId + "/" + pluginFile.path;
                pluginFileMetadata.remotePath = pluginFile.path;
                pluginFileMetadata.contentHash = 0;
                pluginMetadata.files.add(pluginFileMetadata);

                // Recurse if necessary
                if (pluginFile.children != null && !pluginFile.children.isEmpty()) {
                    checkoutFolder(pluginMetadata, pluginId, createdFileList, file, pluginFile.children);
                }
            } else {
                // Regular file
                log.info(String.format("Checkout plugin file '%s' (path=%s)", pluginFile.name, pluginFile.path));

                VirtualFile file = getOrCreateVirtualFile(requestor, parent, pluginFile.name);

                byte[] fileContent = pluginFile.size == 0 ? new byte[0] : model.serverClient.downloadPluginFile(pluginId, pluginFile.path);
                VirtualFileUtils.writeToVirtualFile(file, fileContent, UTF_8);

                // Write metadata
                DssPluginFileMetadata pluginFileMetadata = new DssPluginFileMetadata();
                pluginFileMetadata.pluginId = pluginId;
                pluginFileMetadata.instance = model.server.name;
                pluginFileMetadata.path = pluginId + "/" + pluginFile.path;
                pluginFileMetadata.remotePath = pluginFile.path;
                pluginFileMetadata.contentHash = getContentHash(fileContent);
                pluginMetadata.files.add(pluginFileMetadata);

                createdFileList.add(file);
            }
        }
    }

    private static String getFilename(Recipe recipe) {
        return recipe.name + RecipeUtils.extension(recipe.type);
    }

    @NotNull
    private static VirtualFile getModuleRootFolder(ModuleRootManager module) {
        VirtualFile[] contentRoots = module.getContentRoots();
        VirtualFile contentRoot = contentRoots.length == 0 ? null : contentRoots[0];
        if (contentRoot == null) {
            throw new IllegalStateException(String.format("No source root directory defined for module %s. Update its configuration to fix the problem.", module.getModule().getName()));
        }
        return contentRoot;
    }

    @NotNull
    private static String[] appendToArray(String[] array, String item) {
        String[] result = new String[array.length + 1];
        System.arraycopy(array, 0, result, 0, array.length);
        result[array.length] = item;
        return result;
    }
}
