/*
 * Copyright (c) 2017 Goran Ehrsson.
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

import groovy.transform.CompileStatic

class DefaultEventColorSelector implements CrmEventColorSelector {

    def grailsApplication

    @Override
    @CompileStatic
    String getEventColor(Object event) {
        def color
        if (event instanceof CrmTask) {
            if (event.priority >= 90) {
                color = getColor('highest', '#c3325f')
            } else if (event.priority >= 60) {
                color = getColor('high', '#f9b936')
            } else if (event.priority >= 40) {
                color = getColor('normal', '#71bf44')
            } else if (event.priority >= 20) {
                color = getColor('low', '#00b6de')
            } else {
                color = getColor('lowest', '#999999')
            }
        } else {
            color = getColor('normal', '#71bf44')
        }
        return color
    }

    private String getColor(String priority, String defaultColor) {
        grailsApplication.config.crm.task.color.priority."$priority" ?: defaultColor
    }
}
