package com.dataiku.dss.intellij.actions.checkout;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.Os;
import com.dataiku.dss.intellij.config.DssServer;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

public class DataikuInternalClientInstaller {
    private static Logger log = Logger.getInstance(DataikuInternalClientInstaller.class);

    public boolean isInstalled(RunConfiguration runConfiguration) {
        String interpreterPath = getInterpreterPath(runConfiguration);
        if (interpreterPath == null) {
            throw new IllegalStateException("Unable to retrieve the path of the default python interpreter.");
        }
        return isInstalled(interpreterPath);
    }

    public boolean isInstalled(String interpreterPath) {
        Preconditions.checkNotNull(interpreterPath, "interpreterPath");
        File workingDir = new File(interpreterPath).getParentFile();
        String pipCommand = Os.isWindows() ? "pip" : "./pip";
        ProcessBuilder pipList = new ProcessBuilder()
                .command(pipCommand, "list")
                .redirectErrorStream(true)
                .directory(workingDir);
        ProcessOutcome pipListOutcome = executeProcess(pipList);
        if (pipListOutcome.exitCode != 0) {
            throw new IllegalStateException(String.format("pip list command exited with non zero error code: %d", pipListOutcome.exitCode));
        }
        return pipListOutcome.output.stream().anyMatch(s -> s.startsWith("dataiku-internal-client"));
    }

    public String getInstalledVersion(String interpreterPath) {
        Preconditions.checkNotNull(interpreterPath, "interpreterPath");
        File workingDir = new File(interpreterPath).getParentFile();
        String pipCommand = Os.isWindows() ? "pip" : "./pip";
        ProcessBuilder pipList = new ProcessBuilder()
                .command(pipCommand, "list")
                .redirectErrorStream(true)
                .directory(workingDir);
        ProcessOutcome pipListOutcome = executeProcess(pipList);
        if (pipListOutcome.exitCode != 0) {
            throw new IllegalStateException(String.format("pip list command exited with non zero error code: %d", pipListOutcome.exitCode));
        }
        return pipListOutcome.output.stream()
                .filter(s -> s.startsWith("dataiku-internal-client"))
                .findFirst()
                .map(s -> s.substring("dataiku-internal-client".length()).trim())
                .orElse(null);
    }

    public void install(Module module, DssServer dssServer, String recipeName) {
        Preconditions.checkNotNull(module, "module");
        Preconditions.checkNotNull(dssServer, "dssServer");
        Preconditions.checkNotNull(recipeName, "recipeName");

        Project project = module.getProject();
        RunManager runManager = RunManager.getInstance(project);
        Optional<RunConfiguration> existingConfiguration = runManager.getAllConfigurationsList().stream().filter(c -> recipeName.equalsIgnoreCase(c.getName())).findAny();
        if (!existingConfiguration.isPresent()) {
            throw new IllegalStateException(String.format("Unable to find a Run Configuration named '%s'", recipeName));
        }
        RunConfiguration runConfiguration = existingConfiguration.get();

        String interpreterPath = getInterpreterPath(runConfiguration);
        if (interpreterPath == null) {
            throw new IllegalStateException("Unable to retrieve the path of the default python interpreter.");
        }
        File workingDir = new File(interpreterPath).getParentFile();
        String pipCommand = Os.isWindows() ? "pip" : "./pip";
        boolean internalClientInstalled = isInstalled(runConfiguration);
        if (!internalClientInstalled) {
            ProcessBuilder pipInstall = new ProcessBuilder()
                    .command(pipCommand, "install", "--upgrade", clientTarGzUrl(dssServer))
                    .redirectErrorStream(true)
                    .directory(workingDir);
            executeProcess(pipInstall);
        }
    }

    public void install(RunConfiguration runConfiguration, DssServer dssServer) {
        Preconditions.checkNotNull(dssServer, "dssServer");

        String interpreterPath = getInterpreterPath(runConfiguration);
        if (interpreterPath == null) {
            throw new IllegalStateException("Unable to retrieve the path of the default python interpreter.");
        }
        boolean internalClientInstalled = isInstalled(runConfiguration);
        if (!internalClientInstalled) {
            install(interpreterPath, dssServer);
        }
    }

    public void install(String interpreterPath, DssServer dssServer) {
        Preconditions.checkNotNull(dssServer, "dssServer");
        Preconditions.checkNotNull(interpreterPath, "interpreterPath");

        File workingDir = new File(interpreterPath).getParentFile();
        String pipCommand = Os.isWindows() ? "pip" : "./pip";
        ProcessBuilder pipInstall = new ProcessBuilder()
                .command(pipCommand, "install", "--upgrade", clientTarGzUrl(dssServer))
                .redirectErrorStream(true)
                .directory(workingDir);
        executeProcess(pipInstall);
    }

    public Process installAsync(String interpreterPath, DssServer dssServer) throws IOException {
        Preconditions.checkNotNull(dssServer, "dssServer");
        Preconditions.checkNotNull(interpreterPath, "interpreterPath");

        File workingDir = new File(interpreterPath).getParentFile();
        String pipCommand = Os.isWindows() ? "pip" : "./pip";
        ProcessBuilder pipInstall = new ProcessBuilder()
                .command(pipCommand, "install", "--upgrade", clientTarGzUrl(dssServer))
                .redirectErrorStream(true)
                .directory(workingDir);
        return pipInstall.start();
    }

    private ProcessOutcome executeProcess(ProcessBuilder pb) {
        ProcessOutput output = null;
        try {
            log.info("Executing command: " + Joiner.on(" ").join(pb.command()));
            Process process = pb.start();
            output = new ProcessOutput(process.getInputStream());
            Thread consumerThread = new Thread(output);
            consumerThread.start();
            int exitCode = process.waitFor();
            consumerThread.join();
            return new ProcessOutcome(exitCode, output.read(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProcessOutcome(-1, Collections.emptyList(), null);
        } catch (Exception e) {
            return new ProcessOutcome(-1, output == null ? Collections.emptyList() : output.read(), e);
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
        public volatile boolean done;

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
            done = true;
        }

        private void consume(InputStream inputStream) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (output) {
                    output.add(line);
                }
                log.debug(line);
            }
        }
    }

    @NotNull
    private String clientTarGzUrl(DssServer dssServer) {
        return dssServer.baseUrl + "/public/packages/dataiku-internal-client.tar.gz";
    }

    @Nullable
    @SuppressWarnings({"SameParameterValue", "JavaReflectionMemberAccess"})
    private String getInterpreterPath(RunConfiguration configuration) {
        try {
            return (String) configuration.getClass().getMethod("getInterpreterPath").invoke(configuration);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.warn("Unable to retrieve the Python interpreter path for the new Run Configuration", e);
            return null;
        }
    }
}
