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

import grails.plugins.crm.core.TenantUtils
import groovy.time.Duration
import groovy.time.TimeDuration
import test.TestEntity

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests for CrmTaskService.
 */
class CrmTaskServiceSpec extends grails.test.spock.IntegrationSpec {

    def grailsApplication
    def crmTaskService
    def grailsEventsRegistry
    def crmAccountService
    def crmSecurityService

    def "create task type"() {
        when:
        def t1 = crmTaskService.createTaskType(name: "Email", description: "Read or write email")

        then:
        t1.id == null
        t1.name == "Email"

        when:
        t1.save(flush: true, failOnError: true)

        then:
        t1.id != null
        t1.orderIndex == 1
        t1.enabled
    }

    def "create simple task"() {
        given:
        def now = new Date()
        def then = use(groovy.time.TimeCategory) { now + 60.minutes }
        def t = crmTaskService.createTaskType(name: "Test", true)

        when:
        def task = crmTaskService.createTask(name: "Test").with {
            startTime = now
            endTime = then
            type = t
            save(failOnError: true)
        }

        then:
        task.id != null
        task.durationMinutes == 60
        task.duration.hours == 1
        task.duration.minutes == 0
    }

    def "find by number"() {
        given:
        def t = crmTaskService.createTaskType(name: "Test", true)

        when:
        crmTaskService.createTask(number: 42, name: "Test", type: t, startTime: new Date(), duration: 60, true)

        then:
        crmTaskService.findByNumber("42")
    }

    def "create fixed alarm"() {
        given:
        def cal = Calendar.getInstance()
        cal.clearTime()
        cal.set(Calendar.HOUR_OF_DAY, 12)
        def test = new TestEntity(name: "Test", date: cal.getTime()).save(failOnError: true, flush: true)
        def alarm = crmTaskService.createFixedAlarm("Wake up!", test, new Date() + 1, 9)
        def date = alarm.date

        when: "change date of reference to next week"
        test.date = test.date + 7
        test.save()
        crmTaskService.updateDynamicTasks(test)
        alarm.refresh()

        then:
        alarm.date == date
        alarm.alarmTime == date
    }

    def "create dynamic alarm"() {
        given:
        def cal = Calendar.getInstance()
        cal.clearTime()
        cal.set(Calendar.HOUR_OF_DAY, 12)
        def test = new TestEntity(name: "Test", date: cal.getTime()).save(failOnError: true, flush: true)
        def alarm = crmTaskService.createDynamicAlarm("Check this out!", test, 'date', CrmTask.OFFSET_NONE, 0)
        def date = alarm.date

        when: "change date of reference to next week"
        test.date = test.date + 7
        test.save()
        crmTaskService.updateDynamicTasks(test)
        alarm.refresh()

        then: "date should be updated to new reference date"
        alarm.date != date
        alarm.date == test.date
        alarm.alarmTime == test.date

        when: "offset is set to +6 hours"
        date = alarm.date
        alarm.offsetType = CrmTask.OFFSET_HOURS
        alarm.offset = 6
        alarm.save()

        then: "it should be same day as the reference date, but hour should be 18"
        alarm.date[Calendar.YEAR] == date[Calendar.YEAR]
        alarm.date[Calendar.MONTH] == date[Calendar.MONTH]
        alarm.date[Calendar.DAY_OF_MONTH] == date[Calendar.DAY_OF_MONTH]
        alarm.date[Calendar.HOUR_OF_DAY] == 18
        alarm.date[Calendar.MINUTE] == 0
        alarm.date[Calendar.SECOND] == 0
    }

    def "test alarm job"() {
        given:
        def latch = new CountDownLatch(1)
        grailsEventsRegistry.on("crmTask", "alarm") { event ->
            latch.countDown()
        }
        def user = crmSecurityService.createUser([username: "alarmtester", name: "Test User", email: "test@test.com", password: "test123", enabled: true])
        def alarm
        crmSecurityService.runAs(user.username) {
            def account = crmAccountService.createAccount([status: 'active'])
            def tenant = crmSecurityService.createTenant(account, "Alarms")
            alarm = TenantUtils.withTenant(tenant.id) { crmTaskService.createFixedAlarm("Alarm!", null) }
        }
        grailsApplication.config.crm.task.job.alarm.enabled = true
        def job = new CrmTaskAlarmJob()
        job.crmTaskService = crmTaskService
        job.grailsApplication = grailsApplication

        when:
        job.execute()

        then:
        latch.await(5L, TimeUnit.SECONDS)
        latch.getCount() == 0
    }

    def "test task duration"() {
        given:
        def type = crmTaskService.createTaskType(name: "Coding", description: "Coding related activity", true)

        when:
        def task1 = crmTaskService.createTask(type: type, username: "me", name: "Implemented duration calculation",
                endTime: new Date(), duration: 90, true)
        def duration = crmTaskService.getTotalDuration([task1])

        then:
        duration.hours == 1
        duration.minutes == 30

        when:
        def task2 = crmTaskService.createTask(type: type, username: "me", name: "Added some integration tests",
                endTime: new Date(), duration: 45, true)
        duration = crmTaskService.getTotalDuration([task1, task2])

        then:
        duration.hours == 2
        duration.minutes == 15


        when:
        def task3 = crmTaskService.createTask(type: type, username: "me", name: "Debugged some hairy piece of framework code",
                endTime: new Date(), duration: 1450, true)
        duration = crmTaskService.getTotalDuration([task1, task2, task3])

        then:
        duration.days == 1
        duration.hours == 2
        duration.minutes == 25
    }

}
