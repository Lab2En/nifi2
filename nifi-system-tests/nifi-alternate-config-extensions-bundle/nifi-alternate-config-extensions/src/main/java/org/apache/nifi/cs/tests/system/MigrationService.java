/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nifi.cs.tests.system;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.migration.PropertyConfiguration;
import org.apache.nifi.processor.util.StandardValidators;

import java.util.List;

public class MigrationService extends AbstractControllerService implements ControllerService {

    static final PropertyDescriptor START_VALUE = new PropertyDescriptor.Builder()
            .name("Initial Value")
            .description("The value to start counting from")
            .required(true)
            .addValidator(StandardValidators.LONG_VALIDATOR)
            .defaultValue("0")
            .build();

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return List.of(START_VALUE);
    }

    @Override
    public void migrateProperties(final PropertyConfiguration config) {
        config.removeProperty("Dependent Service");
        config.renameProperty("Start", START_VALUE.getName());
    }
}