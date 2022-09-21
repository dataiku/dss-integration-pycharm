package com.dataiku.dss.intellij.actions.checkout;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.*;
import com.dataiku.dss.intellij.actions.checkout.CheckoutModel.ItemType;
import com.dataiku.dss.intellij.config.DssInstance;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.intellij.utils.RecipeUtils;
import com.dataiku.dss.intellij.utils.VirtualFileManager;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.FolderContent;
import com.dataiku.dss.model.dss.Plugin;
import com.dataiku.dss.model.dss.Recipe;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.dataiku.dss.model.metadata.*;
import com.dataiku.dss.wt1.WT1;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.dataiku.dss.intellij.utils.LibraryUtils.LIB_BASE_FOLDER;
import static com.dataiku.dss.intellij.utils.VirtualFileManager.getContentHash;
import static com.google.common.base.Charsets.UTF_8;

public class CheckoutWorker {
    private static final Logger log = Logger.getInstance(CheckoutWorker.class);

    private final DssSettings dssSettings;
    private final WT1 wt1;
    private final CheckoutModel model;
    private final VirtualFileManager vFileManager;

    CheckoutWorker(DssSettings dssSettings, DataikuDSSPlugin dssPlugin, WT1 wt1, CheckoutModel model) {
        Preconditions.checkNotNull(model, "model");
        this.dssSettings = dssSettings;
        this.wt1 = wt1;
        this.model = model;
        this.vFileManager = new VirtualFileManager(dssPlugin, false);
    }

    public List<VirtualFile> checkout() throws IOException {
        if (dssSettings.isTrackingEnabled()) {
            ImmutableMap tracked;
            if (model.recipes != null) {
                tracked = ImmutableMap.of("recipes", model.recipes.size());
            } else if (model.plugins != null) {
                tracked = ImmutableMap.of("plugins", model.plugins.size());
            } else {
                tracked = ImmutableMap.of("libraries", 1);
            }

            wt1.track("pycharm-checkout", tracked);
        }

        if (model.itemType == ItemType.RECIPE) {
            return checkoutRecipe(model);
        } else if (model.itemType == CheckoutModel.ItemType.PLUGIN){
            return checkoutPlugin(model);
        } else {
            return checkoutLibrary(model);
        }
    }

    private List<VirtualFile> checkoutRecipe(CheckoutModel model) throws IOException {
        // Retrieve project key
        String projectKey = model.projectKey;

        // Retrieve recipe & its payload
        DssInstance dssInstance = model.server;

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
            String instanceId = model.server.id;
            String filename = getFilename(recipe);
            String[] path = appendToArray(checkoutLocation, filename);
            VirtualFile file = vFileManager.getOrCreateVirtualFile(moduleRootFolder, path);
            vFileManager.writeToVirtualFile(file, recipeContent);

            // Write metadata
            MetadataFile metadata = MetadataFilesIndex.getInstance().getOrCreateMetadata(moduleRootFolder);
            DssRecipeMetadata recipeMetadata = new DssRecipeMetadata();
            recipeMetadata.instance = instanceId;
            recipeMetadata.projectKey = projectKey;
            recipeMetadata.recipeName = recipe.name;
            recipeMetadata.path = Joiner.on("/").join(path);
            recipeMetadata.versionNumber = recipeAndPayload.recipe.versionTag.versionNumber;
            recipeMetadata.contentHash = getContentHash(recipeContent);
            recipeMetadata.data = recipeContent.getBytes(UTF_8);
            metadata.addOrUpdateRecipe(recipeMetadata);

            // Monitor the file so that if the underlying recipe is edited on DSS side, the file is updated and vice-versa.
            MonitoredFilesIndex.getInstance().index(file, metadata, recipeMetadata);

            // Create the associated run configuration
            if (model.generateRunConfigurations) {
                new RunConfigurationGenerator().createScriptRunConfiguration(model.module, file, dssInstance, model.projectKey, recipe.name);
            }
            createdFileList.add(file);
        }

