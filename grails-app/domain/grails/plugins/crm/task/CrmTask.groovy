/*
 * Copyright (c) 2013 Goran Ehrsson.
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

import grails.plugins.crm.core.CrmEmbeddedAddress
import grails.plugins.crm.core.CrmContactInformation
import grails.plugins.crm.core.TenantEntity
import grails.plugins.crm.core.AuditEntity
import grails.plugins.crm.core.TenantUtils
import grails.plugins.crm.core.UuidEntity
import groovy.time.Duration
import groovy.time.TimeCategory
import groovy.time.TimeDuration

/**
 * A calendar task.
 */
@TenantEntity
@AuditEntity
@UuidEntity
class CrmTask {

    public static final int PRIORITY_LOWEST = 0
    public static final int PRIORITY_LOW = 20
    public static final int PRIORITY_NORMAL = 40
    public static final int PRIORITY_HIGH = 60
    public static final int PRIORITY_HIGHER = 80
    public static final int PRIORITY_HIGHEST = 100

    public static final int STATUS_PLANNED = 0
    public static final int STATUS_ACTIVE = 50
    public static final int STATUS_COMPLETED = 100

    public static final int OFFSET_NONE = 0
    public static final int OFFSET_MINUTES = 1
    public static final int OFFSET_HOURS = 2
    public static final int OFFSET_DAYS = 3
    public static final int OFFSET_WEEKS = 4
    public static final int OFFSET_MONTHS = 5
    public static final int OFFSET_YEARS = 6

    public static final int ALARM_NONE = 0
    public static final int ALARM_EMAIL = 1
    public static final int ALARM_SMS = 2

    private def _crmCoreService

    String number

    Date startTime
    Date endTime
    Date alarmTime
    boolean busy = false
    boolean hidden = false

    String displayDate

    String referenceProperty
    int offsetType = OFFSET_NONE
    int offset = 0

    int alarms = 0
    int alarmType = ALARM_NONE
    int alarmOffset = 0 // minutes

    // Recurring Options
    boolean isRecurring = false
    TaskRecurType recurType
    Integer recurInterval = 1
    java.sql.Date recurUntil
    Integer recurCount

    // Back link to original recurring task this task was created from
    CrmTask sourceTask

    String username
    String name
    String description
    String location
    Integer priority = PRIORITY_NORMAL
    Integer complete = 0
    CrmTaskType type
    String ref // entityName@id

    CrmEmbeddedAddress address

    static hasMany = [recurDaysOfWeek: Integer, excludeDays: java.sql.Date, attenders: CrmTaskAttender]

    static embedded = ['address']

    static constraints = {
        number(maxSize: 20, nullable:true, validator: {val, obj->
            if(val) {
                def tenant = obj.tenantId ?: TenantUtils.tenant
                withNewSession {
                    CrmTask.createCriteria().count() {
                        eq('tenantId', tenant)
                        eq('number', val)
                        if(obj.id) {
                            ne('id', obj.id)
                        }
                    }
                } ? 'not.unique' : null
            }
            null
        })
        startTime(nullable: false)
        endTime(nullable: false, validator: { val, obj -> val >= obj.startTime })
        alarmTime(nullable: true)
        displayDate(maxSize: 40, nullable: true)
        username(maxSize: 80, nullable: true)
        name(maxSize: 80, blank: false)
        location(maxSize: 80, nullable: true)
        address(nullable: true)

        description(nullable: true, maxSize: 2000, widget: 'textarea')
        priority(min: PRIORITY_LOWEST, max: PRIORITY_HIGHEST)
        complete(min: STATUS_PLANNED, max: STATUS_COMPLETED)

        recurType(nullable: true)
        recurInterval(nullable: true)
        recurUntil(nullable: true)
        recurCount(nullable: true)
        sourceTask(nullable: true)
        recurDaysOfWeek(validator: { val, obj ->
            if (obj.recurType == TaskRecurType.WEEKLY && !val) {
                return 'null'
            }
        })
        ref(maxSize: 80, nullable: true)
        referenceProperty(maxSize: 40, nullable: true)
        offsetType(inList: [OFFSET_NONE, OFFSET_MINUTES, OFFSET_HOURS, OFFSET_DAYS, OFFSET_WEEKS, OFFSET_MONTHS, OFFSET_YEARS])
        offset()
        alarmType(inList: [ALARM_NONE, ALARM_EMAIL, ALARM_SMS])
        alarmOffset()
        alarms(min: 0)
    }

    static mapping = {
        sort 'startTime': 'desc'
        startTime index: 'crm_task_start_idx'
        endTime index: 'crm_task_end_idx'
        alarmTime index: 'crm_task_alarm_idx'
        username index: 'crm_task_user_idx'
        attenders sort: 'bookingDate', 'asc'
    }

    static transients = ['date', 'dates', 'duration', 'durationMinutes', 'completed',
            'reference', 'contact', 'referenceDate', 'targetDate', 'alarm']

    static searchable = {
        name boost: 1.5
    }

