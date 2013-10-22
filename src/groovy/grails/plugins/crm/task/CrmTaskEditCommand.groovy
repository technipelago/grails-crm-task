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

import grails.validation.Validateable
import grails.plugins.crm.core.TenantUtils

/**
 * Command object used by CrmTaskController create/edit.
 */
@Validateable
class CrmTaskEditCommand  implements Serializable {

    Long id
    Integer version
    String name
    String location
    String description
    String username

    Date startTime
    Date endTime

    CrmTaskType type

    Float complete
    Integer priority

    Map metadata = [:]

    static constraints = {
        importFrom CrmTask
    }

    CrmTaskEditCommand() {}

    CrmTaskEditCommand(CrmTask crmTask) {
        this.id = crmTask.id
        this.version = crmTask.version
        this.name = crmTask.name
        this.description = crmTask.description
        this.location = crmTask.location
        this.username = crmTask.username

        this.startTime = crmTask.startTime
        this.endTime = crmTask.endTime

        this.type = crmTask.type

        this.priority = crmTask.priority
        this.complete = crmTask.complete
    }

    def beforeView() {
        def tenant = TenantUtils.tenant

        metadata.typeList = CrmTaskType.findAllByEnabledAndTenantId(true, tenant)
        addMissing(CrmTaskType, type, metadata.typeList)
    }

    private boolean addMissing(Class clazz, Object m, List list) {
        if (m && !list.find {it.id == m.id}) {
            list << m
            return true
        }
        return false
    }

    def beforeValidate() {

    }

    CrmTask bindProperties(CrmTask crmTask = null) {

        this.beforeValidate()

        if (!crmTask) {
            if(this.id) {
                crmTask = CrmTask.get(this.id)
            }
            if(! crmTask) {
                crmTask = new CrmTask()
            }
        }

        crmTask.name = this.name
        crmTask.description = this.description
        crmTask.location = this.location
        crmTask.username = this.username

        crmTask.startTime = this.startTime
        crmTask.endTime = this.endTime

        crmTask.type = this.type

        crmTask.priority = this.priority
        crmTask.complete = this.complete

        return crmTask
    }

    String toString() {
        name
    }
}
