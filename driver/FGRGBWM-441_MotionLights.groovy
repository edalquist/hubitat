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
 *   https://manuals.fibaro.com/content/manuals/us/FGRGBWM-441/FGRGBWM-441-USA-A-v1.01.pdf
 **/
import groovy.transform.Field

@Field int VERSION = 1

@Field List<String> LOG_LEVELS = ["warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

// hard-coded parameters, always set to the specified values
@Field Map CONSTANT_PARAMETERS = [
        // ALL ON / ALL OFF function activation: 0 - ALL ON inactive, ALL OFF inactive
        1:[0],
        // Associations command class choice: 0 - NORMAL (DIMMER) - BASIC SET/SWITCH_MULTILEVEL_-START/STOP
        6:[0],
        // Output state change mode: 0 - MODE1 (related parameters: 9-step value, 10-time between steps)
        8:[0],
        // Time for changing from start to end value: UNUSED DUE TO #8
        11:[0],
        // 38 & 39 are for alarm program, not used
        38:[10],
        39:intToUnsignedByteArray(1, 2),
        // Command class reporting Outputs status change: reporting as a result of inputs and controllers actions (SWITCH MULTILEVEL)
        42:[0],
        // Reporting 0-10v analog inputs change threshold: 0.1V
        43:[1],
        // Response to BRIGHTNESS set to 0%: 0 - illumination colour set to white (all channels controlled together)
        71:[0],
        // Starting predefined program: UNUSED
        72:[1],
        // Triple click action: 0 - NODE INFO control frame is sent
        73:[0]
]

metadata {
    definition(name: "Fibaro RGBW Controller (FGRGBWM-441) - Light & Motion", namespace: "edalquist", author: "Eric Dalquist") {
        capability "Actuator"       // represents that a Device has commands.
        capability "Switch"         // Allows for the control of a switch device
        capability "Switch Level"   // Allows for the control of the level attribute of a light
        capability "Sensor"         // represents that a Device has attributes
        capability "Energy Meter"   // Read the energy consumption of an energy metering device
        capability "Power Meter"    // Allows for reading the power consumption from devices that report it
        capability "Motion Sensor"  // Allows for the ability to read motion sensor device states
        capability "Refresh"        // Allow the execution of the refresh command for devices that support it
        capability "Polling"        // Deprecated: Allows for the polling of devices that support it.
        capability "Configuration"  // Allow configuration of devices that support it
        capability "Initialize"     //

        // Standard Attributes (for the capabilities above):
        attribute "switch", "enum", ["on", "off"]           // switch
        attribute "level", "number"                         // switch level
        attribute "energy", "number"                        // energy meter
        attribute "power", "number"                         // power meter
        attribute "motion", "enum", ["active", "inactive"]  // motion

        // Custom Attributes:
        attribute "lastReset", "string" // Last Time that energy reporting period was reset.

        // Custom Commands:
        command "getConfigReport"
        command "reset"
        command "reinstall"

        fingerprint deviceId: "0x1101", inClusters: "0x33,0x85,0x86,0x31,0x32,0x70,0x60,0x26,0x86,0x72,0x27,0x20,0x7A"
    }

    preferences {
        section { // GENERAL:
            input name: "logLevel", title: "Select log level",
                    displayDuringSetup: false, required: true,
                    type: "enum", defaultValue: DEFAULT_LOG_LEVEL,
                    options: LOG_LEVELS
        }
        // dimUpInput, dimDownInput, motionInput, lightOutput
        section { // Input/Output Assignments:
            input name: "dimUpInput", title: "The dim up momentary button",
                    description: "[Default: IN1]",
                    displayDuringSetup: true, required: true,
                    type: "enum", defaultValue: 0,
                    options: [0: "Input 1",
                              1: "Input 2",
                              2: "Input 3",
                              3: "Input 4"]

            input name: "dimDownInput", title: "The dim down momentary button",
                    description: "[Default: IN2]",
                    displayDuringSetup: true, required: true,
                    type: "enum", defaultValue: 1,
                    options: [0: "Input 1",
                              1: "Input 2",
                              2: "Input 3",
                              3: "Input 4"]

            input name: "motionInput", title: "The input attached to the motion sensor",
                    description: "[Default: IN3]",
                    displayDuringSetup: true, required: true,
                    type: "enum", defaultValue: 2,
                    options: [0: "Input 1",
                              1: "Input 2",
                              2: "Input 3",
                              3: "Input 4"]

            input name: "lightOutput", title: "The output attached to the lights",
                    description: "[Default: W]",
                    displayDuringSetup: true, required: true,
                    type: "enum", defaultValue: 3,
                    options: [0: "R",
                              1: "G",
                              2: "B",
                              3: "W"]
        }

        section { // CHANNEL MAPPING & THRESHOLDS:
            input name: "dimUpThreshold", title: "Dim Up: Threshold to trigger.",
                    description: "[Default 50%]",
                    displayDuringSetup: false, required: true,
                    type: "number", defaultValue: 50, range: "1..99"

            input name: "dimDownThreshold", title: "Dim Down: Threshold to trigger.",
                    description: "[Default 50%]",
                    displayDuringSetup: false, required: true,
                    type: "number", defaultValue: 50, range: "1..99"

            input name: "motionThreshold", title: "Motion: Threshold to trigger.",
                    description: "[Default 50%]",
                    displayDuringSetup: false, required: true,
                    type: "number", defaultValue: 50, range: "1..99"
        }

        section { // PHYSICAL DEVICE PARAMETERS:
            input name: "configParam09", title: "#9: MODE1: Step value:",
                    description: "[Default: 1]<br/>Size of the step for each change in level during the transition.",
                    displayDuringSetup: false, required: true,
                    type: "number", defaultValue: 1, range: "1..255"

            input name: "configParam10", title: "#10: MODE1: Time between steps:",
                    description: "[Default: 10ms]<br/>Time between each step in a transition between levels. Setting this to zero means an instantaneous change.",
                    displayDuringSetup: false, required: true,
                    type: "number", defaultValue: 10, range: "0..60000"

            input name: "configParam12", title: "#12: Maximum brightening level:",
                    description: "[Default: 255]",
                    displayDuringSetup: false, required: true,
                    type: "number", defaultValue: 255, range: "3..255"

            input name: "configParam13", title: "#13: Minimum dim level:",
                    description: "[Default: 2]",
                    displayDuringSetup: false, required: true,
                    type: "number", defaultValue: 2, range: "0..254"

            input name: "configParam16", title: "#16: Memorise device status at power cut:",
                    description: "[Default: 1: MEMORISE STATUS]",
                    displayDuringSetup: false, required: true,
                    type: "enum", defaultValue: 1,
                    options: [0: "0: DO NOT MEMORISE STATUS",
                              1: "1: MEMORISE STATUS"]

            input name: "configParam30",title: "#30: Response to ALARM of any type:",
                    description: "[Default: 0: INACTIVE]",
                    displayDuringSetup: false, required: true,
                    type: "enum", defaultValue: 0,
                    options: [0: "0: INACTIVE - Device doesn't respond",
                              1: "1: ALARM ON - Device turns on when alarm is detected",
                              2: "2: ALARM OFF - Device turns off when alarm is detected"]
            // Option 3 doesn't make sense for this driver
            // "3": "3: ALARM PROGRAM - Alarm sequence turns on (Parameter #38)"

            input name: "configParam44", title: "#44: Power load reporting frequency:",
                    description: "[Default: 30s]" +
                            "<ul><li>0: reports are not sent" +
                            "<li>1-65534: time between reports (s)",
                    displayDuringSetup: false, required: true,
                    type: "number", defaultValue: 30, range: "0..65534"

            input name: "configParam45", title: "#45: Reporting changes in energy:",
                    description: "[Default: 10 = 0.1kWh]" +
                            "<ul><li>0: reports are not sent" +
                            "<li>1-254: 0.01kWh - 2.54kWh",
                    displayDuringSetup: false, required: true,
                    type: "number", defaultValue: 10, range: "0..254"

        }

        section { // ASSOCIATION GROUPS:
            input type: "paragraph", element: "paragraph",
                    title: "ASSOCIATION GROUPS:", description: "Enter a comma-delimited list of node IDs for each association group.\n" +
                    "Node IDs must be in decimal format (E.g.: 27,155, ... ).\n" +
                    "Each group allows a maximum of five devices.\n"

            input name: "configAssocGroup01", title: "Association Group #1:",
                    type: "text", defaultValue: ""

            input name: "configAssocGroup02", title: "Association Group #2:",
                    type: "text", defaultValue: ""

            input name: "configAssocGroup03", title: "Association Group #3:",
                    type: "text", defaultValue: ""

            input name: "configAssocGroup04", title: "Association Group #4:",
                    type: "text", defaultValue: ""

        }

    }
}

/**********************************************************************
 *  Z-wave Event Handlers.
 **********************************************************************/

/**
 *  parse() - Called when messages from a device are received by the hub.
 *
 *  The parse method is responsible for interpreting those messages and returning Event definitions.
 *
 * @param description The message from the device.
 * @return commands to run in response to the device message
 */
def parse(description) {
    logger("trace", "parse(): raw message: ${description}")
    if (description == "updated") {
        logger("error", "THIS IS STILL NEEDED ON HUBITAT")
        return null
    }

    def result = null
    if (description != "updated") {
        def cmd = zwave.parse(description, getSupportedCommands())
        logger("trace", "parse(): command: ${cmd}")
        if (cmd) {
            result = zwaveEvent(cmd)
        } else {
            logger("error", "parse(): Could not parse raw message: ${description}")
        }
    }

    return result
}

/*

    0x33: 1, // Color Control Command Class (V1)
      physicalgraph.zwave.commands.colorcontrolv1.CapabilityReport
      physicalgraph.zwave.commands.colorcontrolv1.StartCapabilityLevelChange
      physicalgraph.zwave.commands.colorcontrolv1.StateReport
      physicalgraph.zwave.commands.colorcontrolv1.StopStateChange
    0x85: 2, // Association Command Class (V2)
    0x86: 1, // Version Command Class (V1)
    0x31: 2, // Sensor Multilevel Command Class (V2)
    0x32: 2, // Meter Command Class (V2)
    0x70: 1, // Configuration Command Class (V1)
    0x60: 3, // Multi Channel Command Class (V3)
    0x26: 1, // Switch Multilevel Command Class (V1)
    0x86: 1, // Version Command Class (V1)
    0x72: 1, // Manufacturer Specific Command Class (V1)
    0x27: 1, // Switch All Command Class (V1)
    0x20: 1, // Basic Command Class (V1)
    0x7A: 2  // Firmware Update Meta Data (0x7A) : V2 - Not supported in Hubitat
*/


/**
 *  COMMAND_CLASS_MULTICHANNEL (0x60) : MultiChannelCmdEncap
 *
 *  The MultiChannel Command Class is used to address one or more endpoints in a multi-channel device.
 *  The sourceEndPoint attribute will identify the sub-device/channel the command relates to.
 *  The encpsulated command is extracted and passed to the appropriate zwaveEvent handler.
 **/
def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    logger("trace", "zwaveEvent(): MultiChannelCmdEncap received: ${cmd}")

    def encapsulatedCommand = cmd.encapsulatedCommand(getSupportedCommands())
    if (!encapsulatedCommand) {
        logger("warn", "zwaveEvent(): MultiChannelCmdEncap from endPoint ${cmd.sourceEndPoint} could not be translated: ${cmd}")
    } else {
        return zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint)
    }
}

