package com.dataiku.dss.intellij.actions.checkin.nodes;

import java.util.List;

import com.dataiku.dss.intellij.config.DssServer;

public class RootNode extends CheckinBaseNode {
    @Override
    public String toString() {
        return "[ROOT]";
    }

    public DssServerTreeNode getOrAddInstanceNode(DssServer dssServer) {
        for (DssServerTreeNode node : listChildren(DssServerTreeNode.class)) {
            if (node.dssServer.equals(dssServer)) {
                return node;
            }
        }
        // Not found, add it
        DssServerTreeNode newNode = new DssServerTreeNode(dssServer);
        add(newNode);
        return newNode;
    }

    public List<DssServerTreeNode> getInstanceNodes() {
        return listChildren(DssServerTreeNode.class);
    }
}
