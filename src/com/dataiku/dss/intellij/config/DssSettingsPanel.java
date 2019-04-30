package com.dataiku.dss.intellij.config;

import static com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.Border;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.DataikuImages;
import com.intellij.openapi.Disposable;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;

public class DssSettingsPanel implements Disposable {
    private JPanel panel;
    private JBList<DssServer> serverList;
    private final List<DssServer> servers = new ArrayList<>();

    public DssSettingsPanel() {
    }

    private void create() {
        serverList = new JBList<>();
        serverList.getEmptyText().setText("No instances");
        serverList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    editServer();
                }
            }
        });
        JPanel serversPanel = new JPanel(new BorderLayout());
        ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(serverList);
        toolbarDecorator.setEditActionName("Edit").setEditAction((e) -> editServer());
        toolbarDecorator.setAddAction(new AddServerAction());
        toolbarDecorator.setRemoveAction(new RemoveServerAction());
        toolbarDecorator.setMoveUpAction(new MoveUpServerAction());
        toolbarDecorator.setMoveDownAction(new MoveDownServerAction());
        serversPanel.add(toolbarDecorator.createPanel(), "Center");
        JBLabel emptyLabel = new JBLabel("No instance selected", 0);
        JPanel emptyPanel = new JPanel(new BorderLayout());
        emptyPanel.add(emptyLabel, "Center");
        Border b = IdeBorderFactory.createTitledBorder("Instances");
        panel = new JPanel(new BorderLayout());
        panel.setBorder(b);
        panel.add(serversPanel);
        serverList.setCellRenderer(new ColoredListCellRenderer<DssServer>() {
            protected void customizeCellRenderer(@NotNull JList<? extends DssServer> list, DssServer server, int index, boolean selected, boolean hasFocus) {
                setIcon(DataikuImages.ICON_DATAIKU_24);
                append(" " + server.name, REGULAR_ATTRIBUTES);
                append("  [" + server.baseUrl + "]", GRAYED_ATTRIBUTES, false);
            }
        });
    }

    public JComponent getComponent() {
        if (panel == null) {
            create();
        }
        return panel;
    }

    public boolean isModified(@NotNull DssSettings settings) {
        return !servers.equals(settings.getDssServers());
    }

    public void save(@NotNull DssSettings settings) {
        settings.setDssServers(new ArrayList<>(servers));
    }

    public void load(@NotNull DssSettings settings) {
        servers.clear();
        CollectionListModel<DssServer> listModel = new CollectionListModel<>(new ArrayList<>());
        listModel.add(settings.getDssServers());
        servers.addAll(settings.getDssServers());
        serverList.setModel(listModel);
        if (!servers.isEmpty()) {
            serverList.setSelectedValue(servers.get(0), true);
        }
    }

    private DssServer getSelectedServer() {
        return serverList.getSelectedValue();
    }

    private void editServer() {
        DssServer selectedServer = getSelectedServer();
        int selectedIndex = serverList.getSelectedIndex();
        if (selectedServer != null) {
            DssServerDialog dialog = new DssServerDialog(selectedServer);
            if (dialog.showAndGet()) {
                DssServer newServer = dialog.getServer();
                ListModel<DssServer> model = serverList.getModel();
                ((CollectionListModel<DssServer>) model).setElementAt(newServer, selectedIndex);
                servers.set(servers.indexOf(selectedServer), newServer);
            }
        }
    }

    public void dispose() {
    }

    private class AddServerAction implements AnActionButtonRunnable {
        public void run(AnActionButton anActionButton) {
            Set<String> existingNames = servers.stream().map(DssServer::getName).collect(Collectors.toSet());
            DssServerDialog dialog = new DssServerDialog(existingNames);
            if (dialog.showAndGet()) {
                DssServer created = dialog.getServer();
                servers.add(created);
                ((CollectionListModel<DssServer>) serverList.getModel()).add(created);
                serverList.setSelectedIndex(serverList.getModel().getSize() - 1);
            }
        }
    }

    private class RemoveServerAction implements AnActionButtonRunnable {
        public void run(AnActionButton anActionButton) {
            DssServer server = getSelectedServer();
            int selectedIndex = serverList.getSelectedIndex();
            if (server != null) {
                CollectionListModel<DssServer> model = (CollectionListModel<DssServer>) serverList.getModel();
                model.remove(server);
                servers.remove(server);
                if (model.getSize() > 0) {
                    int newIndex = Math.min(model.getSize() - 1, Math.max(selectedIndex - 1, 0));
                    serverList.setSelectedValue(model.getElementAt(newIndex), true);
                }
            }
        }
    }

    private class MoveUpServerAction implements AnActionButtonRunnable {
        public void run(AnActionButton anActionButton) {
            DssServer server = getSelectedServer();
            int selectedIndex = serverList.getSelectedIndex();
            if (server != null && selectedIndex > 0) {
                CollectionListModel<DssServer> model = (CollectionListModel<DssServer>) serverList.getModel();
                int newIndex = selectedIndex - 1;
                model.remove(server);
                model.add(newIndex, server);

                servers.remove(server);
                servers.add(newIndex, server);

                serverList.setSelectedIndex(newIndex);
            }
        }
    }

    private class MoveDownServerAction implements AnActionButtonRunnable {
        public void run(AnActionButton anActionButton) {
            DssServer server = getSelectedServer();
            int selectedIndex = serverList.getSelectedIndex();
            if (server != null && selectedIndex < servers.size() - 1) {
                CollectionListModel<DssServer> model = (CollectionListModel<DssServer>) serverList.getModel();
                int newIndex = selectedIndex + 1;
                model.remove(server);
                model.add(newIndex, server);

                servers.remove(server);
                servers.add(newIndex, server);

                serverList.setSelectedIndex(newIndex);
            }
        }
    }
}
