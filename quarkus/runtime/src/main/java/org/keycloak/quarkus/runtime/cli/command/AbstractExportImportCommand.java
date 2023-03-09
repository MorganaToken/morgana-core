/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.quarkus.runtime.cli.command;

import org.keycloak.quarkus.runtime.Environment;

import picocli.CommandLine.Option;

import static org.keycloak.exportimport.ExportImportConfig.ACTION_EXPORT;
import static org.keycloak.exportimport.ExportImportConfig.ACTION_IMPORT;

public abstract class AbstractExportImportCommand extends AbstractStartCommand implements Runnable {

    private final String action;

    @Option(names = "--dir",
            arity = "1",
            description = "Set the path to a directory where files will be read from when importing or created with the exported data.",
            paramLabel = "<path>")
    String toDir;

    @Option(names = "--file",
            arity = "1",
            description = "Set the path to a file that will be read when importing or created with the exported data.",
            paramLabel = "<path>")
    String toFile;

    protected AbstractExportImportCommand(String action) {
        this.action = action;
    }

    @Override
    public void run() {
        System.setProperty("keycloak.migration.action", action);

        if (action.equals(ACTION_IMPORT)) {
            if (toDir != null) {
                System.setProperty("keycloak.migration.provider", "dir");
                System.setProperty("keycloak.migration.dir", toDir);
            } else if (toFile != null) {
                System.setProperty("keycloak.migration.provider", "singleFile");
                System.setProperty("keycloak.migration.file", toFile);
            } else {
                executionError(spec.commandLine(), "Must specify either --dir or --file options.");
            }
        } else if (action.equals(ACTION_EXPORT)) {
            // for export, the properties are no longer needed and will be passed by the property mappers
            if (toDir == null && toFile == null) {
                executionError(spec.commandLine(), "Must specify either --dir or --file options.");
            }
        }

        Environment.setProfile(Environment.IMPORT_EXPORT_MODE);

        super.run();
    }
}