/**
 *  COMMAND_CLASS_CONFIGURATION (0x70) : ConfigurationReport
 *
 *  Configuration reports tell us the current parameter values stored in the physical device.
 *
 *  Due to platform security restrictions, the relevent preference value cannot be updated with the actual
 *  value from the device, instead all we can do is output to the SmartThings IDE Log for verification.
 **/
def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    logger("debug", "zwaveEvent(): ConfigurationReport received: ${cmd}")
    int paramNum = (int) cmd.parameterNumber
    def settingName = String.format("configParam%02d", cmd.parameterNumber)

    def expectedValue
    if (cmd.parameterNumber == 14) {
        expectedValue = getParameter14()
    } else if (CONSTANT_PARAMETERS.containsKey(paramNum)) {
        expectedValue = CONSTANT_PARAMETERS.get(paramNum)
    } else {
        expectedValue = settings.get(settingName)
        if (expectedValue != null) {
            expectedValue = intToUnsignedByteArray(expectedValue, cmd.size)
        }
    }

    // TODO is this true for hubitat too?
    // Translate cmd.configurationValue to an int. This should be returned from zwave.parse() as
    // cmd.scaledConfigurationValue, but it hasn't been implemented by SmartThings yet! :/
    //  See: https://community.smartthings.com/t/zwave-configurationv2-configurationreport-dev-question/9771
    // def scaledConfigurationValue = byteArrayToInt(cmd.configurationValue)

    if (expectedValue != cmd.configurationValue) {
        logger("warn", "Parameter #${cmd.parameterNumber} has value: ${cmd.configurationValue} but should have value: ${expectedValue}")
        logger("warn", "Reload the Device page and click Save Preferences")
    } else {
        logger("info", "Parameter #${cmd.parameterNumber} has expected value: ${cmd.configurationValue}")
    }
}

