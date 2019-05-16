package com.dataiku.dss.intellij.actions.synchronize.nodes;

import java.util.List;

import com.dataiku.dss.intellij.config.DssInstance;

public class SynchronizeNodeRoot extends SynchronizeBaseNode {
    @Override
    public String toString() {
        return "[ROOT]";
    }

    public SynchronizeNodeDssInstance getOrAddInstanceNode(DssInstance dssInstance) {
        for (SynchronizeNodeDssInstance node : listChildren(SynchronizeNodeDssInstance.class)) {
            if (node.dssInstance.equals(dssInstance)) {
                return node;
            }
        }
        // Not found, add it
        SynchronizeNodeDssInstance newNode = new SynchronizeNodeDssInstance(dssInstance);
        if (newNode.dssInstance.isDefault) {
            insert(newNode, 0);
        } else {
            add(newNode);
        }
        return newNode;
    }

    public List<SynchronizeNodeDssInstance> getInstanceNodes() {
        return listChildren(SynchronizeNodeDssInstance.class);
    }
}
