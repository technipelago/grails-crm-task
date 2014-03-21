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

import grails.plugins.crm.contact.CrmContact
import grails.plugins.crm.core.CrmContactInformation
import grails.plugins.crm.core.CrmEmbeddedContact
import grails.plugins.crm.core.TenantUtils
import grails.plugins.crm.core.DateUtils
import grails.plugins.crm.core.SearchUtils
import groovy.time.Duration
import groovy.time.TimeDuration
import groovy.transform.CompileStatic
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod
import grails.events.Listener
import org.apache.commons.lang.StringUtils

/**
 * Task management service.
 */
class CrmTaskService {

    def grailsApplication
    def crmCoreService
    def crmSecurityService
    def crmTagService
    def messageSource

    def sessionFactory

    @Listener(namespace = "crmTask", topic = "enableFeature")
    def enableFeature(event) {
        // event = [feature: feature, tenant: tenant, role:role, expires:expires]
        Map tenant = crmSecurityService.getTenantInfo(event.tenant)
        Locale locale = tenant.locale ?: Locale.getDefault()
        CrmTaskService self = grailsApplication.mainContext.getBean('crmTaskService')

        TenantUtils.withTenant(tenant.id) {

            crmTagService.createTag(name: CrmTask.name, multiple: true)

            // Create default/example task types.
            self.createTaskType(orderIndex: 1, param: "admin", name: messageSource.getMessage("crmTaskType.name.admin", null, "Paper work", locale), true)
            self.createTaskType(orderIndex: 2, param: "meeting", name: messageSource.getMessage("crmTaskType.name.meeting", null, "Meeting", locale), true)
            self.createTaskType(orderIndex: 3, param: "telephone", name: messageSource.getMessage("crmTaskType.name.telephone", null, "Phone call", locale), true)
            self.createTaskType(orderIndex: 4, param: "email", name: messageSource.getMessage("crmTaskType.name.email", null, "Email", locale), true)
            self.createTaskType(orderIndex: 5, param: "alarm", name: messageSource.getMessage("crmTaskType.name.alarm", null, "Reminder", locale), true)

            // Create default attender statuses.
            self.createAttenderStatus(orderIndex: 1, param: "created", name: messageSource.getMessage("crmTaskAttenderStatus.name.created", null, "Created", locale), true)
            self.createAttenderStatus(orderIndex: 2, param: "confirmed", name: messageSource.getMessage("crmTaskAttenderStatus.name.confirmed", null, "Confirmed", locale), true)
            self.createAttenderStatus(orderIndex: 3, param: "attended", name: messageSource.getMessage("crmTaskAttenderStatus.name.attended", null, "Attended", locale), true)
            self.createAttenderStatus(orderIndex: 4, param: "cancelled", name: messageSource.getMessage("crmTaskAttenderStatus.name.cancelled", null, "Cancelled", locale), true)
            self.createAttenderStatus(orderIndex: 5, param: "absent", name: messageSource.getMessage("crmTaskAttenderStatus.name.absent", null, "Absent", locale), true)
        }
    }

    @Listener(namespace = "crmTenant", topic = "requestDelete")
    def requestDeleteTenant(event) {
        def tenant = event.id
        def count = 0
        count += CrmTaskType.countByTenantId(tenant)
        count += CrmTask.countByTenantId(tenant)
        count += CrmTaskAttenderStatus.countByTenantId(tenant)
        return count ? [namespace: 'crmTask', topic: 'deleteTenant'] : null
    }

    @Listener(namespace = "crmTask", topic = "deleteTenant")
    def deleteTenant(event) {
        def tenant = event.id
        def result = CrmTask.findAllByTenantId(tenant)
        result*.delete()
        CrmTaskType.findAllByTenantId(tenant)*.delete()
        CrmTaskAttenderStatus.findAllByTenantId(tenant)*.delete()
        log.warn("Deleted ${result.size()} tasks in tenant $tenant")
    }

    CrmTaskType getTaskType(String param) {
        CrmTaskType.findByParamAndTenantId(param, TenantUtils.tenant, [cache: true])
    }

    CrmTaskType createTaskType(Map params, boolean save = false) {
        if (!params.param) {
            params.param = StringUtils.abbreviate(params.name?.toLowerCase(), 20)
        }
        def tenant = TenantUtils.tenant
        def m = CrmTaskType.findByParamAndTenantId(params.param, tenant)
        if (!m) {
            m = new CrmTaskType()
            def args = [m, params, [include: CrmTaskType.BIND_WHITELIST]]
            new BindDynamicMethod().invoke(m, 'bind', args.toArray())
            m.tenantId = tenant
            if (params.enabled == null) {
                m.enabled = true
            }
            if (save) {
                m.save()
            } else {
                m.validate()
                m.clearErrors()
            }
        }
        return m
    }

