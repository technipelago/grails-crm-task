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

import grails.plugins.crm.security.CrmAccount

/**
 * Search for tasks with pending alarm that are due now and trigger alarms.
 */
class CrmTaskAlarmJob {

    static triggers = {
        simple name: 'crmTaskAlarm', startDelay: 1000 * 60 * 3, repeatInterval: 1000 * 60 * 5 // every five minutes
    }

    def group = 'crmTask'

    def grailsApplication
    def crmTaskService

    def execute() {
        if(grailsApplication.config.crm.task.job.alarm.enabled) {
            def result = crmTaskService.findDueAlarms()
            if(result) {
                for (due in result) {
                    // select status from crm_account where id = (select account_id from crm_tenant where id = ?)
                    def crmAccount = CrmAccount.find("from CrmAccount as a where a = (select account from CrmTenant as t where t.id = ?)", [due.tenantId])
                    if(crmAccount?.active) {
                        crmTaskService.triggerAlarm(due)
                    }
                }
            } else if(log.isDebugEnabled()) {
                log.debug "${getClass().getName()}: no alarms to trigger at this time"
            }
        } else if(log.isDebugEnabled()) {
            log.debug "${getClass().getName()} is disabled because config [crm.task.job.alarm.enabled] is not true"
        }
    }
}