    public static final List BIND_WHITELIST = [
            'number',
            'startTime',
            'endTime',
            'alarmTime',
            'displayDate',
            'busy',
            'hidden',
            'referenceProperty',
            'offsetType',
            'offset',
            'alarmType',
            'alarmOffset',
            'isRecurring',
            'recurType',
            'recurInterval',
            'recurUntil',
            'recurCount',
            'sourceTask',
            'username',
            'name',
            'description',
            'location',
            'priority',
            'complete',
            'type',
            'address'
    ].asImmutable()

    static taggable = true
    static attachmentable = true
    static dynamicProperties = true

    // Lazy injection of service.
    private def getCrmCoreService() {
        if (_crmCoreService == null) {
            _crmCoreService = this.getDomainClass().getGrailsApplication().getMainContext().getBean('crmCoreService')
        }
        _crmCoreService
    }

    def beforeValidate() {
        def rd = getReferenceDate()
        if (rd) {
            // Don't update the dynamic date if an alarm has been triggered for this task.
            if (alarms < 1) {
                def m = Math.min(1440, getDurationMinutes() ?: 30) // save current duration. TODO 30 minutes hard coded!
                startTime = getTargetDate(rd, 8) // TODO relative time set to 8am hard coded!
                setDuration(m) // this call will set endTime
            }
        } else if (startTime && !endTime) {
            setDuration(30)
        }

        if (alarmType != ALARM_NONE && startTime != null) {
            if (alarmOffset != 0) {
                use(TimeCategory) {
                    alarmTime = startTime - new TimeDuration(0, alarmOffset, 0, 0)
                }
            } else {
                alarmTime = startTime
            }
        }
    }

    def beforeDelete() {
        // Remove all child tasks
        CrmTask.withNewSession {
            CrmTask.findAllBySourceTask(this)*.delete()
        }
    }

    /**
     * Return the task start time. This is just an an alias for the 'startTime' property.
     * @return task start time
     */
    Date getDate() {
        startTime ? new Date(startTime.time) : null
    }

    void setDate(Date arg) {
        startTime = arg ? new Date(arg.time) : null
    }

    List<Date> getDates() {
        def list = []
        if(startTime) {
            list << startTime
        }
        if(endTime) {
            list << endTime
        }
        return list
    }

    void setReference(object) {
        ref = object ? crmCoreService.getReferenceIdentifier(object) : null
    }

    Object getReference() {
        ref ? crmCoreService.getReference(ref) : null
    }

    transient CrmContactInformation getContact() {
        attenders?.find{it}?.getContactInformation()
    }

    Duration getDuration() {
        Duration dur
        if (startTime && endTime) {
            use(TimeCategory) {
                dur = endTime - startTime
            }
        } else {
            dur = new TimeDuration(0, 0, 0, 0)
        }

        return dur
    }

    void setDuration(Duration d) {
        if (startTime) {
            use(groovy.time.TimeCategory) {
                endTime = startTime + d
            }
        } else if (endTime) {
            use(groovy.time.TimeCategory) {
                startTime = endTime - d
            }
        }
    }

    void setDuration(int minutes) {
        def d = new TimeDuration(0, minutes, 0, 0)
        use(groovy.time.TimeCategory) {
            if (startTime) {
                endTime = startTime + d
            } else if (endTime) {
                startTime = endTime - d
            }
        }
    }

    int getDurationMinutes() {
        getDuration().toMilliseconds() / 60000
    }

    Date getReferenceDate() {
        referenceProperty ? getReference()?."$referenceProperty" : null
    }

    Date getTargetDate(Date ref, Integer hour = null) {
        if (!ref) {
            return ref
        }
        def target
        switch (offsetType) {
            case OFFSET_NONE:
                target = ref
                break
            case OFFSET_MINUTES:
                target = new TimeDuration(0, offset, 0, 0) + ref
                break
            case OFFSET_HOURS:
                target = new TimeDuration(offset, 0, 0, 0) + ref
                break
            case OFFSET_DAYS:
                def cal = Calendar.getInstance()
                cal.setTime(ref)
                cal.add(Calendar.DAY_OF_MONTH, offset)
                if (hour != null) {
                    cal.clearTime()
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                }
                target = cal.getTime()
                break
            case OFFSET_WEEKS:
                def cal = Calendar.getInstance()
                cal.setTime(ref)
                cal.add(Calendar.DAY_OF_MONTH, offset * 7)
                if (hour != null) {
                    cal.clearTime()
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                }
                target = cal.getTime()
                break
            case OFFSET_MONTHS:
                def cal = Calendar.getInstance()
                cal.setTime(ref)
                cal.add(Calendar.MONTH, offset)
                if (hour != null) {
                    cal.clearTime()
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                }
                target = cal.getTime()
                break
            case OFFSET_YEARS:
                def cal = Calendar.getInstance()
                cal.setTime(ref)
                cal.add(Calendar.YEAR, offset)
                if (hour != null) {
                    cal.clearTime()
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                }
                target = cal.getTime()
                break
        }
        return target
    }

    boolean isCompleted() {
        complete == STATUS_COMPLETED
    }

    boolean isAlarm() {
        alarmType != ALARM_NONE
    }

    String toString() {
        name.toString()
    }

}

public enum TaskRecurType {
    DAILY('daily'),
    WEEKLY('weekly'),
    MONTHLY('monthly'),
    YEARLY('yearly')

    String name

    TaskRecurType(String name) {
        this.name = name
    }
}
