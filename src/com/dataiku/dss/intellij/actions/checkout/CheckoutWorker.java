package com.dataiku.dss.intellij.actions.checkout;

import static com.dataiku.dss.intellij.VirtualFileUtils.getContentHash;
import static com.dataiku.dss.intellij.VirtualFileUtils.getOrCreateVirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.intellij.MetadataFile;
import com.dataiku.dss.intellij.MetadataFilesIndex;
import com.dataiku.dss.intellij.MonitoredFilesIndex;
import com.dataiku.dss.intellij.RecipeUtils;
import com.dataiku.dss.intellij.VirtualFileUtils;
import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.Recipe;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.dataiku.dss.model.metadata.DssRecipeMetadata;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.vfs.VirtualFile;

public class CheckoutWorker {

    public List<VirtualFile> checkout(CheckoutDSSItemModel model) throws IOException {
        Preconditions.checkNotNull(model, "item");

        // Retrieve project key
        String projectKey = model.projectKey;

        // Retrieve recipe & its payload
        DssServer dssServer = model.server;
        DSSClient dssClient = new DSSClient(dssServer.baseUrl, PasswordUtil.decodePassword(dssServer.encryptedApiKey));

        String[] checkoutLocation = model.checkoutLocation.isEmpty() ? new String[0] : model.checkoutLocation.split("/");
        List<VirtualFile> createdFileList = new ArrayList<>();
        List<Recipe> recipes = model.recipes;
        for (Recipe recipe : recipes) {
            RecipeAndPayload recipeAndPayload = dssClient.loadRecipe(projectKey, recipe.name);
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
            VirtualFileUtils.writeToFile(file, recipeContent);

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
        return createdFileList;
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