/**
 *  COMMAND_CLASS_ASSOCIATION (0x85) : AssociationReport
 *
 *  AssociationReports tell the nodes in an association group.
 *  Due to platform security restrictions, the relevent preference value cannot be updated with the actual
 *  value from the device, instead all we can do is output to the SmartThings IDE Log for verification.
 *
 *  Example: AssociationReport(groupingIdentifier: 4, maxNodesSupported: 5, nodeId: [1], reportsToFollow: 0)
 **/
def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
    logger("debug", "zwaveEvent(): AssociationReport received: ${cmd}")
    logger("info", "Association Group ${cmd.groupingIdentifier} contains nodes: ${cmd.nodeId}")
}

/**
 *  COMMAND_CLASS_MANUFACTURER_SPECIFIC (0x72) : ManufacturerSpecificReport
 *
 *  ManufacturerSpecific reports tell us the device's manufacturer ID and product ID.
 **/
def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv1.ManufacturerSpecificReport cmd) {
    logger("debug", "zwaveEvent(): ManufacturerSpecificReport received: ${cmd}")
    updateDataValue("manufacturerName", "${cmd.manufacturerName}")
    updateDataValue("manufacturerId", "${cmd.manufacturerId}")
    updateDataValue("productId", "${cmd.productId}")
    updateDataValue("productTypeId", "${cmd.productTypeId}")
}

/**
 *  COMMAND_CLASS_VERSION (0x86) : VersionReport
 *
 *  Version reports tell us the device's Z-Wave framework and firmware versions.
 **/
def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    logger("debug", "zwaveEvent(): VersionReport received: ${cmd}")
    updateDataValue("applicationVersion", "${cmd.applicationVersion}")
    updateDataValue("applicationSubVersion", "${cmd.applicationSubVersion}")
    updateDataValue("zWaveLibraryType", "${cmd.zWaveLibraryType}")
    updateDataValue("zWaveProtocolVersion", "${cmd.zWaveProtocolVersion}")
    updateDataValue("zWaveProtocolSubVersion", "${cmd.zWaveProtocolSubVersion}")
}

/**
 *  COMMAND_CLASS_FIRMWARE_UPDATE_MD (0x7A) : FirmwareMdReport
 *
 *  Firmware Meta Data reports tell us the device's firmware version and manufacturer ID.
 *  TODO(edalquist) hubitat doesn't fully support this yet:
 **/
def zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd) {
    logger("debug", "zwaveEvent(): FirmwareMdReport received: ${cmd}")
    if (cmd.checksum != null) updateDataValue("firmwareChecksum", "${cmd.checksum}")
    if (cmd.firmwareId != null) updateDataValue("firmwareId", "${cmd.firmwareId}")
    if (cmd.manufacturerId != null) updateDataValue("manufacturerId", "${cmd.manufacturerId}")
}

/**
 *  COMMAND_CLASS_SWITCH_ALL (0x27) : * [IGNORED]
 *
 *  SwitchAll functionality is controlled and reported via device Parameter #1 instead.
 **/
def zwaveEvent(hubitat.zwave.commands.switchallv1.SwitchAllReport cmd) {
    logger("trace", "zwaveEvent(): SwitchAllReport received: ${cmd}")
}

