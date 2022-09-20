package com.dataiku.dss.intellij;

import java.util.ArrayList;

public class MonitoredDSSElements {

    public ArrayList<MonitoredRecipeFile> monitoredRecipeFiles;
    public ArrayList<MonitoredPlugin> monitoredPlugins;
    public ArrayList<MonitoredLibrary> monitoredLibraries;

    public MonitoredDSSElements(ArrayList<MonitoredRecipeFile> monitoredRecipeFiles, ArrayList<MonitoredPlugin> monitoredPlugins, ArrayList<MonitoredLibrary> monitoredLibraries) {
        this.monitoredRecipeFiles = monitoredRecipeFiles;
        this.monitoredPlugins = monitoredPlugins;
        this.monitoredLibraries = monitoredLibraries;
    }
}
