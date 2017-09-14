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

package grails.plugins.crm.task

import grails.plugins.crm.core.CrmEmbeddedAddress
import grails.plugins.crm.contact.CrmContact
import org.apache.commons.lang.StringUtils

/**
 * A task booking can hold multiple attenders.
 */
class CrmTaskBooking {

    CrmContact contact // The company/contact responsible for the booking ("the customer").
    String bookingRef
    Date bookingDate
    Integer reserve
    String comments

    CrmEmbeddedAddress invoiceAddress

    static belongsTo = [task: CrmTask]

    static hasMany = [attenders: CrmTaskAttender]

    static embedded = ['invoiceAddress']

    static constraints = {
        contact(nullable: true)
        bookingRef(maxSize: 80, nullable: true)
        bookingDate()
        reserve(nullable: true)
        comments(maxSize: 2000, nullable: true, widget: 'textarea')
        invoiceAddress(nullable: true)
    }

    static mapping = {
        sort 'bookingDate': 'asc'
        attenders sort: 'bookingDate', 'asc'
    }

    static taggable = true
    static attachmentable = true

    public static final List BIND_WHITELIST = [
            'contact',
            'bookingRef',
            'bookingDate',
            'reserve',
            'comments',
            'invoiceAddress'
    ]

    def beforeValidate() {
        if (!bookingDate) {
            bookingDate = new Date()
        }
    }

    /**
     * Returns the of actual number of attenders for this booking.
     *
     * @return
     */
    transient Integer getAttenderCount() {
        attenders != null ? attenders.size() : 0
    }

    /**
     * Returns (depending on what is highest) the reserved or actual number of attenders for this booking.
     *
     * @return
     */
    transient Integer getEstimatedAttenderCount() {
        Math.max(getAttenderCount(), reserve ?: 0)
    }

    transient String getAttenderName() {
        final StringBuilder s = new StringBuilder()
        if (attenders) {
            final Iterator<CrmTaskAttender> itor = attenders.iterator()
            if (itor.hasNext()) {
                s << itor.next().toString()
            }
            if (itor.hasNext()) {
                s << '...'
            }
        }
        return s.toString()
    }

    transient String getTitle() {
        if (bookingRef) {
            return bookingRef
        }
        if (contact) {
            return contact.toString()
        }
        if(attenders) {
            return StringUtils.abbreviate(attenders.join(', '), 50)
        }
        return '#' + id
    }

    private static final List<String> DAO_PROPS = [
            'bookingRef',
            'bookingDate',
            'reserve',
            'comments'
    ]

    private Map<String, Object> getSelfProperties(List<String> props) {
        props.inject([:]) { m, i ->
            def v = this."$i"
            if (v != null) {
                m[i] = v
            }
            m
        }
    }

    transient Map<String, Object> getDao() {
        final Map<String, Object> map = getSelfProperties(DAO_PROPS)
        map.tenant = task.tenantId
        map.task = taskId
        map.invoiceAddress = invoiceAddress?.getDao() ?: [:]
        if (contact) {
            map.contact = contact.getDao(false)
        }
        map.tags = getTagValue()

        map
    }

    String toString() {
        getTitle() ?: getTask().toString()
    }
}
