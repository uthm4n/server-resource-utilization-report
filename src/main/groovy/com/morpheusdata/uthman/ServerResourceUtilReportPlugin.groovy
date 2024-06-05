/*
* Copyright 2022 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.morpheusdata.uthman

import com.morpheusdata.core.Plugin
import com.morpheusdata.model.OptionType
import com.morpheusdata.uthman.datasets.DateTimeDatasetProvider
import com.morpheusdata.uthman.datasets.ElasticQueryDatasetProvider

class ServerResourceUtilReportPlugin extends Plugin {

    @Override
    String getCode() {
        return 'server-resource-utilization-report-plugin'
    }

    @Override
    void initialize() {
        this.setName("Server Resource Utilization Report Plugin")
        this.registerProvider(new ServerResourceUtilReportProvider(this,this.morpheus))
        this.registerProvider(new DateTimeDatasetProvider(this, this.morpheus))
        this.registerProvider(new ElasticQueryDatasetProvider(this, this.morpheus))

        def optionTypes = this.getSettings()
        optionTypes << new OptionType (
                name: 'Appliance URL',
                code: 'sru-report-appliance-url',
                fieldContext: 'config',
                fieldName: 'applianceUrl',
                displayOrder: 1,
                fieldLabel: 'Morpheus appliance URL:',
                helpText: 'Enter your Morpheus appliance URL e.g. https://ueqbal-test-appliance.com',
                required: true,
                inputType: OptionType.InputType.TEXT,
        )
    }

    @Override
    void onDestroy() {
    }
}
