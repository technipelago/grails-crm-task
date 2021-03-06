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

import grails.plugins.crm.contact.CrmContact
import grails.plugins.crm.core.CrmContactInformation
import grails.plugins.crm.core.CrmEmbeddedContact

/**
 * A person attending a task/event.
 */
class CrmTaskAttender {

    boolean hide
    Date bookingDate
    String bookingRef
    String externalRef
    String source
    String food
    String notes
    String bio
    CrmTaskAttenderStatus status
    CrmContact contact // If contact is in our database, this is the attender.
    CrmEmbeddedContact tmp // Embedded contact information if 'contact' is not used.

    static belongsTo = [booking: CrmTaskBooking]

    static embedded = ['tmp']

    static constraints = {
        contact(nullable: true)
        hide()
        bookingDate()
        bookingRef(maxSize: 128, nullable: true)
        externalRef(maxSize: 128, nullable: true)
        source(maxSize: 128, nullable: true)
        food(maxSize: 255, nullable: true)
        notes(maxSize: 4000, nullable: true, widget: 'textarea')
        bio(maxSize: 4000, nullable: true, widget: 'textarea')
        status()
        tmp(nullable: true)
    }

    static transients = ['virtualId', 'contactInformation', 'description']

    static taggable = true
    static attachmentable = true

    public static final List BIND_WHITELIST = [
            'hide',
            'bookingDate',
            'bookingRef',
            'externalRef',
            'source',
            'food',
            'notes',
            'bio',
            'status'
    ]

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
            tmp = new CrmEmbeddedContact(contactInfo)
        }
    }

    void setDescription(String arg) {
        this.@notes = arg
    }

    String getDescription() {
        this.@notes
    }

    def beforeValidate() {
        if (!bookingDate) {
            bookingDate = new Date()
        }
    }

    String toString() {
        contactInformation.fullName.toString()
    }

    transient String getVirtualId() {
        if(contactId) {
            return contactId.toString()
        }
        if(tmp?.number) {
            return tmp.number
        }
        if(tmp?.email) {
            return tmp.email
        }
        if(tmp?.telephone) {
            return tmp.telephone
        }
        if(id) {
            return id.toString()
        }
        return hashCode().toString()
    }

    transient Map<String, Object> getDao() {
        final CrmContactInformation contact = getContactInformation()
        final Map<String, Object> map = contact.getDao() // TODO no interface, code smell.
        map.id = id
        map.booking = bookingId
        map.task = booking.taskId
        map.tenant = booking.task.tenantId
        map.bookingDate = bookingDate
        map.bookingRef = bookingRef
        map.externalRef = externalRef
        map.source = source
        map.status = status?.param
        map.food = food
        map.notes = getDescription()
        map.bio = bio
        map.tags = getTagValue()

        map
    }
}