/**
 *  COMMAND_CLASS_METER_V3 (0x32) : MeterReport
 *
 *  The Fibaro RGBW Controller supports scale 0 (energy), and 2 (power) only.
 *
 *  Integer         deltaTime                   Time in seconds since last report
 *  Short           meterType                   Unknown = 0, Electric = 1, Gas = 2, Water = 3
 *  List<Short>     meterValue                  Meter value as an array of bytes
 *  Short           precision                   The decimal precision of the values
 *  List<Short>     previousMeterValue          Previous meter value as an array of bytes
 *  Double          scaledPreviousMeterValue    Previous meter value as a double
 *  Short           rateType                    ???
 *  Short           scale                       The scale of the values: "kWh"=0, "kVAh"=1, "Watts"=2, "pulses"=3,
 *                                              "Volts"=4, "Amps"=5, "Power Factor"=6, "Unknown"=7
 *  Double          scaledMeterValue            Meter value as a double
 *  Short           size                        The size of the array for the meterValue and previousMeterValue
 *  List<Short>     payload                     ???
 **/
def zwaveEvent(hubitat.zwave.commands.meterv2.MeterReport cmd) {
    logger("trace", "zwaveEvent(): MeterReport received: ${cmd}")

    if (cmd.scale == 0) { // Accumulated Energy (kWh):
        state.energy = cmd.scaledMeterValue
        //sendEvent(name: "dispEnergy", value: String.format("%.2f",cmd.scaledMeterValue as BigDecimal) + " kWh", displayed: false)
        logger("info", "Accumulated energy is ${cmd.scaledMeterValue} kWh")
        return createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
    } else if (cmd.scale == 1) { // Accumulated Energy (kVAh): Ignore.
        //createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kVAh")
    } else if (cmd.scale == 2) { // Instantaneous Power (Watts):
        //sendEvent(name: "dispPower", value: String.format("%.1f",cmd.scaledMeterValue as BigDecimal) + " W", displayed: false)
        logger("info", "Power is ${cmd.scaledMeterValue} W")
        return createEvent(name: "power", value: cmd.scaledMeterValue, unit: "W")
    } else if (cmd.scale == 4) { // Instantaneous Voltage (Volts):
        //sendEvent(name: "dispVoltage", value: String.format("%.1f",cmd.scaledMeterValue as BigDecimal) + " V", displayed: false)
        logger("info", "Voltage is ${cmd.scaledMeterValue} V")
        return createEvent(name: "voltage", value: cmd.scaledMeterValue, unit: "V")
    } else if (cmd.scale == 5) {  // Instantaneous Current (Amps):
        //sendEvent(name: "dispCurrent", value: String.format("%.1f",cmd.scaledMeterValue as BigDecimal) + " A", displayed: false)
        logger("info", "Current is ${cmd.scaledMeterValue} A")
        return createEvent(name: "current", value: cmd.scaledMeterValue, unit: "A")
    } else if (cmd.scale == 6) { // Instantaneous Power Factor:
        //sendEvent(name: "dispPowerFactor", value: "PF: " + String.format("%.2f",cmd.scaledMeterValue as BigDecimal), displayed: false)
        logger("info", "PowerFactor is ${cmd.scaledMeterValue}")
        return createEvent(name: "powerFactor", value: cmd.scaledMeterValue, unit: "PF")
    }
}

/**
 *  COMMAND_CLASS_BASIC (0x20) : BasicReport [IGNORED]
 *  https://graph.api.smartthings.com/ide/doc/zwave-utils.html#basicV1
 *
 *  Short   value   0xFF for on, 0x00 for off
 **/
def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    logger("trace", "zwaveEvent(): BasicReport received: ${cmd} (ignored)")
    // BasicReports are ignored as the aggregate switch and level attributes are calculated seperately.
}

/**
 *  COMMAND_CLASS_SWITCH_MULTILEVEL (0x26) : SwitchMultilevelReport
 *
 *  SwitchMultilevelReports tell us the current level of a channel.
 *
 *  These reports will arrive via a MultiChannelCmdEncap command, the zwaveEvent(...MultiChannelCmdEncap) handler
 *  will add the correct sourceEndPoint, before passing to this event handler.
 *
 *  Fibaro RGBW SwitchMultilevelReports have value in range [0..99], so this is scaled to 255 and passed to
 *  zwaveEndPointEvent().
 *
 *  Short       value       0x00 for off, other values are level (on).
 **/
def zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelReport cmd, sourceEndPoint = 0) {
    logger("trace", "zwaveEvent(): SwitchMultilevelReport received from endPoint ${sourceEndPoint}: ${cmd}")
//    return zwaveEndPointEvent(sourceEndPoint, Math.round(cmd.value * 255 / 99))
}

//def zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelStartLevelChange cmd) {
//    logger("trace", "zwaveEvent(): SwitchMultilevelStartLevelChange received: ${cmd}")
//}
//
//def zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelStopLevelChange cmd) {
//    logger("trace", "zwaveEvent(): SwitchMultilevelStopLevelChange received: ${cmd}")
//}
//
/**
 *  COMMAND_CLASS_SENSOR_MULTILEVEL (0x31) : SensorMultilevelReport
 *
 *  Appears to be used to report power. Not sure if anything else...?
 **/