        NonProjectFileWritingAccessProvider.allowWriting(createdFileList);
        return createdFileList;
    }


    private List<VirtualFile> checkoutLibrary(CheckoutModel model) throws IOException {
        // Retrieve project key
        String projectKey = model.libraryProjectKey;

        DSSClient dssClient = model.serverClient;
        VirtualFile moduleRootFolder = getModuleRootFolder(ModuleRootManager.getInstance(model.module));
        MetadataFile metadata = MetadataFilesIndex.getInstance().getOrCreateMetadata(moduleRootFolder);
        List<VirtualFile> createdFileList = new ArrayList<>();

        // Track library
        DssLibraryMetadata libraryMetadata = new DssLibraryMetadata(model.server.id, projectKey, projectKey + "/" + LIB_BASE_FOLDER + "/");
        // Create folder for library
        VirtualFile folder = vFileManager.getOrCreateVirtualDirectory(vFileManager.getOrCreateVirtualDirectory(moduleRootFolder, projectKey), LIB_BASE_FOLDER);
        // Checkout library files
        List<FolderContent> folderContents = dssClient.listLibraryFiles(projectKey);

        checkoutFolder(libraryMetadata, projectKey, projectKey + "/" + LIB_BASE_FOLDER, createdFileList, folder, folderContents);

        metadata.addOrUpdateLibrary(libraryMetadata);

        // Monitor the library directory so that any further change in this directory is synchronized with DSS.
        MonitoredFilesIndex.getInstance().index(new MonitoredLibrary(folder, metadata, libraryMetadata));

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
            DssPluginMetadata pluginMetadata = new DssPluginMetadata(model.server.id, plugin.id, plugin.id);

            // Create folder for plugin
            VirtualFile folder = vFileManager.getOrCreateVirtualDirectory(moduleRootFolder, plugin.id);

            // Checkout plugin files
            List<FolderContent> folderContents = dssClient.listPluginFiles(plugin.id);
            checkoutFolder(pluginMetadata, plugin.id, plugin.id, createdFileList, folder, folderContents);

            metadata.addOrUpdatePlugin(pluginMetadata);

            // Monitor the plugin directory so that any further change in this directory is synchronized with DSS.
            MonitoredFilesIndex.getInstance().index(new MonitoredPlugin(folder, metadata, pluginMetadata));
        }

        NonProjectFileWritingAccessProvider.allowWriting(createdFileList);
        List<VirtualFile> importantFiles = createdFileList.stream().filter(file -> file.getName().equals("plugin.json")).collect(Collectors.toList());
        return importantFiles.isEmpty() ? createdFileList : importantFiles;
    }


    private void checkoutFolder(DssFileSystemMetadata metadata, String id, String localBaseDir, List<VirtualFile> createdFileList, VirtualFile parent, List<FolderContent> folderContents) throws IOException {
        for (FolderContent remoteFile : folderContents) {
            if (remoteFile.mimeType == null || "null".equals(remoteFile.mimeType)) {
                // Folder
                log.info(String.format("Checkout folder '%s' (path=%s)", remoteFile.name, remoteFile.path));

                // Create folder
                VirtualFile localFile = vFileManager.getOrCreateVirtualDirectory(parent, remoteFile.name);

                // Write metadata
                metadata.files.add(new DssFileMetadata(
                        model.server.id,
                        id,
                        localBaseDir + "/" + remoteFile.path,
                        remoteFile.path,
                        0,
                        (byte[]) null));

                // Recurse if necessary
                if (remoteFile.children != null && !remoteFile.children.isEmpty()) {
                    checkoutFolder(metadata, id, localBaseDir, createdFileList, localFile, remoteFile.children);
                }
            } else {
                // Regular file
                log.info(String.format("Checkout file '%s' (path=%s)", remoteFile.name, remoteFile.path));

                VirtualFile localFile = vFileManager.getOrCreateVirtualFile(parent, remoteFile.name);

                byte[] fileContent;
                if (metadata instanceof DssPluginMetadata) {
                    fileContent = remoteFile.size == 0 ? new byte[0] : model.serverClient.downloadPluginFile(id, remoteFile.path);
                } else {
                    String fileContentString = remoteFile.size == 0 ? "" : model.serverClient.downloadLibraryFile(id, remoteFile.path).data;
                    // Converting back to bytes to factorize code with plugins
                    if (Strings.isNullOrEmpty(fileContentString)) {
                        fileContent = new byte[0];
                    } else {
                        fileContent = fileContentString.getBytes(UTF_8);
                    }
                }

                vFileManager.writeToVirtualFile(localFile, fileContent, UTF_8);
                // Write metadata
                metadata.files.add(new DssFileMetadata(
                        model.server.id,
                        id,
                        localBaseDir + "/" + remoteFile.path,
                        remoteFile.path,
                        getContentHash(fileContent),
                        fileContent));

                createdFileList.add(localFile);
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