    List<CrmTaskType> listTaskTypes() {
        CrmTaskType.findAllByEnabledAndTenantId(true, TenantUtils.tenant)
    }

    CrmTask getTask(Long id) {
        CrmTask.findByIdAndTenantId(id, TenantUtils.tenant, [cache: true])
    }

    CrmTask createTask(Map params, boolean save = false) {
        def task = new CrmTask()
        def args = [task, params, [include: CrmTask.BIND_WHITELIST]]
        new BindDynamicMethod().invoke(task, 'bind', args.toArray())
        task.tenantId = TenantUtils.tenant

        if (params.reference) {
            task.setReference(params.reference)
        }
        if (params.duration) {
            task.setDuration(params.duration)
        }
        if (save) {
            task.save()
        } else {
            task.validate()
            task.clearErrors()
        }

        return task
    }

    String deleteTask(CrmTask crmTask) {
        def tombstone = crmTask.toString()
        def id = crmTask.id
        def tenant = crmTask.tenantId
        def username = crmSecurityService.currentUser?.username
        event(for: "crmTask", topic: "delete", fork: false, data: [id: id, tenant: tenant, user: username, name: tombstone])
        crmTask.delete(flush:true)
        log.debug "Deleted task #$id in tenant $tenant \"${tombstone}\""
        event(for: "crmTask", topic: "deleted", data: [id: id, tenant: tenant, user: username, name: tombstone])
        return tombstone
    }

    CrmTaskAttenderStatus getAttenderStatus(String param) {
        CrmTaskAttenderStatus.findByParamAndTenantId(param, TenantUtils.tenant, [cache: true])
    }

    CrmTaskAttenderStatus createAttenderStatus(Map params, boolean save = false) {
        if (!params.param) {
            params.param = StringUtils.abbreviate(params.name?.toLowerCase(), 20)
        }
        def tenant = TenantUtils.tenant
        def m = CrmTaskAttenderStatus.findByParamAndTenantId(params.param, tenant)
        if (!m) {
            m = new CrmTaskAttenderStatus()
            def args = [m, params, [include: CrmTaskAttenderStatus.BIND_WHITELIST]]
            new BindDynamicMethod().invoke(m, 'bind', args.toArray())
            m.tenantId = tenant
            if (params.enabled == null) {
                m.enabled = true
            }
            if (save) {
                m.save()
            } else {
                m.validate()
                m.clearErrors()
            }
        }
        return m
    }

    List<CrmTaskAttenderStatus> listAttenderStatus() {
        CrmTaskAttenderStatus.findAllByEnabledAndTenantId(true, TenantUtils.tenant)
    }

    CrmTaskAttender addAttender(CrmTask task, CrmContactInformation contact, Object status = null, String notes = null) {
        if (contact == null) {
            throw new IllegalArgumentException("Cannot add null attender to task [$task]")
        }
        CrmTaskAttenderStatus attenderStatus
        if (status instanceof CrmTaskAttenderStatus) {
            attenderStatus = status
        } else if(status) {
            attenderStatus = getAttenderStatus(status.toString())
            if (!attenderStatus) {
                throw new IllegalArgumentException("[$status] is not a valid attender status for task [$task]")
            }
        } else {
            attenderStatus = listAttenderStatus().find{it}
        }
        def ta = new CrmTaskAttender(task: task, status: attenderStatus, notes: notes)
        ta.setContactInformation(contact)
        if (ta.validate()) {
            task.addToAttenders(ta)
        }
        return ta
    }

    List<CrmTaskAttender> findTasksAttended(CrmContact contact, Map params = [:]) {
        CrmTaskAttender.findAllByContact(contact, params)
    }

    // TODO Move this method to CrmContactService in plugin crm-contact.
    CrmContactInformation createContactInformation(Map<String, Object> values) {
        new CrmEmbeddedContact(values)
    }

    /**
     * Empty query = search all records.
     *
     * @param params pagination parameters
     * @return List of CrmTask domain instances
     */
    def list(Map params) {
        list([:], params)
    }

