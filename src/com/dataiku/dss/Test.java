package com.dataiku.dss;

import java.util.List;

import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.DssException;
import com.dataiku.dss.model.dss.FolderContent;
import com.dataiku.dss.model.dss.Plugin;

public class Test {
    public static void main(String[] args) throws DssException {
        final String baseUrl = "http://localhost:11200/";
        final String apiKey = "tBj824dO47XYbYvtjSbdGheBO7uolR2P";
        DSSClient dssClient = new DSSClient(baseUrl, apiKey, true);
        List<Plugin> projects = dssClient.listPluginsInDevelopment();
        projects.stream().map(plugin -> plugin.id).forEach(System.out::println);
        List<FolderContent> folderContents = dssClient.listPluginFiles("PyCharmDemo");
        for (FolderContent folderContent : folderContents) {
            System.out.println("----");
            System.out.println(folderContent);
        }
    }
}