def zwaveEvent(hubitat.zwave.commands.sensormultilevelv2.SensorMultilevelReport cmd) {
    logger("trace", "zwaveEvent(): SensorMultilevelReport received: ${cmd}")

    if (cmd.sensorType == 4 /*SENSOR_TYPE_POWER_VERSION_2*/) { // Instantaneous Power (Watts):
        logger("info", "Power is ${cmd.scaledSensorValue} W")
//        return createEvent(name: "power", value: cmd.scaledSensorValue, unit: "W")
    } else {
        logger("warn", "zwaveEvent(): SensorMultilevelReport with unhandled sensorType: ${cmd}")
    }
}
//
//// /**
////  *  COMMAND_CLASS_SWITCH_COLOR (0x33) : SwitchColorReport
////  *
////  *  SwitchColorReports tell us the current level of a color channel.
////  *  The value will be in the range 0..255, which is passed to zwaveEndPointEvent().
////  *
////  *  String      colorComponent                  Color name, e.g. "red", "green", "blue".
////  *  Short       colorComponentId                0 = warmWhite, 2 = red, 3 = green, 4 = blue, 5 = coldWhite.
////  *  Short       value                           0x00 to 0xFF
////  **/
//// def zwaveEvent(hubitat.zwave.commands.switchcolorv3.SwitchColorReport cmd) {
////     logger("trace", "zwaveEvent(): SwitchColorReport received: ${cmd}")
////     if (cmd.colorComponentId == 0) { cmd.colorComponentId = 5 } // Remap warmWhite colorComponentId
////     return zwaveEndPointEvent(cmd.colorComponentId, cmd.value)
//// }
//
///**
// *  Default zwaveEvent handler.
// *
// *  Called for all Z-Wave events that aren't handled above.
// **/
//def zwaveEvent(hubitat.zwave.Command cmd) {
//    logger("error", "zwaveEvent(): No handler for command: ${cmd}")
//    //logger("error", "zwaveEvent(): Class is: ${cmd.getClass()}" // This causes an error, but still gives us the class in the error message. LOL!)
//}

/**********************************************************************
 *  SmartThings Platform Commands:
 **********************************************************************/

def reinstall() {
    log.warn "DELETING ALL STATE AND REINSTALLING"

    log.info "OLD STATE: ${state}"
    state.clear()
    log.info "NEW STATE: ${state}"

    log.info "OLD SETTINGS: ${settings}"
    def keys = new HashSet(settings.keySet())
    keys.each { key -> device.removeSetting(key) }
    log.info "NEW SETTINGS: ${settings}"

    log.warn "RELOAD THE DRIVER PAGE AND CLICK SAVE"
}

/**
 *  configure() - runs when driver is installed, after installed is run. if capability Configuration exists, a Configure command is added to the ui.
 *
 *  Uses values from device preferences.
 **/
def configure() {
    logger("trace", "configure()")

    def cmds = []

    CONSTANT_PARAMETERS.each {key, val ->
        cmds << zwave.configurationV1.configurationSet(parameterNumber: key, size: val.size(), configurationValue: val)
    }

    cmds << zwave.configurationV1.configurationSet(parameterNumber: 9, size: 1, configurationValue: [configParam09])
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 10, size: 2, configurationValue: intToUnsignedByteArray(configParam10, 2))
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 12, size: 1, configurationValue: [configParam12])
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 13, size: 1, configurationValue: [configParam13])
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 14, size: 2, configurationValue: getParameter14())
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 16, size: 1, configurationValue: [configParam16])
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 30, size: 1, configurationValue: [configParam30])
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 44, size: 2, configurationValue: intToUnsignedByteArray(configParam44, 2))
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 45, size: 1, configurationValue: [configParam45])

    // Association Groups:
    cmds << zwave.associationV2.associationRemove(groupingIdentifier: 1, nodeId: [])
    cmds << zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: parseAssocGroup(configAssocGroup01, 5))
    cmds << zwave.associationV2.associationRemove(groupingIdentifier: 2, nodeId: [])
    cmds << zwave.associationV2.associationSet(groupingIdentifier: 2, nodeId: parseAssocGroup(configAssocGroup02, 5))
    cmds << zwave.associationV2.associationRemove(groupingIdentifier: 3, nodeId: [])
    cmds << zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: parseAssocGroup(configAssocGroup03, 5))
    cmds << zwave.associationV2.associationRemove(groupingIdentifier: 4, nodeId: [])
    cmds << zwave.associationV2.associationSet(groupingIdentifier: 4, nodeId: parseAssocGroup(configAssocGroup04, 5))
    cmds << zwave.associationV2.associationRemove(groupingIdentifier: 5, nodeId: [])
    // Add the hub (controller) to Association Group #5.
    cmds << zwave.associationV2.associationSet(groupingIdentifier: 5, nodeId: [zwaveHubNodeId])

    logger("warn", "configure(): Device Parameters are being updated. It is recommended to power-cycle the Fibaro device once completed.")
    return formatForSend(delayBetween(cmds, 500)) + getConfigReport()
}

/**
 *  installed() - runs when driver is installed, via pair or virtual.
 **/
def installed() {
    logger("trace", "installed(${VERSION})")

    /*
    sendEvent(config_status: initializing)
    send getParameter commands
    zwave event handlers update settings
     */

    state.installedAt = now()
    state.lastReset = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
//
//    // Initialise attributes:
//    sendEvent(name: "switch", value: "off", displayed: false)
//    sendEvent(name: "level", value: 0, unit: "%", displayed: false)
//    sendEvent(name: "hue", value: 0, unit: "%", displayed: false)
//    sendEvent(name: "saturation", value: 0, unit: "%", displayed: false)
//    sendEvent(name: "colorName", value: "custom", displayed: false)
//    sendEvent(name: "color", value: "[]", displayed: false)
//    sendEvent(name: "activeProgram", value: 0, displayed: false)
//    sendEvent(name: "energy", value: 0, unit: "kWh", displayed: false)
//    sendEvent(name: "power", value: 0, unit: "W", displayed: false)
//    sendEvent(name: "lastReset", value: state.lastReset, displayed: false)
//
    state.installVersion = VERSION
}

/**
 *  updated() - Runs after device settings have been changed in the GUI
 **/
def updated() {
    logger("trace", "updated(${settings})")

    state.lastUpdated = now()

    // Make sure installation has completed:
    if (!state.installVersion || state.installVersion != VERSION) {
        installed()
    }

    def channelMap = [[], [], [], []]
    channelMap[dimUpInput.toInteger()] << "Dim Up Input"
    channelMap[dimDownInput.toInteger()] << "Dim Down Input"
    channelMap[motionInput.toInteger()] << "Motion Input"
    channelMap[lightOutput.toInteger()] << "Light Output"

    def badChannel = channelMap.find { it.size() != 1 }
    if (badChannel != null) {
        throw new IllegalStateException("Exactly one input or output may be assigned to each channel: ${channelMap}")
    }

    // TODO may need more logic here as implementation goes along

    return configure()
}

