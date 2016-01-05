/*
 * Copyright (c) 2015 Goran Ehrsson.
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

import grails.plugins.crm.task.CrmTask

class CrmTaskGrailsPlugin {
    def groupId = ""
    def version = "2.4.3"
    def grailsVersion = "2.2 > *"
    def dependsOn = [:]
    def loadAfter = ['crmTags']
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "grails-app/domain/test/TestEntity.groovy",
            "src/groovy/grails/plugins/crm/task/TestSecurityDelegate.groovy"
    ]
    def title = "Task Management Services for GR8 CRM"
    def author = "Goran Ehrsson"
    def authorEmail = "goran@technipelago.se"
    def description = "Provides task management services and domain classes for GR8 CRM applications. For task management user interface see plugin crm-task-ui."
    def documentation = "http://gr8crm.github.io/plugins/crm-task/"
    def license = "APACHE"
    def organization = [name: "Technipelago AB", url: "http://www.technipelago.se/"]
    def issueManagement = [system: "github", url: "https://github.com/technipelago/grails-crm-task/issues"]
    def scm = [url: "https://github.com/technipelago/grails-crm-task"]

    def features = {
        crmTask {
            description "Task Management"
            link controller: "crmTask", action: "index"
            permissions {
                guest "crmTask:index,list,show,createFavorite,deleteFavorite,clearQuery", "crmCalendar:index,events"
                partner "crmTask:index,list,show,createFavorite,deleteFavorite,clearQuery", "crmCalendar:index,events"
                user "crmTask,crmCalendar:*", "crmTaskAttender,crmTaskBooking:show"
                admin "crmTask,crmTaskCategory,crmTaskStatus,crmTaskType,crmCalendar:*", "crmTaskAttender,crmTaskBooking:*"
            }
            statistics { tenant ->
                def total = CrmTask.countByTenantId(tenant)
                def updated = CrmTask.countByTenantIdAndLastUpdatedGreaterThan(tenant, new Date() - 31)
                def usage
                if (total > 0) {
                    def tmp = updated / total
                    if (tmp < 0.1) {
                        usage = 'low'
                    } else if (tmp < 0.3) {
                        usage = 'medium'
                    } else {
                        usage = 'high'
                    }
                } else {
                    usage = 'none'
                }
                return [usage: usage, objects: total]
            }
        }
    }
}
