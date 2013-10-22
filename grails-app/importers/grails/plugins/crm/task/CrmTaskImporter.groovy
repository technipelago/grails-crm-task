/*
 * Copyright (c) 2012 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugins.crm.task

/**
 * Importer implementation that import CrmTask instances.
 */
//@CrmImport(CrmTask)
class CrmTaskImporter {

    def grailsApplication

    def parse(task, data) {
        def crmTask
        if (task.matchCriteria != null) {
            crmTask = match(data, task.matchCriteria)
        }
        if (!crmTask) {
            crmTask = new CrmTask()
        } else if (!task.matchParams?.update) {
            return
        }
        bindData(crmTask, data)
        crmTask.save(failOnError: true)
    }

}
