/**
 *  License:
 *   Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *   for the specific language governing permissions and limitations under the License.
 *
 **/

import groovy.transform.Field

metadata {
    definition(name: "Basic Settings Tool", namespace: "edalquist", author: "Eric Dalquist") {
        command "getAllSettings"
        command "getSetting", [[name: "settingName", type: "STRING", description: "Name of the setting to get"]]
        command "updateSetting", [
            [name: "settingName", type: "STRING", description: "Name of the setting to get"],
            [name: "settingValue", type: "STRING", description: "Value to set for the setting"],
            [name: "settingType", type: "STRING", description: "Type of the setting value"]
        ]
        command "clearSetting", [[name: "settingName", type: "STRING", description: "Name of the setting to clear"]]
        command "clearAllSettings"
        command "removeSetting", [[name: "settingName", type: "STRING", description: "Name of the setting to remove"]]
        command "removeAllSettings"
    }

    preferences {
        input name: "testSetting_1", type: "enum", defaultValue: "1", displayDuringSetup: false,
                title: "Test Setting 1",
                options: ["1", "2"]

// UNCOMMENT THIS TO ADD A NEW SETTING
//        input name: "testSetting_2", type: "string", defaultValue: "foo", displayDuringSetup: false,
//                title: "Test Setting 2"
    }
}

def parse(String description) {
    def cmd = zwave.parse(description, [0x85: 1, 0x86: 1])
    log.debug "${description} -> ${cmd}"
}

//cmds
def getAllSettings() {
    log.debug "settings: " + settings
}

def getSetting(settingName = null) {
    log.debug "${settingName}: ${settings[settingName]}"
}

def updateSetting(settingName = null, settingValue = null, settingType = null) {
    log.debug "Replacing ${settingName}: '${settings[settingName]}' with '${settingValue}' of type ${settingType}"
    try {
        device.updateSetting(settingName, [value: settingValue, type: settingType])
    } catch (NullPointerException e) {
        log.error "Setting ${settingName} is not a valid setting: " + e
    }
    log.debug "Updated Settings: " + settings
}

def clearAllSettings() {
    log.debug "before: " + settings
    // Copy keys set first to avoid any chance of concurrent modification
    def keys = new HashSet(settings.keySet())
    keys.each{ key -> device.clearSetting(key) }
    log.debug " after: " + settings
}

def clearSetting(settingName = null) {
    log.debug "before: " + settings
    device.clearSetting(settingName)
    log.debug " after: " + settings
}
def removeAllSettings() {
    log.debug "before: " + settings
    // Copy keys set first to avoid any chance of concurrent modification
    def keys = new HashSet(settings.keySet())
    keys.each{ key -> device.removeSetting(key) }
    log.debug " after: " + settings
}

def removeSetting(settingName = null) {
    log.debug "before: " + settings
    device.removeSetting(settingName)
    log.debug " after: " + settings
}

def installed() {
    log.info "installed"
    getAllSettings()
}

def configure() {
    log.info "configure"
    getAllSettings()
}

def updated() {
    log.info "updated"
    getAllSettings()
}
