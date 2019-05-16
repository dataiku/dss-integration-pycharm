package com.dataiku.dss.intellij.actions.checkout;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.config.DssInstance;
import com.google.common.base.Preconditions;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configuration.AbstractRunConfiguration;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class RunConfigurationGenerator {
    private static final Logger log = Logger.getInstance(RunConfigurationGenerator.class);

    public RunConfiguration getDefaultPythonRunConfiguration(Module module) {
        Preconditions.checkNotNull(module, "module");

        Project project = module.getProject();
        RunManager runManager = RunManager.getInstance(project);

        ConfigurationType pythonConfigurationType = ConfigurationTypeUtil.findConfigurationType("PythonConfigurationType");
        if (pythonConfigurationType == null) {
            throw new IllegalStateException("No Python run configuration type found.");
        }
        ConfigurationFactory[] pythonConfigurationFactories = pythonConfigurationType.getConfigurationFactories();
        if (pythonConfigurationFactories == null || pythonConfigurationFactories.length == 0) {
            throw new IllegalStateException("No Python run configuration factories found.");
        }
        ConfigurationFactory pythonConfigurationFactory = pythonConfigurationFactories[0];

        // Retrieve the template for Python Run Configurations and extract its Run configuration
        RunnerAndConfigurationSettings pythonTemplate = runManager.getConfigurationTemplate(pythonConfigurationFactory);
        return pythonTemplate.getConfiguration();
    }

    public RunConfiguration createScriptRunConfiguration(Module module, VirtualFile scriptFile, DssInstance dssServer, String projectKey, String recipeName) {
        Preconditions.checkNotNull(module, "module");
        Preconditions.checkNotNull(scriptFile, "scriptFile");
        Preconditions.checkNotNull(dssServer, "dssServer");
        Preconditions.checkNotNull(projectKey, "projectKey");
        Preconditions.checkNotNull(recipeName, "recipeName");

        Project project = module.getProject();
        RunManager runManager = RunManager.getInstance(project);
        Optional<RunConfiguration> existingConfiguration = runManager.getAllConfigurationsList().stream().filter(c -> recipeName.equalsIgnoreCase(c.getName())).findAny();
        if (existingConfiguration.isPresent()) {
            return existingConfiguration.get();
        }

        // Retrieve the template for Python Run Configurations and instantiate a new configuration
        RunConfiguration defaultRunConfiguration = getDefaultPythonRunConfiguration(module);
        ConfigurationFactory factory = defaultRunConfiguration.getFactory();
        AbstractRunConfiguration runConfiguration = (AbstractRunConfiguration) factory.createConfiguration(recipeName, defaultRunConfiguration);

        // Set the environment variables, SDK, script name and working directory.
        Map<String, String> envs = new HashMap<>(runConfiguration.getEnvs());
        if (!dssServer.isDefault) {
            envs.put("DKU_DSS_URL", dssServer.baseUrl);
            envs.put("DKU_API_KEY", dssServer.apiKey);
            envs.put("DKU_NO_CHECK_CERTIFICATE", String.valueOf(dssServer.noCheckCertificate));
        }
        envs.put("DKU_CURRENT_PROJECT_KEY", projectKey);
        runConfiguration.setEnvs(envs);
        runConfiguration.setModule(module);
        runConfiguration.setModuleName(module.getName());

        setUseModuleSdk(runConfiguration, true);
        setScriptName(runConfiguration, scriptFile.getCanonicalPath());
        setWorkingDirectory(runConfiguration, scriptFile.getParent().getCanonicalPath());

        RunnerAndConfigurationSettings settings = runManager.createConfiguration(runConfiguration, factory);
        runManager.addConfiguration(settings);
        runManager.setSelectedConfiguration(settings);
        return runConfiguration;
    }

    @SuppressWarnings({"SameParameterValue", "JavaReflectionMemberAccess"})
    private static void setUseModuleSdk(RunConfiguration configuration, boolean useModuleSdk) {
        try {
            configuration.getClass().getMethod("setUseModuleSdk", boolean.class).invoke(configuration, useModuleSdk);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.warn("Unable to change Python SDK to new Run Configuration", e);
        }
    }

    @SuppressWarnings({"SameParameterValue", "JavaReflectionMemberAccess"})
    private static void setScriptName(RunConfiguration configuration, String scriptName) {
        try {
            configuration.getClass().getMethod("setScriptName", String.class).invoke(configuration, scriptName);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.warn("Unable to set script name to new Run Configuration", e);
        }
    }

    @SuppressWarnings({"SameParameterValue", "JavaReflectionMemberAccess"})
    private static void setWorkingDirectory(RunConfiguration configuration, String workingDirectory) {
        try {
            configuration.getClass().getMethod("setWorkingDirectory", String.class).invoke(configuration, workingDirectory);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.warn("Unable to set working directory to new Run Configuration", e);
        }
    }
}
