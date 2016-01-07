/*
 * Copyright (c) 2016 Goran Ehrsson.
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

import grails.plugins.crm.core.CrmEmbeddedContact

/**
 * Test the CrmTask domain class.
 */
class CrmTaskSpec extends grails.test.spock.IntegrationSpec {

    def crmTaskService

    def "test CrmTask dao"() {
        given:
        def type = crmTaskService.createTaskType(name: "Test", true)
        def task = new CrmTask(type: type, number: "42", name: "Test activity", startTime: new Date(), address: [address1: "The street", postalCode: "12345"]).save(failOnError: true)

        when:
        def dao = task.dao

        then:
        dao.number == "42"
        dao.name == "Test activity"
        dao.address.postalCode == "12345"
    }

    def "test CrmTaskBooking dao"() {
        given:
        def type = crmTaskService.createTaskType(name: "Test", true)
        def task = new CrmTask(type: type, number: "42", name: "Test activity", startTime: new Date(), address: [address1: "The street", postalCode: "12345"]).save(failOnError: true)
        def booking = new CrmTaskBooking(task: task, bookingRef: "Test").save(failOnError: true)

        when:
        def dao = booking.dao

        then:
        dao.bookingRef == "Test"
        dao.task == task.ident()
    }

    def "test CrmTaskAttender dao"() {
        given:
        def type = crmTaskService.createTaskType(name: "Test", true)
        def confirmed = crmTaskService.createAttenderStatus(name: "Confirmed", true)
        def task = new CrmTask(type: type, number: "42", name: "Test activity", startTime: new Date(), address: [address1: "The street", postalCode: "12345"]).save(failOnError: true)
        def booking = new CrmTaskBooking(task: task, bookingRef: "Test").save(failOnError: true)
        def contact = new CrmEmbeddedContact(firstName: "Joe", lastName: "Average")
        def attender = new CrmTaskAttender(booking: booking, status: confirmed, tmp: contact).save(failOnError: true)

        when:
        def dao = attender.dao

        then:
        dao.firstName == "Joe"
        dao.lastName == "Average"
        dao.task == task.ident()
        dao.booking == booking.ident()
    }
}
