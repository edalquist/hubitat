/*
 *  Copyright 2018 Steve White
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy
 *  of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 */
import groovy.transform.Field

@Field List<String> LOG_LEVELS = ["warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
    // Automatically generated. Make future change here.
    definition(name: "Iris Smart Plug - Always On", namespace: "shackrat", author: "Steve White") {
        capability "Actuator"
        capability "Switch"
        capability "Power Meter"
        capability "Voltage Measurement"
        capability "Configuration"
        capability "Refresh"
        capability "Sensor"

        attribute "ACFrequency", "number"

        command "toggle"
        command "identify"
        command "resetToFactoryDefault"


        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B04,0B05", outClusters: "0019", manufacturer: "CentraLite", model: "3200", deviceJoinName: "Outlet"
        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B04,0B05", outClusters: "0019", manufacturer: "CentraLite", model: "3200-Sgb", deviceJoinName: "Outlet"
        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B04,0B05", outClusters: "0019", manufacturer: "CentraLite", model: "4257050-RZHAC", deviceJoinName: "Outlet"
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 000F, 0B04", outClusters: "0019", manufacturer: "SmartThings", model: "outletv4", deviceJoinName: "Outlet"
        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B04,0B05", outClusters: "0019"
        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0009,0B04,0B05", outClusters: "0019", manufacturer: "Samjin", model: "outlet", deviceJoinName: "Outlet"
    }


    preferences {
        section {
            input "intervalMin", "number", title: "Minimum interval (seconds) between power reports:", defaultValue: 5, range: "1..600", required: false
            input "intervalMax", "number", title: "Maximum interval (seconds) between power reports:", defaultValue: 600, range: "1..600", required: false
            input "minDeltaV", "enum", title: "Amount of voltage change required to trigger a report:", options: [".5", "1", "5", "10", "15", "25", "50"], defaultValue: "1", required: false
        }
        section { // GENERAL:
            input name: "logLevel", title: "Select log level",
                    displayDuringSetup: false, required: true,
                    type: "enum", defaultValue: DEFAULT_LOG_LEVEL,
                    options: LOG_LEVELS
        }
    }
}


/*
    installed

    Doesn't do much other than call configure().
*/
def installed() {
    initialize()
    configure()
}


/*
    updated

    Doesn't do much other than call configure().
*/
def updated() {
    initialize()
    configure()
}


/*
    initialize

    Doesn't do much other than call configure().
*/
def initialize() {
    state.lastSwitch = 0
}


/**
 * Processes incoming zigbee messages from the Iris Smart Plug.
 */
def parse(String description) {
    logger("trace", "Msg: Description is $description")

    def event = zigbee.getEvent(description)
    def msg = zigbee.parseDescriptionAsMap(description)

    logger("trace", "Parsed data...  Evt: ${event}, msg: ${msg}")

    // Hubitat does not seem to support power events
    if (event) {
        if (event.name == "power") {
            def value = (event.value.parseInt()) / 10
            event = createEvent(name: event.name, value: value, descriptionText: "${device.displayName} power is ${value} watts")
            logger("trace", "${device.displayName} power is ${value} watts")
        }
        else if (event.name == "switch") {
            def descriptionText = event.value == "on" ? "${device.displayName} is On" : "${device.displayName} is Off"
            event = createEvent(name: event.name, value: event.value, descriptionText: descriptionText)

            if (event.value == "off") {
                // Since the switch has reported that it is off it can't be using any power.  Set to zero in case the power report does not arrive, but do not report in event logs.
                // sendEvent(name: "power", value: "0", descriptionText: "${device.displayName} power is 0 watts")
                logger("info", "button off - forcing on")
                sendHubCommands(on())
            }

            // DEVICE HEALTH MONITOR: Switch state (on/off) should report every 10mins or so, regardless of any state changes.
            // Capture the time of this message
            state.lastSwitch = now()
        }
    }

    // Handle interval-based power reporting
    else if (msg?.cluster == "0B04") {
        // Watts
        if (msg?.attrId == "050B") {
            def value = Integer.parseInt(msg.value, 16) / 10
            event = createEvent(name: "power", value: value, descriptionText: "${device.displayName} power is ${value} watts")
            logger("trace", "${device.displayName} power is ${value} watts")
        }

        // Volts
        else if (msg?.attrId == "0505") {
            def value = Integer.parseInt(msg.value, 16)
            if ((device.data.manufacturer == "SmartThings" || device.data.manufacturer == "Samjin") && value > 10) value /= 10  // ST branded outlets read voltage in tenths
            event = createEvent(name: "voltage", value: value, descriptionText: "${device.displayName} voltage is ${value} volts")
            logger("trace", "${device.displayName} voltage is ${value} volts")
        }

        // AC Frequency
        else if (msg?.attrId == "0300") {
            def value = Integer.parseInt(msg.value, 16)
            event = createEvent(name: "ACFrequency", value: value, descriptionText: "${device.displayName} AC frequency is ${value} hertz")
            logger("trace", "${device.displayName} AC frequency is ${value} hertz")
        }
    }

    // Handle everything else
    else {
        def cluster = zigbee.parse(description)

        if (cluster && cluster.clusterId == 0x0006 && (cluster.command == 0x07 || cluster.command == 0x0B)) {
            if (cluster.data[0] == 0x00 || cluster.data[0] == 0x02) {
                // DEVICE HEALTH MONITOR: Switch state (on/off) should report every 10mins or so, regardless of any state changes.
                // Capture the time of this message
                state.lastSwitch = now()

                if (cluster.data[0] == 0x00) logger("debug", "ON/OFF REPORTING CONFIG RESPONSE: " + cluster)
                if (cluster.data[0] == 0x02) logger("debug", "ON/OFF TOGGLE RESPONSE: " + cluster)
            }
            else {
                logger("error", "ON/OFF REPORTING CONFIG FAILED- error code:${cluster.data[0]}")
                event = null
            }
        }
        else if (cluster && cluster.clusterId == 0x0B04 && cluster.command == 0x07) {
            if (cluster.data[0] == 0x00) {
                // Get a power meter reading
                runIn(5, "refresh")
                logger("debug", "POWER REPORTING CONFIG RESPONSE: " + cluster)
            }
            else {
                logger("error", "POWER REPORTING CONFIG FAILED- error code:${cluster.data[0]}")
                event = null
            }
        }
        else if (cluster && cluster.clusterId == 0x0003 && cluster.command == 0x04) {
            logger("info", "LOCATING DEVICE FOR 30 SECONDS")
        }
        else {
            logger("warn", "DID NOT PARSE MESSAGE for description : ${description}\n\tEvent: ${event}\n\tMsg: ${msg}")
            logger("debug", "${cluster}")
        }
    }

    return event ? createEvent(event) : event
}

