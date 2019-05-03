package com.dataiku.dss.intellij.actions.checkin.nodes;

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

public class CheckinTree extends Tree {
    private final int myCheckboxWidth;

    public CheckinTree() {
        final MyCellRenderer nodeRenderer = new MyCellRenderer();
        setCellRenderer(new MyTreeCellRenderer(nodeRenderer));
        myCheckboxWidth = new JCheckBox().getPreferredSize().width;
    }

    private static class MyCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            if (value instanceof RecipeTreeNode) {
                RecipeTreeNode x = (RecipeTreeNode) value;
                String filename = x.recipe.file.getName().toLowerCase();
                if (filename.endsWith(".py")) {
                    setIcon(Icons.FILE_PYTHON);
                } else if (filename.endsWith(".r")) {
                    setIcon(Icons.RECIPE_R);
                } else if (filename.endsWith(".sh")) {
                    setIcon(Icons.RECIPE_SHELL);
                } else if (filename.endsWith(".sql")) {
                    setIcon(Icons.FILE_SQL);
                } else {
                    setIcon(AllIcons.FileTypes.Any_type);
                }
            } else if (value instanceof PluginTreeNode) {
                setIcon(AllIcons.Nodes.Plugin);
            } else {
                setIcon(AllIcons.Nodes.Folder);
            }
            append(value.toString());
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
                    baseRect.setSize(myCheckboxWidth, baseRect.height);
                    if (baseRect.contains(e.getPoint())) {
                        setSelectionRow(row);
                        CheckinBaseNode[] selectedNodes = this.getSelectedNodes(CheckinBaseNode.class, null);
                        for (CheckinBaseNode selectedNode : selectedNodes) {
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
        if (node instanceof CheckinBaseNode) {
            CheckinBaseNode baseNode = (CheckinBaseNode) node;
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
