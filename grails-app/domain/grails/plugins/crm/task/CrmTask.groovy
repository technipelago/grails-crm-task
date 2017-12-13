/*
 * Copyright (c) 2014 Goran Ehrsson.
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

import grails.plugins.crm.core.*
import groovy.time.Duration
import groovy.time.TimeCategory
import groovy.time.TimeDuration
import org.joda.time.DateTime

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
    public static final int ALARM_RESERVED_1 = 3
    public static final int ALARM_RESERVED_2 = 4
    public static final int ALARM_RESERVED_3 = 5
    public static final int ALARM_CUSTOM_1 = 10
    public static final int ALARM_CUSTOM_2 = 11
    public static final int ALARM_CUSTOM_3 = 12

    private def _crmCoreService

    String number

    Date startTime
    Date endTime
    Date alarmTime
    boolean busy = false
    boolean hidden = false

    String displayDate
    String scope

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

    static hasMany = [recurDaysOfWeek: Integer, excludeDays: java.sql.Date, bookings: CrmTaskBooking]

    static embedded = ['address']

    static constraints = {
        number(maxSize: 20, nullable: true, validator: { val, obj ->
            if (val) {
                def tenant = obj.tenantId ?: TenantUtils.tenant
                withNewSession {
                    CrmTask.createCriteria().count() {
                        eq('tenantId', tenant)
                        eq('number', val)
                        if (obj.id) {
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
        scope(maxSize: 40, nullable: true)
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
        alarmType(inList: [ALARM_NONE, ALARM_EMAIL, ALARM_SMS, ALARM_RESERVED_1, ALARM_RESERVED_2, ALARM_RESERVED_3, ALARM_CUSTOM_1, ALARM_CUSTOM_2, ALARM_CUSTOM_3])
        alarmOffset()
        alarms(min: 0)
    }

    static mapping = {
        sort 'startTime': 'desc'
        startTime index: 'crm_task_start_idx'
        endTime index: 'crm_task_end_idx'
        alarmTime index: 'crm_task_alarm_idx'
        username index: 'crm_task_user_idx'
        ref index: 'crm_task_ref_idx'
        bookings sort: 'bookingDate', 'asc'
    }

    static transients = ['date', 'duration', 'reference']

    static searchable = {
        name boost: 1.5
    }

    public static final List BIND_WHITELIST = [
            'number',
            'startTime',
            'endTime',
            'alarmTime',
            'displayDate',
            'scope',
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
            synchronized (this) {
                if (_crmCoreService == null) {
                    _crmCoreService = this.getDomainClass().getGrailsApplication().getMainContext().getBean('crmCoreService')
                }
            }
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

        if (!name && type) {
            name = type.name
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
    transient Date getDate() {
        startTime ? new Date(startTime.time) : null
    }

    void setDate(Date arg) {
        startTime = arg ? new Date(arg.time) : null
    }

    transient List<Date> getDates() {
        def list = []
        if (startTime) {
            list << startTime
        }
        if (endTime) {
            list << endTime
        }
        return list
    }

    /**
     * Get start time and end time as a pair of Joda DateTime instances.
     *
     * @return a list with two elements [start, end] (elements can be null)
     */
    transient List<DateTime> getEventDates() {
        DateTime start
        DateTime end
        if (startTime) {
            start = new DateTime(startTime)
            end = start.plusMinutes(this.getDurationMinutes() ?: 30)
        } else if (endTime) {
            end = new DateTime(endTime)
            start = end.minusMinutes(this.getDurationMinutes() ?: 30)
        }
        return [start, end]
    }

    transient List<Date> getDateRange() {
        if (startTime && endTime) {
            return startTime..endTime
        } else if (startTime) {
            return [startTime]
        } else if (endTime) {
            return [endTime]
        }
        return []
    }

    void setReference(object) {
        ref = object ? crmCoreService.getReferenceIdentifier(object) : null
    }

    Object getReference() {
        ref ? crmCoreService.getReference(ref) : null
    }

    /**
     * If this task is directly associated with a CrmContact then it is returned.
     * Otherwise if there is only one attender it is returned.
     * If task has multiple attenders then null is returned.
     *
     * @return
     */
    transient CrmContactInformation getContact() {
        if (ref?.startsWith('crmContact@')) {
            return getReference()
        }
        def list = getAttenders()
        if (list.size() == 1) {
            return list.get(0).getContactInformation()
        }
        return null // Multiple attenders, we can't just return a random one.
    }

    /**
     * Return a list of CrmTaskAttender instances associated with this task.
     *
     * @return if no attenders are found an empty List is returned.
     */
    transient List<CrmTaskAttender> getAttenders() {
        ident() ? CrmTaskAttender.createCriteria().list([sort: 'bookingDate', order: 'asc']) {
            booking {
                eq('task', this)
            }
        } : Collections.EMPTY_LIST
    }

    transient Duration getDuration() {
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

    transient void setDuration(Duration d) {
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

    transient void setDuration(int minutes) {
        def d = new TimeDuration(0, minutes, 0, 0)
        use(groovy.time.TimeCategory) {
            if (startTime) {
                endTime = startTime + d
            } else if (endTime) {
                startTime = endTime - d
            }
        }
    }

    transient int getDurationMinutes() {
        getDuration().toMilliseconds() / 60000
    }

    transient Date getReferenceDate() {
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

    transient boolean isActive() {
        complete == STATUS_ACTIVE
    }

    transient boolean isCompleted() {
        complete == STATUS_COMPLETED
    }

    transient boolean isAlarm() {
        alarmType != ALARM_NONE
    }

    private Map<String, Object> getSelfProperties(List<String> props) {
        props.inject([:]) { m, i ->
            def v = this."$i"
            if (v != null) {
                m[i] = v
            }
            m
        }
    }

    private static final List<String> DAO_PROPS = [
            'number',
            'startTime',
            'endTime',
            'alarmTime',
            'displayDate',
            'scope',
            'busy',
            'hidden',
            'alarmType',
            'username',
            'name',
            'description',
            'location',
            'priority',
            'complete'
    ]

    transient Map<String, Object> getDao() {
        final Map<String, Object> map = getSelfProperties(DAO_PROPS)
        map.tenant = tenantId
        map.address = address?.getDao() ?: [:]
        map.type = type?.getDao() ?: [:]
        return map
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
