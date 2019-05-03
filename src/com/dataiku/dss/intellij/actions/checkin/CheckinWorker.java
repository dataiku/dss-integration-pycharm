package com.dataiku.dss.intellij.actions.checkin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.dataiku.dss.intellij.actions.checkin.nodes.DssServerTreeNode;
import com.dataiku.dss.intellij.actions.checkin.nodes.PluginContentTreeNode;
import com.dataiku.dss.intellij.actions.checkin.nodes.PluginTreeNode;
import com.dataiku.dss.intellij.actions.checkin.nodes.PluginsTreeNode;
import com.dataiku.dss.intellij.actions.checkin.nodes.SelectionState;
import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.metadata.DssPluginFileMetadata;
import com.google.common.base.Preconditions;

public class CheckinWorker {
    private final CheckinModel model;

    public CheckinWorker(CheckinModel model) {
        Preconditions.checkNotNull(model, "model");
        Preconditions.checkNotNull(model.synchronizeStepRootNode, "model.rootNode");
        this.model = model;
    }

    public void checkin() throws IOException {
        for (DssServerTreeNode instanceNode : model.synchronizeStepRootNode.getInstanceNodes()) {
            PluginsTreeNode pluginsNode = instanceNode.getPluginsNode();
            if (pluginsNode != null) {
                for (PluginTreeNode pluginNode : pluginsNode.getPluginNodes()) {
                    for (DssPluginFileMetadata selectedPluginFile : getSelectedPluginFiles(pluginNode)) {
                        checkinPluginFile(selectedPluginFile);
                    }
                }
            }
        }
    }

    private void checkinPluginFile(DssPluginFileMetadata pluginFile) throws IOException {
        DssServer dssServer = DssSettings.getInstance().getDssServer(pluginFile.instance);
        DSSClient client = dssServer.createClient();
        byte[] content = null;
        client.uploadPluginFile(pluginFile.pluginId, pluginFile.remotePath, content);
    }

    private List<DssPluginFileMetadata> getSelectedPluginFiles(PluginTreeNode pluginNode) {
        List<DssPluginFileMetadata> selectedPluginFiles = new ArrayList<>();
        addSelectedPluginFiles(selectedPluginFiles, pluginNode.getContentNodes());
        return selectedPluginFiles;
    }

    private void addSelectedPluginFiles(List<DssPluginFileMetadata> selectedPluginFiles, List<PluginContentTreeNode> nodes) {
        for (PluginContentTreeNode node : nodes) {
            if (node.selectionState != SelectionState.NOT_SELECTED) {
                selectedPluginFiles.add(node.pluginFile);
            }
            addSelectedPluginFiles(selectedPluginFiles, node.getContentNodes());
        }
    }
}
