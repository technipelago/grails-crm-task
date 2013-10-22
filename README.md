# Grails CRM - Task Management Plugin

CRM = [Customer Relationship Management](http://en.wikipedia.org/wiki/Customer_relationship_management)

Grails CRM is a set of [Grails Web Application Framework](http://www.grails.org/)
plugins that makes it easy to develop web application with CRM functionality.
With CRM we mean features like:

- Contact Management
- Task/Todo Lists
- Project Management


## Task Management Plugin
This plugin provides services and persistence for task/todo management in Grails CRM.
This is a "headless" plugin. For task management user interface see the
[crm-task-ui](https://github.com/technipelago/grails-crm-task-ui.git) plugin.

## Examples

    def phoneCall = crmTaskService.createTaskType(name: "Phone call")

    def later = use(groovy.time.TimeCategory) { new Date() + 60.minutes }
    def task = crmTaskService.createTask(type: phoneCall, name: "Call Joe Average to discuss presentation", startTime: later, true)