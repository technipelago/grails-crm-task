package grails.plugins.crm.task

/**
 * Test CrmTaskAttender features.
 */
class CrmTaskAttenderSpec extends grails.plugin.spock.IntegrationSpec {

    def crmTaskService
    def crmContactService

    def "add attenders to task"() {
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
        task.attenders
        attender.notes == "test"
        attender.toString() == "George Groovy"
        attender.status.toString() == "Confirmed"
    }
}