    /**
     * Find CrmTask instances filtered by query.
     *
     * @param query filter parameters
     * @param params pagination parameters
     * @return List of CrmTask domain instances
     */
    def list(Map query, Map params) {
        def tenant = TenantUtils.tenant
        def ids
        if(query.attender) {
            ids = CrmTaskAttender.createCriteria().list() {
                projections {
                    distinct('task.id')
                }
                task {
                    eq('tenantId', tenant)
                }
                contact {
                    ilike('name', SearchUtils.wildcard(query.attender))
                }
            } ?: [0L]
        }
        if(query.tags) {
            def tagged = crmTagService.findAllIdByTag(CrmTask, query.tags) ?: [0L]
            if(ids) {
                ids.retainAll(tagged)
            } else {
                ids = tagged
            }
            if(ids.isEmpty()) {
                ids = [0L]
            }
        }

        CrmTask.createCriteria().list(params) {
            eq('tenantId', tenant)
            if (query.id) {
                eq('id', Long.valueOf(query.id))
            }
            if (query.name) {
                ilike('name', SearchUtils.wildcard(query.name))
            }
            if (query.location) {
                ilike('location', SearchUtils.wildcard(query.location))
            }
            if (query.username) {
                eq('username', query.username)
            }
            if (query.type) {
                type {
                    ilike('name', SearchUtils.wildcard(query.type))
                }
            }
            if (query.priority) {
                eq('priority', Integer.valueOf(query.priority))
            }
            if (query.complete) {
                eq('complete', Integer.valueOf(query.complete))
            }
            if (query.reference) {
                eq('ref', crmCoreService.getReferenceIdentifier(query.reference))
            } else if (query.ref) {
                eq('ref', query.ref)
            }
            if (query.fromDate && query.toDate) {
                def timezone = query.timezone ?: TimeZone.getDefault()
                def d1 = query.fromDate instanceof Date ? query.fromDate : DateUtils.parseDate(query.fromDate, timezone)
                def d2 = query.toDate instanceof Date ? query.toDate : DateUtils.parseDate(query.toDate, timezone)
                or {
                    between('startTime', d1, d2)
                    between('endTime', d1, d2)
                }
            } else if (query.fromDate) {
                def timezone = query.timezone ?: TimeZone.getDefault()
                def d1 = DateUtils.parseDate(query.fromDate, timezone)
                or {
                    ge('startTime', d1)
                    gt('endTime', d1)
                }
            } else if (query.toDate) {
                def timezone = query.timezone ?: TimeZone.getDefault()
                def d2 = DateUtils.parseDate(query.toDate, timezone)
                or {
                    lt('startTime', d2)
                    le('endTime', d2)
                }
            }
            if(ids) {
                inList('id', ids)
            }
        }
    }

    @CompileStatic
    void setStatusPlanned(CrmTask crmTask) {
        crmTask.complete = CrmTask.STATUS_PLANNED
    }

    @CompileStatic
    void setStatusActive(CrmTask crmTask) {
        crmTask.complete = CrmTask.STATUS_ACTIVE
    }

    @CompileStatic
    void setStatusCompleted(CrmTask crmTask) {
        crmTask.complete = CrmTask.STATUS_COMPLETED
    }

    @CompileStatic
    Duration getTotalDuration(Collection<CrmTask> tasks) {
        Duration d = new TimeDuration(0, 0, 0, 0)
        for (task in tasks) {
            d = d + task.duration
        }
        normalize(d)
    }

    Duration normalize(Duration duration) {
        def newdmap = ['days', 'hours', 'minutes', 'seconds', 'millis'].collectEntries {
            [(it): duration."$it"]
        }.with { dmap ->
            [millis: 1000, seconds: 60, minutes: 60, hours: 24, days: -1].inject([dur: [days: 0, hours: 0, minutes: 0, seconds: 0, millis: 0], roll: 0]) { val, field ->
                val.dur."$field.key" = dmap."$field.key" + val.roll
                val.roll = val.dur."$field.key".intdiv(field.value)
                val.dur."$field.key" = field.value < 0 ?
                    val.dur."$field.key" :
                    val.dur."$field.key" % field.value
                val
            }.dur
        }
        new TimeDuration(newdmap.days, newdmap.hours, newdmap.minutes, newdmap.seconds, newdmap.millis)
    }

    /**
     * Create fixed date alarm.
     *
     * @param message alarm message
     * @param reference domain instance reference
     * @param date a fixed Date or null to trigger alarm immediately
     * @param hour start hour of alarm
     * @param start minute of alarm
     * @param duration duration in minutes for the alarm (slot size in calendar, not duration of alarm sound!)
     * @return the created alarm (CrmTask instance)
     */
    CrmTask createFixedAlarm(String message, Object reference, Date date = null, Integer hour = null, Integer minute = null, Integer duration = null) {
        def username = crmSecurityService.currentUser?.username
        def type = getTaskType("alarm") ?: createTaskType(param: "alarm", name: "Påminnelse", true)
        def startTime = date ? date.clone() : new Date()
        def cal = Calendar.getInstance()
        cal.setTime(startTime)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.SECOND, 0)
        if (hour != null) {
            cal.set(Calendar.HOUR_OF_DAY, hour)
            if (minute == null) {
                cal.set(Calendar.MINUTE, 0)
            }
        }
        if (minute != null) {
            cal.set(Calendar.MINUTE, minute)
        }

