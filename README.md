# Dataiku DSS PyCharm Plugin

This plugin helps you develop [Dataiku DSS](https://www.dataiku.com) Recipes and Plugins in Python
directly from PyCharm.

*Note: It has been developed for PyCharm, but also works on IDEA IntelliJ with the
Python plugin installed.*

## Features

This plugin allows developers to check out Recipes that have been created in
Dataiku DSS. Once a DSS Recipe has been opened in PyCharm, developers can edit
it, run it locally, debug it and finally upload it back to DSS.

Similarly this plugin allows developers to edit DSS plugins directly from
PyCharm. Developers can edit files, add new files or folders, delete existing
ones, run their code and finally upload it back to DSS.

## Installation

You can install the Dataiku DSS PyCharm Plugin directly from PyCharm Preferences.
Alternatively, you can download it from the [Jetbrains Plugin Repository](https://plugins.jetbrains.com/plugin/12511-dataiku-dss)
and manually install it.

## Configuration

To be able to open a Recipe or Plugins content in PyCharm, you must first declare and
configure a DSS instance.

You can do it either from the PyCharm Preferences, or by manually editing
`~/.dataiku/config.json` (`%USERPROFILE%/.dataiku/config.json` on Windows).
```
{
  "dss_instances": {
    "default": {
      "url": "http(s)://DSS_HOST:DSS_PORT/",
      "api_key": "Your API key secret",
      "no_check_certificate": false
    },
  },
  "default_instance": "default"
}
```

Alternatively, you can also specify the URL and API key to use via system
environment variables.

Read [Using the APIs outside of DSS](https://doc.dataiku.com/dss/latest/python-api/outside-usage.html#setting-up-the-connection-with-dss)
for more information. 

## Usage

### Editing a Recipe or a DSS Plugin
To edit a Recipe or Plugin that already exists on a DSS instance, open the
**File** menu and select **Open Dataiku DSS...**

### Synchronization with DSS instance

By default, all changes made to a Recipe or Plugin are automatically sent to
DSS when files are saved in PyCharm. Similarly PyCharm will poll the DSS
instance every 2 minutes for all and synchronizes your local copies with the
most up-to-date versions.

You can disable this automatic synchronization in the Preferences and configure
the polling interval: open the **PyCharm** menu, select **Preferences...** and
navigate to the **Dataiku DSS Settings** pane.

If automatic synchronization is disabled, or if you want to synchronize your
local copies with DSS immediately, you can manually trigger a synchronization. To do
so, open the **File** menu and select **Synchronize with DSS**.

## Contributing

### How to build
Building the plugin without running the tests and checks:

    ./gradlew buildPlugin
    
After build, you can find the plugin in the `build/distributions` directory.

To also run tests and checks:

    ./gradlew check buildPlugin

For the complete list of tasks, see:

    ./gradlew tasks

### How to develop in IntelliJ

Import the project as a Gradle project, or directly open the `dss-integration-pycharm.iml` *Project* file.

### How to release

    ./gradlew buildPlugin
    
You can find the newly generated jar in `build/libs`

## Copyright and License

Copyright 2013-2019 Dataiku SAS.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with the License. You may obtain a copy of the License in the LICENSE file, or at:

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. 
