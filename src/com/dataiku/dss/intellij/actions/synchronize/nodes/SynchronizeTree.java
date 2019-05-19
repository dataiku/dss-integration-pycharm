package com.dataiku.dss.intellij.actions.synchronize.nodes;

import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

import java.awt.*;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Icons;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.ThreeStateCheckBox.State;

public class SynchronizeTree extends Tree {
    private final int checkboxWidth;

    public SynchronizeTree() {
        final MyCellRenderer nodeRenderer = new MyCellRenderer();
        setCellRenderer(new MyTreeCellRenderer(nodeRenderer));
        checkboxWidth = new JCheckBox().getPreferredSize().width;
    }

    private static class MyCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            if (value instanceof SynchronizeNodeDssInstance) {
                SynchronizeNodeDssInstance node = (SynchronizeNodeDssInstance) value;
                append(node.dssInstance.label, REGULAR_ATTRIBUTES);
                append("  " + node.dssInstance.baseUrl, GRAY_ATTRIBUTES);
            } else if (value instanceof SynchronizeNodeRecipe) {
                SynchronizeNodeRecipe node = (SynchronizeNodeRecipe) value;
                String filename = node.recipe.file.getName().toLowerCase();
                if (filename.endsWith(".py")) {
                    setIcon(Icons.RECIPE_PYTHON);
                } else if (filename.endsWith(".r")) {
                    setIcon(Icons.RECIPE_R);
                } else if (filename.endsWith(".sh")) {
                    setIcon(Icons.RECIPE_SHELL);
                } else if (filename.endsWith(".sql")) {
                    setIcon(Icons.RECIPE_SQL);
                } else {
                    setIcon(AllIcons.FileTypes.Any_type);
                }
                append(node.toString());
            } else if (value instanceof SynchronizeNodePlugin) {
                setIcon(Icons.PLUGIN);
                append(value.toString());
            } else {
                setIcon(AllIcons.Nodes.Folder);
                append(value.toString());
            }

        }
    }

    private class MyTreeCellRenderer extends JPanel implements TreeCellRenderer {
        private final ColoredTreeCellRenderer myTextRenderer;
        private final ThreeStateCheckBox myCheckBox;

        MyTreeCellRenderer(ColoredTreeCellRenderer textRenderer) {
            super(new BorderLayout());
            myCheckBox = new ThreeStateCheckBox();
            myTextRenderer = textRenderer;

            add(myCheckBox, BorderLayout.WEST);
            add(myTextRenderer, BorderLayout.CENTER);
            setOpaque(false);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            setBackground(null);
            myCheckBox.setBackground(null);
            myCheckBox.setOpaque(false);

            myTextRenderer.setOpaque(false);
            myTextRenderer.setTransparentIconBackground(true);
            myTextRenderer.setToolTipText(null);
            myTextRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            myCheckBox.setState(getNodeStatus(value));
            myCheckBox.setEnabled(tree.isEnabled());
            revalidate();

            return this;
        }

        @Override
        public String getToolTipText() {
            return myTextRenderer.getToolTipText();
        }
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            if (isEnabled() && !e.isPopupTrigger()) {
                int row = getRowForLocation(e.getX(), e.getY());
                if (row >= 0) {
                    final Rectangle baseRect = getRowBounds(row);
                    baseRect.setSize(checkboxWidth, baseRect.height);
                    if (baseRect.contains(e.getPoint())) {
                        setSelectionRow(row);
                        SynchronizeBaseNode[] selectedNodes = this.getSelectedNodes(SynchronizeBaseNode.class, null);
                        for (SynchronizeBaseNode selectedNode : selectedNodes) {
                            selectedNode.toggle();
                        }
                        repaint();
                        return; // Disable further processing
                    }
                }
            }
        }
        super.processMouseEvent(e);
    }

    private State getNodeStatus(Object node) {
        if (node instanceof SynchronizeBaseNode) {
            SynchronizeBaseNode baseNode = (SynchronizeBaseNode) node;
            if (baseNode.selectionState == null) {
                return State.NOT_SELECTED;
            }
            switch (baseNode.selectionState) {
            default:
            case SELECTED:
                return State.SELECTED;
            case NOT_SELECTED:
                return State.NOT_SELECTED;
            case PARTLY_SELECTED:
                return State.DONT_CARE;
            }
        } else {
            return State.NOT_SELECTED;
        }
    }
}