        startTime = cal.getTime()

        def crmTask = new CrmTask(type: type, name: StringUtils.abbreviate(message, CrmTask.constraints.name.maxSize), startTime: startTime, username: username,
                alarmType: CrmTask.ALARM_EMAIL) // TODO Other alarm types
        crmTask.setDuration(duration ?: 30)
        crmTask.setReference(reference)
        // If we create an alarm that was supposed to fire more than a month ago,
        // then we assume it's already completed one alarm.
        def overDue = new Date() - 31
        if (crmTask.startTime < overDue) {
            setStatusCompleted(crmTask)
            crmTask.alarms = 1
        }
        crmTask.save(failOnError: true)
    }

    /**
     * Create an alarm that depend on an external date property.
     *
     * @param message alarm message
     * @param reference domain instance reference
     * @param referenceProperty name of Date property in reference domain
     * @param offsetType offset type as specified by CrmTask.OFFSET_xxxxx
     * @param offset offset value, positive or negative
     * @param hidden true to hide task from calendar views
     * @return the created alarm (CrmTask instance)
     */
    CrmTask createDynamicAlarm(String message, Object reference, String referenceProperty, int offsetType = CrmTask.OFFSET_NONE, int offset = 0, boolean hidden = false) {
        def username = crmSecurityService.currentUser?.username
        def type = getTaskType("alarm") ?: createTaskType(param: "alarm", name: "Påminnelse", true)
        def crmTask = new CrmTask(type: type, name: StringUtils.abbreviate(message, CrmTask.constraints.name.maxSize),
                referenceProperty: referenceProperty, offsetType: offsetType, offset: offset, username: username,
                alarmType: CrmTask.ALARM_EMAIL, hidden: hidden)
        crmTask.setReference(reference)
        crmTask.validate() // date is set in beforeValidate()
        if (!crmTask.startTime) {
            throw new IllegalArgumentException("reference time not set for dynamic alarm \"$message\" [$reference]")
        }
        if (hasDuplicateDate(crmTask)) {
            crmTask.discard()
        } else {
            // If we create an alarm that was supposed to fire more than a month ago,
            // then we assume it's already completed one alarm.
            def overDue = new Date() - 31
            if (crmTask.startTime < overDue) {
                setStatusCompleted(crmTask)
                crmTask.alarms = 1
            }
            crmTask.save(failOnError: true)
        }
    }

    boolean hasDuplicateDate(CrmTask crmTask) {
        def date = crmTask.startTime
        CrmTask.createCriteria().list() {
            ne('id', crmTask.id) // exclude self
            eq('type', crmTask.type)
            eq('referenceProperty', crmTask.referenceProperty)
            eq('ref', crmTask.ref)
            eq('alarmType', crmTask.alarmType)
        }.find { isSameDay(it.startTime, date) }
    }

    @CompileStatic
    boolean isSameDay(Date date1, Date date2) {
        if (date1[Calendar.YEAR] != date2[Calendar.YEAR]) {
            return false
        }
        if (date1[Calendar.MONTH] != date2[Calendar.MONTH]) {
            return false
        }
        if (date1[Calendar.DAY_OF_MONTH] != date2[Calendar.DAY_OF_MONTH]) {
            return false
        }
        return true
    }

    def updateDynamicTasks(Object reference) {
        def rid = crmCoreService.getReferenceIdentifier(reference)
        def result = CrmTask.findAllByRefAndReferencePropertyIsNotNull(rid)

        result*.save() // Save will trigger validate() and CrmTask#beforeValidate() will update startTime

        sessionFactory.currentSession.flush()

        return result
    }

    def findDueAlarms() {
        def now = new Date()
        CrmTask.createCriteria().list() {
            ne('alarmType', CrmTask.ALARM_NONE)
            eq('alarms', 0)
            // Sanity check. It's silly to alarm about something due more than 30 days ago.
            between('alarmTime', now - 30, now)
        }
    }

    int triggerAlarm(CrmTask crmTask) {
        try {
            def recipients = []
            if (crmTask.username) {
                recipients << crmTask.username
            }
            event(for: "crmTask", topic: "alarm", fork: false,
                    data: [tenant: crmTask.tenantId, reference: crmTask.ref, id: crmTask.id, recipients: recipients])
        } catch (Exception e) {
            log.error("Task alarm failed for task with id [${crmTask.id}]", e)
        }
        crmTask.discard()
        crmTask = CrmTask.lock(crmTask.id)
        crmTask.alarms = crmTask.alarms + 1
        crmTask.save(flush: true)
        // TODO snooze a day or two?
        crmTask.alarms
    }
}
