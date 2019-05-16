package com.dataiku.dss.intellij.actions.checkout;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.text.DefaultCaret;

import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.config.DssInstance;
import com.google.common.base.Joiner;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;

public class DataikuInternalClientLibsInstallerDialog extends DialogWrapper {
    private static final Logger log = Logger.getInstance(DataikuInternalClientLibsInstallerDialog.class);
    private final Sdk sdk;
    private final DssInstance dssInstance;
    private JTextArea textArea;
    private Process installProcess;

    public DataikuInternalClientLibsInstallerDialog(Sdk sdk, DssInstance dssInstance) {
        super(true); // use current window as parent
        this.sdk = sdk;
        this.dssInstance = dssInstance;
        init();
        setTitle("Install Dataiku Internal Client Libraries");
        setOKButtonText("Close");
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        textArea = new JTextArea();
        textArea.setEnabled(true);
        textArea.setMinimumSize(new Dimension(240, 240));
        JBScrollPane scroll = new JBScrollPane(textArea);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setMinimumSize(new Dimension(240, 240));

        // Start installation
        try {
            startInstallation();
            setOKActionEnabled(false);
            setCancelButtonText("Abort");
        } catch (IOException e) {
            textArea.append("--------------------\n");
            textArea.append("ERROR\n");
            textArea.append("--------------------\n");
            textArea.append(e.getMessage());
            textArea.append("\n");
        }

        return scroll;
    }

    @Override
    protected void doOKAction() {
        if (installProcess == null || !installProcess.isAlive()) {
            super.doOKAction();
        }
    }

    private void startInstallation() throws IOException {
        textArea.append("> " + DataikuInternalClientInstaller.getInstallCommandPreview(dssInstance) + "\n");
        installProcess = new DataikuInternalClientInstaller().installAsync(sdk.getHomePath(), dssInstance);
        new Thread(new ProcessOutput(installProcess.getInputStream())).start();
    }

    @Override
    public final void doCancelAction() {
        if (installProcess != null && installProcess.isAlive()) {
            if (Messages.showDialog("Do you really want to abort the installation process?", "Abort", new String[]{Messages.YES_BUTTON, Messages.NO_BUTTON}, 1, null) == 0) {
                installProcess.destroy();
            } else {
                return; // Do not cancel the action
            }
        }
        super.doCancelAction();
    }

    public class ProcessOutput implements Runnable {
        private final ArrayDeque<String> output = new ArrayDeque<>();
        private final InputStream inputStream;

        private ProcessOutput(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        public List<String> read() {
            List<String> result = new ArrayList<>();
            synchronized (output) {
                while (!output.isEmpty()) {
                    result.add(output.removeFirst());
                }
            }
            return result;
        }

        @Override
        public void run() {
            try {
                consume(inputStream);
            } catch (IOException e) {
                log.debug("An error occurred while consuming process output stream", e);
            }
            SwingUtilities.invokeLater(() -> {
                updateTextArea();
                setOKActionEnabled(true);
                setCancelButtonText("Cancel");
            });
        }

        private void consume(InputStream inputStream) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (output) {
                    output.add(line);
                }
                SwingUtilities.invokeLater(this::updateTextArea);
                log.debug(line);
            }
        }

        private void updateTextArea() {
            List<String> lines = read();
            if (!lines.isEmpty()) {
                textArea.append(Joiner.on("\n").join(lines));
                textArea.append("\n");
            }
            ((DefaultCaret) textArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        }
    }
}

