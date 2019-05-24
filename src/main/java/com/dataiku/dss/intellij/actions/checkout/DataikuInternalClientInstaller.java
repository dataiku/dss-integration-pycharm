package com.dataiku.dss.intellij.actions.checkout;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.config.DssInstance;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.intellij.openapi.util.SystemInfo;

@SuppressWarnings("WeakerAccess")
public class DataikuInternalClientInstaller {
    private static final String DATAIKU_INTERNAL_CLIENT = "dataiku-internal-client";
    private static final Logger log = Logger.getInstance(DataikuInternalClientInstaller.class);

    @NotNull
    public static String getInstallCommandPreview(DssInstance dssInstance) {
        return Joiner.on(" ").join(getInstallCommand(null, dssInstance));
    }

    public String getInstalledVersion(String interpreterPath) throws InterruptedException {
        Preconditions.checkNotNull(interpreterPath, "interpreterPath");
        log.info("Checking installed version of Dataiku Internal Client in interpreter: " + interpreterPath);
        File workingDir = new File(interpreterPath).getParentFile();
        ProcessBuilder pipList = new ProcessBuilder()
                .command(getPipCommand(workingDir), "list", "--format=columns")
                .redirectErrorStream(true)
                .directory(workingDir);
        ProcessOutcome pipListOutcome = executeProcess(pipList);
        if (pipListOutcome.exitCode != 0) {
            throw new IllegalStateException(String.format("pip list command exited with non zero error code: %d", pipListOutcome.exitCode));
        }
        return pipListOutcome.output.stream()
                .filter(s -> s.startsWith(DATAIKU_INTERNAL_CLIENT))
                .findFirst()
                .map(s -> s.substring(DATAIKU_INTERNAL_CLIENT.length()).trim())
                .orElse(null);
    }

    public Process installAsync(String interpreterPath, DssInstance dssInstance) throws IOException {
        Preconditions.checkNotNull(dssInstance, "dssInstance");
        Preconditions.checkNotNull(interpreterPath, "interpreterPath");

        File workingDir = new File(interpreterPath).getParentFile();
        ProcessBuilder pipInstall = new ProcessBuilder()
                .command(getInstallCommand(workingDir, dssInstance))
                .redirectErrorStream(true)
                .directory(workingDir);
        return pipInstall.start();
    }

    private ProcessOutcome executeProcess(ProcessBuilder pb) throws InterruptedException {
        ProcessOutput output;
        try {
            log.info(String.format("Executing command '%s' in working directory '%s'.", Joiner.on(" ").join(pb.command()), pb.directory()));
            Process process = pb.start();
            output = new ProcessOutput(process.getInputStream());
            Thread consumerThread = new Thread(output);
            consumerThread.start();
            int exitCode = process.waitFor();
            consumerThread.join();
            return new ProcessOutcome(exitCode, output.read(), null);
        } catch (IOException e) {
            return new ProcessOutcome(-1, Collections.emptyList(), e);
        }
    }

    private static class ProcessOutcome {
        public final int exitCode;
        public final List<String> output;
        public final Exception exception;

        private ProcessOutcome(int exitCode, List<String> output, Exception exception) {
            this.exitCode = exitCode;
            this.output = output;
            this.exception = exception;
        }
    }

    public static class ProcessOutput implements Runnable {
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
        }

        private void consume(InputStream inputStream) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (output) {
                    output.add(line);
                }
                log.info(line);
            }
        }
    }

    @NotNull
    @VisibleForTesting
    static String extractHostAndPort(String baseUrl) {
        String prefix = "://";
        int baseIndex = baseUrl.indexOf(prefix);
        if (baseIndex < 0) {
            throw new IllegalArgumentException("Missing '://' string in server base URL: " + baseUrl);
        }
        int endIndex = baseUrl.indexOf("/", baseIndex + prefix.length());
        return (endIndex < 0) ? baseUrl.substring(baseIndex + prefix.length()) : baseUrl.substring(baseIndex + prefix.length(), endIndex);
    }

    /**
     * Returns the command to use to launch pip, or a preview of the command (suitable to be displayed in log messages, ...)
     *
     * @param workingDir Directory containing the python binaries (python, pip, ...). {@code null} to get the preview.
     */
    @NotNull
    private static String getPipCommand(File workingDir) {
        boolean forPreview = (workingDir == null);
        if (forPreview) {
            return "pip";
        } else {
            return SystemInfo.isWindows ? new File(workingDir, "pip.exe").getAbsolutePath() : "./pip";
        }
    }

    private static List<String> getInstallCommand(File workingDir, DssInstance dssInstance) {
        List<String> result = new ArrayList<>();
        result.add(getPipCommand(workingDir));
        result.add("install");
        result.add("--upgrade");
        if (dssInstance.noCheckCertificate) {
            result.add("--trusted-host=" + extractHostAndPort(dssInstance.baseUrl));
        }
        result.add(clientTarGzUrl(dssInstance));
        return result;
    }

    @NotNull
    private static String clientTarGzUrl(DssInstance dssInstance) {
        return dssInstance.baseUrl + "/public/packages/dataiku-internal-client.tar.gz";
    }
}