/**
 * Turns the device on. Uses standard Zigbee on/off cluster.
 */
def on() {
    zigbee.command(0x0006, 0x01)
}

/**
 * Turns the device off. Uses standard Zigbee on/off cluster.
 */
def off() {
    // Disabled, this outlet should never get turned off
    //zigbee.command(0x0006, 0x00)
}

/**
 * Toggles the device on/off state.
 */
def toggle() {
    // Disabled, this outlet should never get turned off
    //zigbee.command(0x0006, 0x02)
}

/**
 * Flashes the blue LED on the plug to identify it.
 */
def identify() {
    zigbee.writeAttribute(0x0003, 0x0000, DataType.UINT16, 0x00A)
}

/**
 * Resets the plug to factory defaults but does not unpair the device.
 */
def resetToFactoryDefault() {
    logger("warn", "Resetting device to factory defaults...")
    zigbee.command(0x0000, 0x00)

    runIn(10, configure)
}

/**
 * Refreshes the device by requesting manufacturer-specific information.
 *
 * Note: This is called from the refresh capbility
 */
def refresh() {
    logger("debug", "Refresh called...")

    cmds = []
    cmds += zigbee.onOffRefresh()
    cmds += zigbee.electricMeasurementPowerRefresh()
    cmds += zigbee.readAttribute(0x0B04, 0x0505)
    cmds += zigbee.readAttribute(0x0B04, 0x0300)
    return cmds
}


/**
 * Configures the Z-Wave repeater associations and establishes periodic device check.
 */
def configure() {
    logger("debug", "Configure called...")

    // On/Off reporting of 0 seconds, maximum of 15 minutes if the device does not report any on/off activity
    cmds = []
    cmds += zigbee.onOffConfig(0, 900)
    cmds += powerConfig()
    return cmds
}


/**
 * Set power reporting configuration for devices with min reporting interval as 5 seconds and
 * reporting interval if no activity as 10min (600s), if not otherwise specified.
 */
def powerConfig() {
    // Calculate threshold
    def powerDelta = (Float.parseFloat(minDeltaV ?: "1") * 10)

    logger("debug", "Configuring power reporting intervals; min: ${intervalMin}, max: ${intervalMax}, delta: ${minDeltaV}, endpointId: ${endpointId}")

    def cmds = []
    cmds +=  zigbee.configureReporting(0x0B04, 0x050B, 0x29, (int) (intervalMin ?: 5), (int) (intervalMax ?: 600), (int) powerDelta) // Wattage report.
    cmds +=  zigbee.configureReporting(0x0B04, 0x0505, 0x21, 30, 900, 1)                                                             // Voltage report
    cmds +=  zigbee.configureReporting(0x0B04, 0x0300, 0x21, 900, 86400, 1)                                                          // AC Frequency Report
    return cfg
}


/**
 * Helper function to get device endpoint (hex) as a String.
 */
private getEndpointId() {
    new BigInteger(device.endpointId, 16).toString()
}

private sendHubCommands(cmds) {
    action = new hubitat.device.HubMultiAction()
    cmds.each{ cmd -> action.add(cmd) }
    sendHubCommand(action)
}

/**
 * @param level Level to log at, see LOG_LEVELS for options
 * @param msg Message to log
 */
private logger(level, msg) {
    if (level && msg) {
        def levelIdx = LOG_LEVELS.indexOf(level)
        def setLevelIdx = LOG_LEVELS.indexOf(logLevel)
        if (setLevelIdx < 0) setLevelIdx = LOG_LEVELS.indexOf(DEFAULT_LOG_LEVEL)
        if (levelIdx <= setLevelIdx) {
            log."${level}" "${device.displayName}: ${msg}"
        }
    }
}
