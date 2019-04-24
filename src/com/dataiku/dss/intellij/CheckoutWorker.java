package com.dataiku.dss.intellij;

import static com.dataiku.dss.intellij.VirtualFileUtils.getContentHash;
import static com.dataiku.dss.intellij.VirtualFileUtils.getOrCreateVirtualFile;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.intellij.metadata.DssMetadataManager;
import com.dataiku.dss.intellij.metadata.DssRecipeMetadata;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.Recipe;
import com.dataiku.dss.model.RecipeAndPayload;
import com.google.common.base.Preconditions;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.vfs.VirtualFile;

public class CheckoutWorker {

    public VirtualFile checkout(CheckoutDSSItem item) throws IOException {
        Preconditions.checkNotNull(item, "item");

        // Retrieve project key
        String projectKey = item.recipe.projectKey;

        // Retrieve recipe & its payload
        DssServer dssServer = item.server;
        DSSClient dssClient = new DSSClient(dssServer.baseUrl, PasswordUtil.decodePassword(dssServer.encryptedApiKey));

        RecipeAndPayload recipeAndPayload = dssClient.loadRecipe(projectKey, item.recipe.name);
        String recipeContent = recipeAndPayload.payload;
        if (recipeContent == null) {
            recipeContent = "";
        }

        // Retrieve IntelliJ module
        ModuleRootManager module = ModuleRootManager.getInstance(item.module);

        // Read the metadata
        //...

        // Write recipe file
        Object requestor = this;
        VirtualFile moduleRootFolder = getModuleRootFolder(module);

        String serverName = item.server.name;
        String filename = getFilename(item.recipe);
        VirtualFile result = getOrCreateVirtualFile(requestor, moduleRootFolder, serverName, projectKey, filename);
        VirtualFileUtils.writeToFile(result, recipeContent);

        // Write metadata
        DssRecipeMetadata fileMetadata = new DssRecipeMetadata();
        fileMetadata.path = serverName + "/" + projectKey + "/" + filename;
        fileMetadata.contentDssVersion = recipeAndPayload.recipe.versionTag.versionNumber;
        fileMetadata.contentHash = getContentHash(recipeContent);
        fileMetadata.projectKey = projectKey;
        fileMetadata.recipeName = item.recipe.name;
        fileMetadata.instance = serverName;

        DssMetadataManager.updateRecipeMetadata(requestor, moduleRootFolder, fileMetadata);
        return result;
    }

    private static String getFilename(Recipe recipe) {
        return recipe.name + RecipeUtils.extension(recipe.type);
    }

    @NotNull
    private VirtualFile getModuleRootFolder(ModuleRootManager module) {
        VirtualFile[] contentRoots = module.getContentRoots();
        VirtualFile contentRoot = contentRoots.length == 0 ? null : contentRoots[0];
        if (contentRoot == null) {
            throw new IllegalStateException(String.format("No source root directory defined for module %s. Update its configuration to fix the problem.", module.getModule().getName()));
        }
        return contentRoot;
    }

}
