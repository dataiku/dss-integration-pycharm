package com.dataiku.dss.model;

public class Recipe {
    // Taggable Object
    public VersionTag versionTag;
    public VersionTag creationTag;
    public String description;
    public String shortDesc;

    // Recipe
    public String projectKey;
    public String name;
    public String type;
}