/**
 * runs first time driver loads, ie system startup when capability Initialize exists, a Initialize command is added to the ui.
 */
def initialize() {
    logger("trace", "initialize()")
}

/**********************************************************************
 *  Capability-related Commands:
 **********************************************************************/

/**
 *  on() - Turn the switch on. [Switch Capability]
 **/
def on() {
    def channel = getOutputChannel()
    logger("info", "on(): Setting channel ${channel} switch to on.")

    def cmds = []

    cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: channel )
        // TODO restore previous level?
        .encapsulate(zwave.switchMultilevelV2.switchMultilevelSet(value: 99, dimmingDuration: 1))
    cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: channel )
        .encapsulate(zwave.switchMultilevelV2.switchMultilevelGet())
    
    sendEvent(name: "switch", value: "on")

    return formatForSend(delayBetween(cmds))
}

/**
 *  off() - Turn the switch off. [Switch Capability]
 **/
def off() {
    def channel = getOutputChannel()
    logger("info", "off(): Setting channel ${channel} switch to off.")

    def cmds = []

    cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: channel )
        // TODO save current level for restore?
        .encapsulate(zwave.switchMultilevelV2.switchMultilevelSet(value: 0, dimmingDuration: 1))
    cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: channel )
        .encapsulate(zwave.switchMultilevelV2.switchMultilevelGet())
    
    sendEvent(name: "switch", value: "off")

    return formatForSend(delayBetween(cmds))
}

/**
 *  setLevel(level, rate) - Set the (aggregate) level. [Switch Level Capability]
 *
 *  Note: rate is ignored as it is not supported.
 *
 *  Calculation of new channel levels is controlled by configLevelSetMode (see preferences).
 *  Only sends commands to RGBW/OUT channels to avoid altering the levels of INPUT channels.
 **/
def setLevel(level, rate = 1) {
    def channel = getOutputChannel()
    if (level < 0) level = 0
    if (level > 100) level = 100
    level = Math.round(level * 99 / 100).toInteger() // scale level for switchMultilevelSet.
    logger("info", "setLevel(): Setting channel ${channel} switch to ${level}.")

    def cmds = []
    sendEvent(name: "level", value: level)
    cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: channel)
        .encapsulate(zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: 1))
    cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: channel)
        .encapsulate(zwave.switchMultilevelV2.switchMultilevelGet())

    return formatForSend(delayBetween(cmds))
}

/**
 *  poll() - Polls the device. [Polling Capability]
 *
 *  The SmartThings platform seems to poll devices randomly every 6-8mins.
 **/
def poll() {
    logger("trace", "poll()")
    refresh()
}

/**
 *  refresh() - Refreshes values from the physical device. [Refresh Capability]
 **/
def refresh() {
    logger("trace", "refresh()")
    def cmds = []

    cmds << zwave.switchAllV1.switchAllGet()

    // dimUpInput, dimDownInput, motionInput, lightOutput

    // INPUT channels are 2..5, ch1 is the average of 2..5
    (2..5).each {
        cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: it).encapsulate(zwave.switchMultilevelV2.switchMultilevelGet())
    }
    // else { // There are no INPUT channels, so we can use switchColorGet for greater accuracy:
      (0..4).each { cmds << zwave.switchColorV3.switchColorGet(colorComponentId: it) }
    // }

    cmds << zwave.meterV3.meterGet(scale: 0) // Get energy MeterReport
    cmds << zwave.meterV3.meterGet(scale: 2) // Get power MeterReport

    return formatForSend(delayBetween(cmds, 200))
}

/**********************************************************************
 *  Custom Commands:
 **********************************************************************/

/**
 *  reset() - Reset Accumulated Energy.
 **/
def reset() {
    logger("trace", "reset()")

    state.lastReset = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
    sendEvent(name: "lastReset", value: state.lastReset)

    return [
            zwave.meterV3.meterReset(),
            zwave.meterV3.meterGet(scale: 0)
    ]
}

/**********************************************************************
 *  Private Helper Methods:
 **********************************************************************/

/**
 * Channel numbers start at 2 because ch1 is the "all" channel and "set" commands are 1 indexed                
 */
