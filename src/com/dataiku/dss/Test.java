package com.dataiku.dss;

import java.util.List;

import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.Project;

public class Test {
    public static void main(String[] args) {
        final String baseUrl = "http://localhost:8082/";
        final String apiKey = "QjU4CPJcxSgn1jskvDXIsUvHuzCwY5ZQ";
        DSSClient dssClient = new DSSClient(baseUrl, apiKey);
        List<Project> projects = dssClient.listProjects();
        projects.stream().map(project -> project.name).forEach(System.out::println);
    }
}
