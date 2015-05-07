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

import grails.plugins.crm.core.CrmContactInformation
import grails.plugins.crm.core.CrmEmbeddedContact
import grails.plugins.crm.contact.CrmContact

/**
 * A person attending a task/event.
 */
class CrmTaskAttender {
    // If contact is in our database, this is the attender.
    CrmContact contact
    boolean hide
    Date bookingDate
    String bookingRef
    String notes
    CrmTaskAttenderStatus status
    CrmEmbeddedContact tmp

    static belongsTo = [task: CrmTask]

    static embedded = ['tmp']

    static constraints = {
        contact(nullable: true)
        hide()
        bookingDate()
        bookingRef(maxSize: 80, nullable: true)
        notes(maxSize: 2000, nullable: true, widget: 'textarea')
        status()
        tmp(nullable: true)
    }

    static transients = ['contactInformation', 'dao']

    static taggable = true
    static attachmentable = true

    CrmContactInformation getContactInformation() {
        if (contact != null) {
            return contact
        }
        if (tmp == null) {
            tmp = new CrmEmbeddedContact()
        }
        tmp
    }

    void setContactInformation(CrmContactInformation contactInfo) {
        if (contactInfo == null) {
            contact = null
            tmp = null
        } else if (contactInfo instanceof CrmContact) {
            tmp = null
            contact = contactInfo
        } else {
            contact = null
            if (!tmp) {
                tmp = new CrmEmbeddedContact()
            }
            tmp.firstName = contactInfo.firstName
            tmp.lastName = contactInfo.lastName
            tmp.companyName = contactInfo.companyName
            tmp.title = contactInfo.title
            tmp.address = contactInfo.fullAddress
            tmp.telephone = contactInfo.telephone
            tmp.email = contactInfo.email
            tmp.number = contactInfo.number
        }
    }

    def beforeValidate() {
        if (!bookingDate) {
            bookingDate = new Date()
        }
    }

    String toString() {
        contactInformation.fullName.toString()
    }

    transient Map<String, Object> getDao() {
        final CrmContactInformation contact = getContactInformation()
        final Map<String, Object> map = contact.getDao()
        map.id = id
        if(task != null) {
            map.tenant = task.tenantId
            map.task = task.dao
            map.task.id = task.id
            if(! map.number) {
                map.number = task.guid
            }
        }
        if(! map.fullAddress) {
            map.fullAddress = contact.fullAddress
        }
        map.bookingDate = bookingDate
        map.bookingRef = bookingRef
        map.status = status?.param
        map.notes = notes
        map.tags = this.getTagValue()
        return map
    }
}