private getOutputChannel() {
    return lightOutput.toInteger() + 2;
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

/**
 * Calls .format() on every element in the cmds list. If the method can't be found keep the element as is.
 */
private formatForSend(cmds) {
    logger("trace", "formatForSend:\n\t" + cmds.join("\n\t"))
    out = cmds.flatten().collect {
        try {
            return it.format()
        } catch (org.codehaus.groovy.runtime.metaclass.MethodSelectionException e) {
            if (!e.toString().contains("java.lang.String#")) {
                logger("warn", "format failed: ${e}")
            }
            return it
        }
    }
    logger("trace", "formatForSend:\n\t" + out.join("\n\t"))
    return out
}

/**
 * parseAssocGroup(string, maxNodes)
 *
 *  Converts a comma-delimited string into a list of integers.
 *  Checks that all elements are integer numbers, and removes any that are not.
 *  Checks that the final list contains no more than maxNodes.
 */
private parseAssocGroup(string, maxNodes) {
    logger("trace", "parseAssocGroup(): Translating string: ${string}")

    if (string) {
        def nodeList = string.split(',')
        nodeList = nodeList.collect { node ->
            if (node.isInteger()) {
                node.toInteger()
            } else {
                logger("warn", "parseAssocGroup(): Cannot parse: ${node}")
            }
        }
        nodeList = nodeList.findAll() // findAll() removes the nulls.
        if (nodeList.size() > maxNodes) {
            logger("warn", "parseAssocGroup(): Number of nodes is greater than ${maxNodes}!")
        }
        return nodeList.take(maxNodes)
    } else {
        return []
    }
}

/**
 * getSupportedCommands() - Returns a map of the command versions supported by the device.
 *
 * Used by parse(), and to extract encapsulated commands
 *
 * https://graph.api.smartthings.com/ide/doc/zwave-utils.html
 **/
private getSupportedCommands() {
    return [
            0x33: 3, // Color Control Command Class (V3)
            0x85: 2, // Association Command Class (V2)
            0x86: 1, // Version Command Class (V1)
            0x31: 2, // Sensor Multilevel Command Class (V2)
            0x32: 3, // Meter Command Class (V3)
            0x70: 2, // Configuration Command Class (V2)
            0x60: 3, // Multi Channel Command Class (V3)
            0x26: 2, // Switch Multilevel Command Class (V2)
            0x86: 1, // Version Command Class (V1)
            0x72: 2, // Manufacturer Specific Command Class (V2)
            0x27: 1, // Switch All Command Class (V1)
            0x20: 1, // Basic Command Class (V1)
            0x7A: 2  // Firmware Update Meta Data (0x7A) : V2 - Not supported in Hubitat
    ]
}

/**
 * Parameter #14 needs to be reconstituted from each 4-bit channel value. 3 of the channels are always 0-10V IN
 * which is 8 (1000) so create an array of 8s then set whichever channel is the output to OUT 9 (1001)
 */
private getParameter14() {
    def channels = [8, 8, 8, 8]
    channels[lightOutput.toInteger()] = 9
    def param = [
            (channels[0] * 0x10) + channels[1],
            (channels[2] * 0x10) + channels[3]
    ]
    logger("debug", "Parameter #14 = ${param} via ${channels}")
    return param
}

/**
 *  intToUnsignedByteArray(number, size)
 *
 *  Converts an unsigned int to an unsigned byte array of set size.
 **/
private intToUnsignedByteArray(number, size) {
    if (number < 0) {
        logger("error", "intToUnsignedByteArray(): Doesn't work with negative number: ${number}")
    } else {
        def uBA = new BigInteger(number).toByteArray() // This returns a SIGNED byte array.
        uBA = uBA.collect { (it < 0) ? it & 0xFF : it } // Convert from signed to unsigned.
        while (uBA.size() > size) {
            uBA = uBA.drop(1)
        } // Trim leading bytes if too long. (takeRight() is not available)
        while (uBA.size() < size) {
            uBA = [0] + uBA
        } // Pad with leading zeros if too short.
        return uBA
    }
}

/**
 *  byteArrayToInt(byteArray)
 *
 *  Converts an unsigned byte array to a int.
 *  Should use ByteBuffer, but it's not available in SmartThings.
 **/
private byteArrayToInt(byteArray) {
    // return java.nio.ByteBuffer.wrap(byteArray as byte[]).getInt()
    def i = 0
    byteArray.reverse().eachWithIndex { b, ix -> i += b * (0x100**ix) }
    return i
}

/**
 *  zwaveEndPointEvent(sourceEndPoint, value)
 *
 *   Int        sourceEndPoint      ID of endPoint. 1 = Aggregate, 2 = Ch1, 3 = Ch2...
 *   Short      value               Expected range [0..255].
 *
 *  This method handles level reports received via several different command classes (BasicReport,
 *  SwitchMultilevelReport, SwitchColorReport).
 *
 *  switch and level attributes for the physical channel are updated (e.g. switchCh1, levelCh1).
 *
 *  If the channel is mapped to a colour, the colour's switch and level attributes are also updated
 *  (e.g. switchBlue, levelBlue).
 *
 *  Aggregate device atributes (switch, level, hue, saturation, color, colorName) are also updated.
 **/
private zwaveEndPointEvent(sourceEndPoint, value) {
    logger("trace", "zwaveEndPointEvent(): EndPoint ${sourceEndPoint} has value: ${value}")

    def channel = sourceEndPoint - 1
    def percent = Math.round(value * 100 / 255)

    if (1 == sourceEndPoint) { // EndPoint 1 is the aggregate channel, which is calculated later. IGNORE.
        logger("debug", "zwaveEndPointEvent(): MultiChannelCmdEncap from endpoint 1 (aggregate) ignored ${percent}%.")
    } else if ((sourceEndPoint > 1) & (sourceEndPoint < 6)) { // Physical channel #1..4

        // Update level:
        logger("info", "Channel ${channel} level is ${percent}%.")
        sendEvent(name: "levelCh${channel}", value: percent, unit: "%")

        // Update switch:
        if (percent >= state.channelThresholds[channel].toInteger()) {
            logger("info", "Channel ${channel} is on.")
            sendEvent(name: "switchCh${channel}", value: "on")
        } else {
            logger("info", "Channel ${channel} is off.")
            sendEvent(name: "switchCh${channel}", value: "off")
        }
    } else {
        logger("warn", "SwitchMultilevelReport recieved from unknown endpoint: ${sourceEndPoint}")
    }

    // Calculate aggregate switch attribute:
    // TODO: Add shortcuts here to check if the channel we are processing is IN or OUT.
    def newSwitch = "off"
    if ("IN" == configAggregateSwitchMode) { // Build aggregate only from INput channels.
        (1..4).each { i ->
            if ((8 == state.channelModes[i]) & ("on" == device.latestValue("switchCh${i}"))) {
                newSwitch = "on"
            }
        }
    } else if ("OUT" == configAggregateSwitchMode) { // Build aggregate only from RGBW/OUT channels.
        (1..4).each { i ->
            if ((8 != state.channelModes[i]) & ("on" == device.latestValue("switchCh${i}"))) {
                newSwitch = "on"
            }
        }
    } else { // Build aggregate from ALL channels.
        (1..4).each { i ->
            if ("on" == device.latestValue("switchCh${i}")) {
                newSwitch = "on"
            }
        }
    }
    logger("info", "Switch is ${newSwitch}.")
    sendEvent(name: "switch", value: newSwitch)

    // Calculate aggregate level attribute:
    def newLevel = 0
    if ("IN" == configAggregateSwitchMode) { // Build aggregate only from INput channels.
        (1..4).each { i ->
            if (8 == state.channelModes[i]) {
                newLevel = Math.max(newLevel, device.latestValue("levelCh${i}").toInteger())
            }
        }
    } else if ("OUT" == configAggregateSwitchMode) { // Build aggregate only from RGBW/OUT channels.
        (1..4).each { i ->
            if (8 != state.channelModes[i]) {
                newLevel = Math.max(newLevel, device.latestValue("levelCh${i}").toInteger())
            }
        }
    } else { // Build aggregate from ALL channels.
        (1..4).each { i ->
            newLevel = Math.max(newLevel, device.latestValue("levelCh${i}").toInteger())
        }
    }
    logger("info", "Level is ${newLevel}.")
    sendEvent(name: "level", value: newLevel, unit: "%")

    // Should send the result of a CreateEvent...
    return "Processed channel level"
}

/**
 *  onChX() - Set switch for an individual channel to "on".
 *
 *  If channel is RGBW/OUT, restore the saved level (if there is one, else 100%).
 *  If channel is an INPUT channel, don't issue command. Log warning instead.
 **/
private onChX(channel) {
    logger("info", "onX(): Setting channel ${channel} switch to on.")

    def cmds = []
    if (channel < 1 || channel > 4) {
        logger("warn", "onX(): Channel ${channel} does not exist!")
    } else if (8 == state.channelModes[channel]) {
        logger("warn", "onX(): Channel ${channel} is an INPUT channel. Command not sent.")
        cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: (channel + 1))
                .encapsulate(zwave.switchMultilevelV2.switchMultilevelGet()) // Endpoint = channel + 1
    } else {
        def newLevel = device.latestValue("savedLevelCh${channel}") ?: 100
        newLevel = (0 == newLevel.toInteger()) ? 99 : Math.round(newLevel.toInteger() * 99 / 100)
        // scale level for switchMultilevelSet.
        logger("info", "onX(): Channel ${channel} to: ${newLevel}")

        cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: (channel + 1))
                .encapsulate(zwave.switchMultilevelV2.switchMultilevelSet(value: newLevel.toInteger()))
        // Endpoint = channel + 1
        sendEvent(name: "savedLevelCh${channel}", value: null) // Wipe savedLevel.
        sendEvent(name: "activeProgram", value: 0) // Wipe activeProgram.
    }

    logger("trace", "Running " + cmds.size() + " commands: " + cmds)
    return cmds
}

