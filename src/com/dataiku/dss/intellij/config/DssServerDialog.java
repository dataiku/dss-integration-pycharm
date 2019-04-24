package com.dataiku.dss.intellij.config;

import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import javax.swing.*;

import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.model.DSSClient;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;

public class DssServerDialog extends DialogWrapper {

    private static final String API_KEY_PLACEHOLDER = "__DATAIKU_DSS_API_KEY_PLACEHOLDER__";

    private final DssServer server;
    private final Collection<String> existingNames;
    private JPanel panel;
    private JBTextField urlText;
    private JTextField nameField;
    private JPasswordField apiKeyField;

    public DssServerDialog(DssServer serverToEdit) {
        this(serverToEdit, Collections.emptySet());
    }

    public DssServerDialog(Set<String> existingNames) {
        this(null, existingNames);
    }

    private DssServerDialog(DssServer serverToEdit, Set<String> existingNames) {
        super(true); // use current window as parent

        boolean editing = serverToEdit != null;
        this.existingNames = existingNames;

        server = new DssServer();
        if (editing) {
            server.name = serverToEdit.name;
            server.baseUrl = serverToEdit.baseUrl;
            server.encryptedApiKey = serverToEdit.encryptedApiKey;
        }

        init();
        setTitle((editing ? "Edit" : "New") + " Server");
    }

    public DssServer getServer() {
        return server;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        panel = new JPanel(new GridLayoutManager(3, 2));
        panel.setPreferredSize(new Dimension(480, 100));

        panel.add(new JLabel("Name"), newConstraints(0, 0, GridConstraints.FILL_NONE));
        nameField = new JTextField();
        nameField.setMinimumSize(new Dimension(320, 28));
        nameField.setPreferredSize(new Dimension(320, 28));
        nameField.setToolTipText("Name of this server (mandatory field)");
        panel.add(nameField, newConstraints(0, 1, GridConstraints.FILL_HORIZONTAL));

        panel.add(new JLabel("Base URL"), newConstraints(1, 0, GridConstraints.FILL_NONE));
        urlText = new JBTextField();
        urlText.getEmptyText().setText("Example: http://localhost:11200");
        urlText.setToolTipText("URL to connect to this server (mandatory field)");
        panel.add(urlText, newConstraints(1, 1, GridConstraints.FILL_HORIZONTAL));

        panel.add(new JLabel("API Key"), newConstraints(2, 0, GridConstraints.FILL_NONE));
        apiKeyField = new JPasswordField();
        apiKeyField.setToolTipText("API key to connect to this server (mandatory field)");
        panel.add(apiKeyField, newConstraints(2, 1, GridConstraints.FILL_HORIZONTAL));

        fillFields();

        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        if (nameField.isEnabled()) {
            return nameField;
        } else {
            return urlText.isEnabled() ? urlText : null;
        }
    }

    @Override
    protected void doOKAction() {
        if (!isOKActionEnabled()) {
            return;
        }
        try {
            commit();
            super.doOKAction();
        } catch (CommitStepException e) {
            Messages.showErrorDialog(panel, e.getMessage(), "Invalid Configuration");
        }
    }

    private void fillFields() {
        if (!isBlank(server.name)) {
            nameField.setText(server.name);
            nameField.setEnabled(false);
        }
        if (!isBlank(server.baseUrl)) {
            urlText.setText(server.baseUrl);
        }
        if (!isBlank(server.encryptedApiKey)) {
            apiKeyField.setText(API_KEY_PLACEHOLDER);
        }
    }

    private void commit() throws CommitStepException {
        validateName();
        validateUrl();
        save();
    }

    private void validateName() throws CommitStepException {
        String name = nameField.getText().trim();
        if (isBlank(name)) {
            throw new CommitStepException("Please provide a name.");
        }
        if (existingNames.contains(name)) {
            throw new CommitStepException("There is already a Dataiku DSS server with that name. Please choose another name.");
        }
    }

    private void validateUrl() throws CommitStepException {
        try {
            URL url = new URL(urlText.getText());
            if (isBlank(url.getHost())) {
                throw new CommitStepException("Please provide a valid URL");
            }
        } catch (MalformedURLException e) {
            throw new CommitStepException("Please provide a valid URL");
        }
    }

    private void save() throws CommitStepException {
        server.name = nameField.getText().trim();
        server.baseUrl = urlText.getText().trim();

        char[] apiKey = apiKeyField.getPassword();
        if (!Arrays.equals(API_KEY_PLACEHOLDER.toCharArray(), apiKey)) {
            server.encryptedApiKey = PasswordUtil.encodePassword(apiKey);
        }
        clearPasswordArray(apiKey);

        // Verify that we can connect to the server
        if (!checkConnection()) {
            throw new CommitStepException("Unable to connect to DSS using the provided URL and credentials.");
        }
    }

    private boolean checkConnection() throws CommitStepException {
        try {
            return new DSSClient(server.baseUrl, PasswordUtil.decodePassword(server.encryptedApiKey)).canConnect();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * For security reasons, write zeros to overwrite the password
     */
    private static void clearPasswordArray(char[] password) {
        for (int i = 0; i < password.length; i++) {
            password[i] = 0;
        }
    }

    private static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    private static GridConstraints newConstraints(int row, int column, int fill) {
        GridConstraints result = new GridConstraints();
        result.setRow(row);
        result.setColumn(column);
        result.setFill(fill);
        return result;
    }
}
