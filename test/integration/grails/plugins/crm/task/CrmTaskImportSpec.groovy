package grails.plugins.crm.task

import spock.lang.Ignore

/**
 * Test import of tasks using the crm-import plugin.
 */
class CrmTaskImportSpec  extends grails.plugin.spock.IntegrationSpec {

    def crmImportService

    @Ignore
    def "import some tasks"() {
        given:
        def file = File.createTempFile("crm", ".csv")
        file.deleteOnExit()
        file.withPrintWriter("UTF-8") { out ->
            out.println('CustomerNo,CompanyName,Address,Zip,Phone,Email')
            out.println('1234-0,"ACME Inc,212 Grails Street","789 0","+46 555 55555","info@acme.com"')
            out.println('17-32,"Spring Code,31 Enterprise Road","76 54","+1 555 1234","info@springcode.com"')
            out.println('2013,"Groovy Labs,184 Dynamic Avenue","","","info@groovylabs.com"')
        }

        when:
        crmImportService?.load(file) {
            data {
                username = 'demo'
            }
            crmTaskImporter(description: "Import CrmTask records", key: 'task') {
                data {
                    name = 'Cold Call'
                    reference = company//person
                    type = 'phone'
                    status = 'planned'
                    startTime = new Date() + 1//at(9, 30, new Date() + 1)
                }
            }
        }

        then:
        CrmTask.count() == 3
    }
}