/**
 *  offChX() - Set switch for an individual channel to "off".
 *
 *  If channel is RGBW/OUT, save the level and turn off.
 *  If channel is an INPUT channel, don't issue command. Log warning instead.
 **/
private offChX(channel) {
    logger("info", "offX(): Setting channel ${channel} switch to off.")

    def cmds = []
    if (channel > 4 || channel < 1) {
        logger("warn", "offX(): Channel ${channel} does not exist!")
    } else if (8 == state.channelModes[channel]) {
        logger("warn", "offX(): Channel ${channel} is an INPUT channel. Command not sent.")
        cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: (channel + 1))
                .encapsulate(zwave.switchMultilevelV2.switchMultilevelGet()) // endPoint = channel + 1
    } else {
        sendEvent(name: "savedLevelCh${channel}", value: device.latestValue("levelCh${channel}").toInteger())
        // Save level to 'hidden' attribute.
        sendEvent(name: "activeProgram", value: 0) // Wipe activeProgram.
        logger("info", "onX(): Channel ${channel} to: 0")
        cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: (channel + 1))
                .encapsulate(zwave.switchMultilevelV2.switchMultilevelSet(value: 0)) // endPoint = channel + 1
    }

    logger("trace", "Running " + cmds.size() + " commands: " + cmds)
    return cmds
}


/**********************************************************************
 *  Testing Commands:
 **********************************************************************/

/**
 * getConfigReport() - Get current device parameters and output to debug log.
 *
 *  The device settings in the UI cannot be updated due to platform restrictions.
 */
def getConfigReport() {
    logger("trace", "getConfigReport()")
    def cmds = []

    cmds << zwave.configurationV1.configurationGet(parameterNumber: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 6)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 8)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 9)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 10)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 11)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 12)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 13)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 14)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 16)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 30)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 38)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 39)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 42)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 43)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 44)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 45)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 71)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 72)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 73)

    // Request Association Reports:
    cmds << zwave.associationV2.associationGet(groupingIdentifier: 1)
    cmds << zwave.associationV2.associationGet(groupingIdentifier: 2)
    cmds << zwave.associationV2.associationGet(groupingIdentifier: 3)
    cmds << zwave.associationV2.associationGet(groupingIdentifier: 4)
    cmds << zwave.associationV2.associationGet(groupingIdentifier: 5)

    // Request Manufacturer, Version, Firmware Reports:
    cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet()
    cmds << zwave.versionV1.versionGet()
    cmds << zwave.firmwareUpdateMdV2.firmwareMdGet()

    return formatForSend(delayBetween(cmds, 500))
}
