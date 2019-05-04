package com.dataiku.dss.intellij.actions.synchronize.nodes;

import java.util.List;

import com.dataiku.dss.intellij.config.DssServer;

public class SynchronizeNodeRoot extends SynchronizeBaseNode {
    @Override
    public String toString() {
        return "[ROOT]";
    }

    public SynchronizeNodeDssInstance getOrAddInstanceNode(DssServer dssServer) {
        for (SynchronizeNodeDssInstance node : listChildren(SynchronizeNodeDssInstance.class)) {
            if (node.dssServer.equals(dssServer)) {
                return node;
            }
        }
        // Not found, add it
        SynchronizeNodeDssInstance newNode = new SynchronizeNodeDssInstance(dssServer);
        add(newNode);
        return newNode;
    }

    public List<SynchronizeNodeDssInstance> getInstanceNodes() {
        return listChildren(SynchronizeNodeDssInstance.class);
    }
}
