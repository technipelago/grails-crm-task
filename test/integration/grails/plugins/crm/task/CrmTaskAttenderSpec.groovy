package grails.plugins.crm.task

/**
 * Test CrmTaskAttender features.
 */
class CrmTaskAttenderSpec extends grails.test.spock.IntegrationSpec {

    def crmTaskService
    def crmContactService

    def "add a booking without attenders to a task"() {
        given:
        def conference = crmTaskService.createTaskType(name: "Meeting", true)
        def startTime = new Date() + 1
        def endTime = use(groovy.time.TimeCategory) { startTime + 60.minutes }
        def task = crmTaskService.createTask(name: "Boring Meeting", startTime: startTime, endTime: endTime, type: conference, true)

        when:
        def booking = crmTaskService.createBooking([task: task, bookingRef: "TEST"], true)

        then:
        !booking.hasErrors()
        booking.ident() != null
        booking.bookingDate != null
        booking.bookingRef == "TEST"
        booking.attenderCount == 0
        booking.estimatedAttenderCount == 0
    }

    def "add CrmContact attenders to a task"() {
        given:
        def meeting = crmTaskService.createTaskType(name: "Meeting", true)
        def confirmed = crmTaskService.createAttenderStatus(name: "Confirmed", true)
        def now = new Date()
        def then = use(groovy.time.TimeCategory) { now + 60.minutes }
        def task = crmTaskService.createTask(name: "Test").with {
            startTime = now
            endTime = then
            type = meeting
            save(failOnError: true)
        }
        def person = crmContactService.createPerson(firstName: "George", lastName: "Groovy", true)

        when:
        def attender = crmTaskService.addAttender(task, person, confirmed, "test")

        then:
        !attender.hasErrors()
        task.save(flush: true)
        task.attenders.size() == 1
        attender.notes == "test"
        attender.toString() == "George Groovy"
        attender.status.toString() == "Confirmed"
    }

    def "add CrmEmbeddedContact attenders to a task"() {
        given:
        def meeting = crmTaskService.createTaskType(name: "Training Event", true)
        def confirmed = crmTaskService.createAttenderStatus(name: "Confirmed", true)
        def now = new Date()
        def then = use(groovy.time.TimeCategory) { now + 180.minutes }
        def task = crmTaskService.createTask(name: "Test").with {
            startTime = now
            endTime = then
            type = meeting
            save(failOnError: true)
        }
        def person = crmContactService.createContactInformation(firstName: "Maria", lastName: "Jensen",
                companyName: 'ACME Inc.', address: [address1: '123 Flower Street', postalCode: '55555', city: 'Eden'])

        when:
        CrmTaskAttender attender = crmTaskService.addAttender(task, person, confirmed, "vegetarian food please")

        then:
        !attender.hasErrors()

        when:
        task.save(flush: true)

        then:
        !task.hasErrors()
        task.attenders.size() == 1
        attender.toString() == "Maria Jensen, ACME Inc."
        attender.contactInformation.name == 'Maria Jensen'
        attender.contactInformation.fullName == 'Maria Jensen, ACME Inc.'
        attender.contactInformation.fullAddress == '123 Flower Street, 55555 Eden'
    }

    def "Test CrmTaskBooking#getAttenderCount"() {
        given:
        def conference = crmTaskService.createTaskType(name: "Conference", true)
        def confirmed = crmTaskService.createAttenderStatus(name: "Confirmed", true)
        def startTime = new Date() + 7
        def endTime = use(groovy.time.TimeCategory) { startTime + 360.minutes }
        def task = crmTaskService.createTask(name: "Developer Conference", startTime: startTime, endTime: endTime, type: conference, true)

        when:
        def booking = crmTaskService.createBooking(task: task, bookingRef: '42', reserve: 2, invoiceAddress:
                [address1: 'ACME Inc.', address2: '123 Flower Street', postalCode: '55555', city: 'Eden'],
                true)

        then: "the booking has 2 reserved spaces"
        !booking.hasErrors()
        booking.attenderCount == 0
        booking.estimatedAttenderCount == 2

        when: "add one attender"
        crmTaskService.addAttender(booking,
                crmContactService.createContactInformation(firstName: "Linda", lastName: "Hamilton",
                        companyName: 'ACME Inc.', address: [address1: '123 Flower Street', postalCode: '55555', city: 'Eden']),
                confirmed, "LCHF food please")

        then: "estimated attender count should return 2"
        booking.save(flush: true)
        booking.attenderCount == 1
        booking.estimatedAttenderCount == 2

        when: "add a second attender"
        crmTaskService.addAttender(booking,
                crmContactService.createContactInformation(firstName: "Adam", lastName: "Goldman",
                        companyName: 'ACME Inc.', address: [address1: '123 Flower Street', postalCode: '55555', city: 'Eden']),
                confirmed, null)

        then: "estimated attender count should return 2"
        booking.save(flush: true)
        booking.attenderCount == 2
        booking.estimatedAttenderCount == 2

        when: "add a third attender"
        crmTaskService.addAttender(booking,
                crmContactService.createContactInformation(firstName: "Jennifer", lastName: "Hackman",
                        companyName: 'ACME Inc.', address: [address1: '123 Flower Street', postalCode: '55555', city: 'Eden']),
                confirmed, null)

        then: "estimated attender count should return 3"
        booking.save(flush: true)
        booking.attenderCount == 3
        booking.estimatedAttenderCount == 3
    }
}
