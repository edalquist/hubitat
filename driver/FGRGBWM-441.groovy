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
metadata {
    definition(name: "Fibaro RGBW Controller - Light & Motion", namespace: "edalquist", author: "Eric Dalquist") {
        capability "Actuator"
        capability "Switch"
        capability "Switch Level"
        capability "Sensor"
        capability "Energy Meter"
        capability "Power Meter"
        capability "Motion Sensor"
        capability "Refresh"
        capability "Polling"
        capability "Configuration"
        capability "Initialize"

        // Standard Attributes (for the capabilities above):
        attribute "switch", "enum", ["on", "off"]
        attribute "level", "number"
        attribute "energy", "number"
        attribute "power", "number"
        attribute "motion", "enum", ["active", "inactive"]

        // Custom Attributes:
        attribute "lastReset", "string" // Last Time that energy reporting period was reset.

        // Custom Commands:
        command "getConfigReport"
        command "reset"

        // Raw Channel attributes and commands:
        (1..4).each { n ->
            attribute "switchCh${n}", "enum", ["on", "off"]
            attribute "levelCh${n}", "number"
            command "onCh$n"
            command "offCh$n"
            command "setLevelCh$n"
        }

        fingerprint deviceId: "0x1101", inClusters: "0x33,0x85,0x86,0x31,0x32,0x70,0x60,0x26,0x86,0x72,0x27,0x20,0x7A"
    }

    preferences {
        section { // GENERAL:
            input type: "paragraph", element: "paragraph",
                    title: "GENERAL:", description: "General settings."

            input name: "configDebugMode", type: "boolean", defaultValue: false, displayDuringSetup: false,
                    title: "Enable debug logging?"

        }

        section { // AGGREGATE SWITCH/LEVEL:
            input type: "paragraph", element: "paragraph",
                    title: "AGGREGATE SWITCH/LEVEL:", description: "These settings control how the device's 'switch' and 'level' attributes are calculated."

            input name: "configAggregateSwitchMode", type: "enum", defaultValue: "OUT", displayDuringSetup: false,
                    title: "Calaculate Aggregate 'switch' value from:\n[Default: RBGW/OUT Channels Only]",
                    options: ["OUT": "RBGW/OUT Channels Only",
                              "IN" : "IN Channels Only",
                              "ALL": "All Channels"]

            input name: "configAggregateLevelMode", type: "enum", defaultValue: "OUT", displayDuringSetup: false,
                    title: "Calaculate Aggregate 'level' value from:\n[Default: RBGW/OUT Channels Only]",
                    options: ["OUT": "RBGW/OUT Channels Only",
                              "IN" : "IN Channels Only",
                              "ALL": "All Channels"]

            input name: "configLevelSetMode", type: "enum", defaultValue: "SCALE", displayDuringSetup: false,
                    title: "LEVEL SET Mode:\n[Default: SCALE]",
                    options: ["SCALE" : "SCALE individual channel levels",
                              "SIMPLE": "SIMPLE: Set all channels to new level"]

        }

        section { // CHANNEL MAPPING & THRESHOLDS:
            input type: "paragraph", element: "paragraph",
                    title: "CHANNEL MAPPING & THRESHOLDS:", description: "Define how the physical channels map to colours.\n" +
                    "Thresholds define the level at which a channel is considered ON, which can be used to translate an analog input to a binary value."

            input name: "configCh1Threshold", type: "number", range: "0..100", defaultValue: "1", displayDuringSetup: false,
                    title: "Channel #1: Threshold for ON (%):"


            input name: "configCh2Threshold", type: "number", range: "0..100", defaultValue: "1", displayDuringSetup: false,
                    title: "Channel #2: Threshold for ON (%):"

            input name: "configCh3Threshold", type: "number", range: "0..100", defaultValue: "1", displayDuringSetup: false,
                    title: "Channel #3: Threshold for ON (%):"

            input name: "configCh4Threshold", type: "number", range: "0..100", defaultValue: "1", displayDuringSetup: false,
                    title: "Channel #4: Threshold for ON (%):"

        }

        section { // PHYSICAL DEVICE PARAMETERS:
            input type: "paragraph", element: "paragraph",
                    title: "PHYSICAL DEVICE PARAMETERS:", description: "Refer to the Fibaro manual for a full description of the device parameters."

            input name: "configParam01", type: "enum", defaultValue: "255", displayDuringSetup: false,
                    title: "#1: ALL ON/ALL OFF function:\n[Default: 255]",
                    options: ["0"  : "0: ALL ON inactive, ALL OFF inactive",
                              "1"  : "1: ALL ON inactive, ALL OFF active",
                              "2"  : "2: ALL ON active, ALL OFF inactive",
                              "255": "255: ALL ON active, ALL OFF active"]

            input name: "configParam06", type: "enum", defaultValue: "0", displayDuringSetup: false,
                    title: "#6: Associations command class:\n[Default: 0]",
                    description: "Choose which command classes are sent to associated devices.",
                    options: ["0": "0: NORMAL (DIMMER) - BASIC SET/SWITCH_MULTILEVEL_START/STOP",
                              "1": "1: NORMAL (RGBW) - COLOR_CONTROL_SET/START/STOP_STATE_CHANGE",
                              "2": "2: NORMAL (RGBW) - COLOR_CONTROL_SET",
                              "3": "3: BRIGHTNESS - BASIC SET/SWITCH_MULTILEVEL_START/STOP",
                              "4": "4: RAINBOW (RGBW) - COLOR_CONTROL_SET"]

            input name: "configParam08", type: "enum", defaultValue: "0", displayDuringSetup: false,
                    title: "#8: IN/OUT: Outputs state change mode:\n[Default: 0: MODE1]",
                    description: "Choose the behaviour of transitions between different levels.",
                    options: ["0": "0: MODE1 - Constant Speed (speed is defined by parameters 9 and 10)",
                              "1": "1: MODE2 - Constant Time (RGB/RBGW only. Time is defined by parameter 11)"]

            input name: "configParam09", type: "number", range: "1..255", defaultValue: "1", displayDuringSetup: false,
                    title: "#9: MODE1: Step value:\n[Default: 1]",
                    description: "Size of the step for each change in level during the transition."

            input name: "configParam10", type: "number", range: "0..60000", defaultValue: "10", displayDuringSetup: false,
                    title: "#10: MODE1: Time between steps:\n[Default: 10ms]",
                    description: "Time between each step in a transition between levels.  Setting this to zero means an instantaneous change."

            input name: "configParam11", type: "number", range: "0..255", defaultValue: "67", displayDuringSetup: false,
                    title: "#11: MODE2: Time for changing from start to end value:\n" +
                            "[Default: 67 = 3s]\n" +
                            " - 0: immediate change\n" +
                            " - 1-63: 20-126- [ms] value*20ms\n" +
                            " - 65-127: 1-63 [s] [value-64]*1s\n" +
                            " - 129-191: 10-630[s] [value-128]*10s\n" +
                            " - 193-255: 1-63[min] [value-192]*1min"

            input name: "configParam12", type: "number", range: "3..255", defaultValue: "255", displayDuringSetup: false,
                    title: "#12: Maximum brightening level:\n[Default: 255]"

            input name: "configParam13", type: "number", range: "0..254", defaultValue: "2", displayDuringSetup: false,
                    title: "#13: Minimum dim level:\n[Default: 2]"

            input type: "paragraph", element: "paragraph",
                    title: "#14: IN/OUT Channel settings: ", description: "If RGBW mode is chosen, settings for all 4 channels must be identical."

            input name: "configParam14_1", type: "enum", defaultValue: "1", displayDuringSetup: false,
                    title: "CHANNEL 1:\n[Default: 1: RGBW - MOMENTARY (NORMAL MODE)]",
                    options: ["1" : "1: RGBW - MOMENTARY (NORMAL MODE)",
                              "2" : "2: RGBW - MOMENTARY (BRIGHTNESS MODE)",
                              "3" : "3: RGBW - MOMENTARY (RAINBOW MODE)",
                              "4" : "4: RGBW - TOGGLE (NORMAL MODE)",
                              "5" : "5: RGBW - TOGGLE (BRIGHTNESS MODE)",
                              "6" : "6: RGBW - TOGGLE W. MEMORY (NORMAL MODE)",
                              "7" : "7: RGBW - TOGGLE W. MEMORY (BRIGHTNESS MODE)",
                              "8" : "8: IN - ANALOG 0-10V (SENSOR)",
                              "9" : "9: OUT - MOMENTARY (NORMAL MODE)",
                              "12": "12: OUT - TOGGLE (NORMAL MODE)",
                              "14": "14: OUT - TOGGLE W. MEMORY (NORMAL MODE)"]

            input name: "configParam14_2", type: "enum", defaultValue: "1", displayDuringSetup: false,
                    title: "CHANNEL 2:\n[Default: 1: RGBW - MOMENTARY (NORMAL MODE)]",
                    options: ["1" : "1: RGBW - MOMENTARY (NORMAL MODE)",
                              "2" : "2: RGBW - MOMENTARY (BRIGHTNESS MODE)",
                              "3" : "3: RGBW - MOMENTARY (RAINBOW MODE)",
                              "4" : "4: RGBW - TOGGLE (NORMAL MODE)",
                              "5" : "5: RGBW - TOGGLE (BRIGHTNESS MODE)",
                              "6" : "6: RGBW - TOGGLE W. MEMORY (NORMAL MODE)",
                              "7" : "7: RGBW - TOGGLE W. MEMORY (BRIGHTNESS MODE)",
                              "8" : "8: IN - ANALOG 0-10V (SENSOR)",
                              "9" : "9: OUT - MOMENTARY (NORMAL MODE)",
                              "12": "12: OUT - TOGGLE (NORMAL MODE)",
                              "14": "14: OUT - TOGGLE W. MEMORY (NORMAL MODE)"]

            input name: "configParam14_3", type: "enum", defaultValue: "1", displayDuringSetup: false,
                    title: "CHANNEL 3:\n[Default: 1: RGBW - MOMENTARY (NORMAL MODE)]",
                    options: ["1" : "1: RGBW - MOMENTARY (NORMAL MODE)",
                              "2" : "2: RGBW - MOMENTARY (BRIGHTNESS MODE)",
                              "3" : "3: RGBW - MOMENTARY (RAINBOW MODE)",
                              "4" : "4: RGBW - TOGGLE (NORMAL MODE)",
                              "5" : "5: RGBW - TOGGLE (BRIGHTNESS MODE)",
                              "6" : "6: RGBW - TOGGLE W. MEMORY (NORMAL MODE)",
                              "7" : "7: RGBW - TOGGLE W. MEMORY (BRIGHTNESS MODE)",
                              "8" : "8: IN - ANALOG 0-10V (SENSOR)",
                              "9" : "9: OUT - MOMENTARY (NORMAL MODE)",
                              "12": "12: OUT - TOGGLE (NORMAL MODE)",
                              "14": "14: OUT - TOGGLE W. MEMORY (NORMAL MODE)"]

            input name: "configParam14_4", type: "enum", defaultValue: "1", displayDuringSetup: false,
                    title: "CHANNEL 4:\n[Default: 1: RGBW - MOMENTARY (NORMAL MODE)]",
                    options: ["1" : "1: RGBW - MOMENTARY (NORMAL MODE)",
                              "2" : "2: RGBW - MOMENTARY (BRIGHTNESS MODE)",
                              "3" : "3: RGBW - MOMENTARY (RAINBOW MODE)",
                              "4" : "4: RGBW - TOGGLE (NORMAL MODE)",
                              "5" : "5: RGBW - TOGGLE (BRIGHTNESS MODE)",
                              "6" : "6: RGBW - TOGGLE W. MEMORY (NORMAL MODE)",
                              "7" : "7: RGBW - TOGGLE W. MEMORY (BRIGHTNESS MODE)",
                              "8" : "8: IN - ANALOG 0-10V (SENSOR)",
                              "9" : "9: OUT - MOMENTARY (NORMAL MODE)",
                              "12": "12: OUT - TOGGLE (NORMAL MODE)",
                              "14": "14: OUT - TOGGLE W. MEMORY (NORMAL MODE)"]

            input name: "configParam16", type: "enum", defaultValue: "1", displayDuringSetup: false,
                    title: "#16: Memorise device status at power cut:\n[Default: 1: MEMORISE STATUS]",
                    options: ["0": "0: DO NOT MEMORISE STATUS",
                              "1": "1: MEMORISE STATUS"]

            input name: "configParam30", type: "enum", defaultValue: "0", displayDuringSetup: false,
                    title: "#30: Response to ALARM of any type:\n[Default: 0: INACTIVE]",
                    options: ["0": "0: INACTIVE - Device doesn't respond",
                              "1": "1: ALARM ON - Device turns on when alarm is detected",
                              "2": "2: ALARM OFF - Device turns off when alarm is detected",
                              "3": "3: ALARM PROGRAM - Alarm sequence turns on (Parameter #38)"]

            input name: "configParam38", type: "number", range: "1..10", defaultValue: "10", displayDuringSetup: false,
                    title: "#38: Alarm sequence program:\n[Default: 10]"

            input name: "configParam39", type: "number", range: "1..65534", defaultValue: "600", displayDuringSetup: false,
                    title: "#39: Active PROGRAM alarm time:\n[Default: 600s]"

            input name: "configParam42", type: "enum", defaultValue: "0", displayDuringSetup: false,
                    title: "#42: Command class reporting outputs status change:\n[Default: 0]",
                    options: ["0": "0: Reporting as a result of inputs and controllers actions (SWITCHMULTILEVEL)",
                              "1": "1: Reporting as a result of input actions (SWITCH MULTILEVEL)",
                              "2": "2: Reporting as a result of input actions (COLOR CONTROL)"]

            input name: "configParam43", type: "number", range: "1..100", defaultValue: "5", displayDuringSetup: false,
                    title: "#43: Reporting 0-10v analog inputs change threshold:\n[Default: 5 = 0.5V]"

            input name: "configParam44", type: "number", range: "0..65534", defaultValue: "30", displayDuringSetup: false,
                    title: "#44: Power load reporting frequency:\n[Default: 30s]\n" +
                            " - 0: reports are not sent\n" +
                            " - 1-65534: time between reports (s)"

            input name: "configParam45", type: "number", range: "0..254", defaultValue: "10", displayDuringSetup: false,
                    title: "#45: Reporting changes in energy:\n[Default: 10 = 0.1kWh]\n" +
                            " - 0: reports are not sent\n" +
                            " - 1-254: 0.01kWh - 2.54kWh"

            input name: "configParam71", type: "enum", defaultValue: "1", displayDuringSetup: false,
                    title: "#71: Response to BRIGHTNESS set to 0%:\n[Default: 1]",
                    options: ["0": "0: Illumination colour set to white",
                              "1": "1: Last set colour is memorised"]

            input name: "configParam72", type: "number", range: "1..10", defaultValue: "1", displayDuringSetup: false,
                    title: "#72: Start predefined (RGBW) program:\n[Default: 1]\n" +
                            " - 1-10: animation program number"

            input name: "configParam73", type: "enum", defaultValue: "0", displayDuringSetup: false,
                    title: "#73: Triple click action:\n[Default: 0]",
                    options: ["0": "0: NODE INFO control frame is sent",
                              "1": "1: Start favourite program"]

        }

        section { // ASSOCIATION GROUPS:
            input type: "paragraph", element: "paragraph",
                    title: "ASSOCIATION GROUPS:", description: "Enter a comma-delimited list of node IDs for each association group.\n" +
                    "Node IDs must be in decimal format (E.g.: 27,155, ... ).\n" +
                    "Each group allows a maximum of five devices.\n"

            input name: "configAssocGroup01", type: "text", defaultValue: "", displayDuringSetup: false,
                    title: "Association Group #1:"

            input name: "configAssocGroup02", type: "text", defaultValue: "", displayDuringSetup: false,
                    title: "Association Group #2:"

            input name: "configAssocGroup03", type: "text", defaultValue: "", displayDuringSetup: false,
                    title: "Association Group #3:"

            input name: "configAssocGroup04", type: "text", defaultValue: "", displayDuringSetup: false,
                    title: "Association Group #4:"

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
 *  String      description         - The message from the device.
 **/
def parse(description) {
    if (state.debug) log.trace "${device.displayName}: parse(): Parsing raw message: ${description}"

    def result = null
    if (description != "updated") {
        def cmd = zwave.parse(description, getSupportedCommands())
        if (cmd) {
            result = zwaveEvent(cmd)
        } else {
            log.error "${device.displayName}: parse(): Could not parse raw message: ${description}"
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
 *  COMMAND_CLASS_BASIC (0x20) : BasicReport [IGNORED]
 *  https://graph.api.smartthings.com/ide/doc/zwave-utils.html#basicV1
 *
 *  Short   value   0xFF for on, 0x00 for off
 **/
def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if (state.debug) log.trace "${device.displayName}: zwaveEvent(): BasicReport received: ${cmd} (ignored)"
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
def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd, sourceEndPoint = 0) {
    if (state.debug) log.trace "${device.displayName}: zwaveEvent(): SwitchMultilevelReport received from endPoint ${sourceEndPoint}: ${cmd}"
    return zwaveEndPointEvent(sourceEndPoint, Math.round(cmd.value * 255 / 99))
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelStartLevelChange cmd) {
    if (state.debug) log.trace "${device.displayName}: zwaveEvent(): SwitchMultilevelStartLevelChange received: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelStopLevelChange cmd) {
    if (state.debug) log.trace "${device.displayName}: zwaveEvent(): SwitchMultilevelStopLevelChange received: ${cmd}"
}

/**
 *  COMMAND_CLASS_SWITCH_ALL (0x27) : * [IGNORED]
 *
 *  SwitchAll functionality is controlled and reported via device Parameter #1 instead.
 **/
def zwaveEvent(hubitat.zwave.commands.switchallv1.SwitchAllReport cmd) {
    if (state.debug) log.trace "${device.displayName}: zwaveEvent(): SwitchAllReport received: ${cmd}"
}

/**
 *  COMMAND_CLASS_SENSOR_MULTILEVEL (0x31) : SensorMultilevelReport
 *
 *  Appears to be used to report power. Not sure if anything else...?
 **/
def zwaveEvent(hubitat.zwave.commands.sensormultilevelv2.SensorMultilevelReport cmd) {
    if (state.debug) log.trace "${device.displayName}: zwaveEvent(): SensorMultilevelReport received: ${cmd}"

    if (cmd.sensorType == 4 /*SENSOR_TYPE_POWER_VERSION_2*/) { // Instantaneous Power (Watts):
        log.info "${device.displayName}: Power is ${cmd.scaledSensorValue} W"
        return createEvent(name: "power", value: cmd.scaledSensorValue, unit: "W")
    } else {
        log.warn "${device.displayName}: zwaveEvent(): SensorMultilevelReport with unhandled sensorType: ${cmd}"
    }
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
    if (state.debug) log.trace "${device.displayName}: zwaveEvent(): MeterReport received: ${cmd}"

    if (cmd.scale == 0) { // Accumulated Energy (kWh):
        state.energy = cmd.scaledMeterValue
        //sendEvent(name: "dispEnergy", value: String.format("%.2f",cmd.scaledMeterValue as BigDecimal) + " kWh", displayed: false)
        log.info "${device.displayName}: Accumulated energy is ${cmd.scaledMeterValue} kWh"
        return createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
    } else if (cmd.scale == 1) { // Accumulated Energy (kVAh): Ignore.
        //createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kVAh")
    } else if (cmd.scale == 2) { // Instantaneous Power (Watts):
        //sendEvent(name: "dispPower", value: String.format("%.1f",cmd.scaledMeterValue as BigDecimal) + " W", displayed: false)
        log.info "${device.displayName}: Power is ${cmd.scaledMeterValue} W"
        return createEvent(name: "power", value: cmd.scaledMeterValue, unit: "W")
    } else if (cmd.scale == 4) { // Instantaneous Voltage (Volts):
        //sendEvent(name: "dispVoltage", value: String.format("%.1f",cmd.scaledMeterValue as BigDecimal) + " V", displayed: false)
        log.info "${device.displayName}: Voltage is ${cmd.scaledMeterValue} V"
        return createEvent(name: "voltage", value: cmd.scaledMeterValue, unit: "V")
    } else if (cmd.scale == 5) {  // Instantaneous Current (Amps):
        //sendEvent(name: "dispCurrent", value: String.format("%.1f",cmd.scaledMeterValue as BigDecimal) + " A", displayed: false)
        log.info "${device.displayName}: Current is ${cmd.scaledMeterValue} A"
        return createEvent(name: "current", value: cmd.scaledMeterValue, unit: "A")
    } else if (cmd.scale == 6) { // Instantaneous Power Factor:
        //sendEvent(name: "dispPowerFactor", value: "PF: " + String.format("%.2f",cmd.scaledMeterValue as BigDecimal), displayed: false)
        log.info "${device.displayName}: PowerFactor is ${cmd.scaledMeterValue}"
        return createEvent(name: "powerFactor", value: cmd.scaledMeterValue, unit: "PF")
    }
}

// /**
//  *  COMMAND_CLASS_SWITCH_COLOR (0x33) : SwitchColorReport
//  *
//  *  SwitchColorReports tell us the current level of a color channel.
//  *  The value will be in the range 0..255, which is passed to zwaveEndPointEvent().
//  *
//  *  String      colorComponent                  Color name, e.g. "red", "green", "blue".
//  *  Short       colorComponentId                0 = warmWhite, 2 = red, 3 = green, 4 = blue, 5 = coldWhite.
//  *  Short       value                           0x00 to 0xFF
//  **/
// def zwaveEvent(hubitat.zwave.commands.switchcolorv3.SwitchColorReport cmd) {
//     if (state.debug) log.trace "${device.displayName}: zwaveEvent(): SwitchColorReport received: ${cmd}"
//     if (cmd.colorComponentId == 0) { cmd.colorComponentId = 5 } // Remap warmWhite colorComponentId
//     return zwaveEndPointEvent(cmd.colorComponentId, cmd.value)
// }

/**
 *  COMMAND_CLASS_MULTICHANNEL (0x60) : MultiChannelCmdEncap
 *
 *  The MultiChannel Command Class is used to address one or more endpoints in a multi-channel device.
 *  The sourceEndPoint attribute will identify the sub-device/channel the command relates to.
 *  The encpsulated command is extracted and passed to the appropriate zwaveEvent handler.
 **/
def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    if (state.debug) log.trace "${device.displayName}: zwaveEvent(): MultiChannelCmdEncap received: ${cmd}"

    def encapsulatedCommand = cmd.encapsulatedCommand(getSupportedCommands())
    if (!encapsulatedCommand) {
        log.warn "${device.displayName}: zwaveEvent(): MultiChannelCmdEncap from endPoint ${cmd.sourceEndPoint} could not be translated: ${cmd}"
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
    if (state.debug) log.trace "${device.displayName}: zwaveEvent(): ConfigurationReport received: ${cmd}"
    // TODO is this true for hubitat too?
    // Translate cmd.configurationValue to an int. This should be returned from zwave.parse() as
    // cmd.scaledConfigurationValue, but it hasn't been implemented by SmartThings yet! :/
    //  See: https://community.smartthings.com/t/zwave-configurationv2-configurationreport-dev-question/9771
    def scaledConfigurationValue = byteArrayToInt(cmd.configurationValue)
    log.info "${device.displayName}: Parameter #${cmd.parameterNumber} has value: ${cmd.configurationValue} (${scaledConfigurationValue})"
}

/**
 *  COMMAND_CLASS_MANUFACTURER_SPECIFIC (0x72) : ManufacturerSpecificReport
 *
 *  ManufacturerSpecific reports tell us the device's manufacturer ID and product ID.
 **/
def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv1.ManufacturerSpecificReport cmd) {
    if (state.debug) log.trace "${device.displayName}: zwaveEvent(): ManufacturerSpecificReport received: ${cmd}"
    updateDataValue("manufacturerName", "${cmd.manufacturerName}")
    updateDataValue("manufacturerId", "${cmd.manufacturerId}")
    updateDataValue("productId", "${cmd.productId}")
    updateDataValue("productTypeId", "${cmd.productTypeId}")
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
    if (state.debug) log.trace "${device.displayName}: zwaveEvent(): AssociationReport received: ${cmd}"
    log.info "${device.displayName}: Association Group ${cmd.groupingIdentifier} contains nodes: ${cmd.nodeId}"
}

/**
 *  COMMAND_CLASS_VERSION (0x86) : VersionReport
 *
 *  Version reports tell us the device's Z-Wave framework and firmware versions.
 **/
def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    if (state.debug) log.trace "${device.displayName}: zwaveEvent(): VersionReport received: ${cmd}"
    updateDataValue("applicationVersion", "${cmd.applicationVersion}")
    updateDataValue("applicationSubVersion", "${cmd.applicationSubVersion}")
    updateDataValue("zWaveLibraryType", "${cmd.zWaveLibraryType}")
    updateDataValue("zWaveProtocolVersion", "${cmd.zWaveProtocolVersion}")
    updateDataValue("zWaveProtocolSubVersion", "${cmd.zWaveProtocolSubVersion}")
}

// TODO(edalquist) hubitat doesn't fully support this yet:
// parse(): Parsing raw message: zw device: 03, command: 7A02, payload: 01 0F 20 09 00 00 , isMulticast: false
// zwaveEvent(): No handler for command: FirmwareMdReport(checksum:null, firmwareId:null, manufacturerId:null)
//
/**
 *  COMMAND_CLASS_FIRMWARE_UPDATE_MD (0x7A) : FirmwareMdReport
 *
 *  Firmware Meta Data reports tell us the device's firmware version and manufacturer ID.
 **/
def zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd) {
    if (state.debug) log.trace "${device.displayName}: zwaveEvent(): FirmwareMdReport received: ${cmd}"
    updateDataValue("firmwareChecksum", "${cmd.checksum}")
    updateDataValue("firmwareId", "${cmd.firmwareId}")
    updateDataValue("manufacturerId", "${cmd.manufacturerId}")
}

/**
 *  Default zwaveEvent handler.
 *
 *  Called for all Z-Wave events that aren't handled above.
 **/
def zwaveEvent(hubitat.zwave.Command cmd) {
    log.error "${device.displayName}: zwaveEvent(): No handler for command: ${cmd}"
    //log.error "${device.displayName}: zwaveEvent(): Class is: ${cmd.getClass()}" // This causes an error, but still gives us the class in the error message. LOL!
}

/**********************************************************************
 *  SmartThings Platform Commands:
 **********************************************************************/

def VERSION = 6

/**
 *  installed() - runs when driver is installed, via pair or virtual.
 **/
def installed() {
    log.trace "installed(${VERSION})"

    state.debug = true
    state.installedAt = now()
    state.lastReset = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
    state.channelThresholds = [null, 1, 1, 1, 1]
    state.channelModes = [null, 1, 1, 1, 1]

    // Initialise attributes:
    sendEvent(name: "switch", value: "off", displayed: false)
    sendEvent(name: "level", value: 0, unit: "%", displayed: false)
    sendEvent(name: "hue", value: 0, unit: "%", displayed: false)
    sendEvent(name: "saturation", value: 0, unit: "%", displayed: false)
    sendEvent(name: "colorName", value: "custom", displayed: false)
    sendEvent(name: "color", value: "[]", displayed: false)
    sendEvent(name: "activeProgram", value: 0, displayed: false)
    sendEvent(name: "energy", value: 0, unit: "kWh", displayed: false)
    sendEvent(name: "power", value: 0, unit: "W", displayed: false)
    sendEvent(name: "lastReset", value: state.lastReset, displayed: false)

    (1..4).each { channel ->
        sendEvent(name: "switchCh${channel}", value: "off", displayed: false)
        sendEvent(name: "levelCh${channel}", value: 0, unit: "%", displayed: false)
    }

    state.installVersion = VERSION;
}

/**
 *  updated() - Runs after device settings have been changed in the SmartThings GUI (and/or IDE?).
 **/
def updated() {
    log.trace "${device.displayName}: updated()"

    if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 2000) {
        state.updatedLastRanAt = now()

        // Make sure installation has completed:
        if (!state.installVersion || state.installVersion != VERSION) {
            installed()
        } else {
            log.trace "No install " + state.installVersion
        }

        // TODO debug is always on right now
        // state.debug = ("true" == configDebugMode)
        state.debug = true

        // Convert channel thresholds to a map:
        def cThresholds = []
        cThresholds[1] = getPreference("configCh1Threshold").toInteger()
        cThresholds[2] = getPreference("configCh2Threshold").toInteger()
        cThresholds[3] = getPreference("configCh3Threshold").toInteger()
        cThresholds[4] = getPreference("configCh4Threshold").toInteger()
        state.channelThresholds = cThresholds

        // Convert channel modes to a map:
        def cModes = []
        cModes[1] = getPreference("configParam14_1").toInteger()
        cModes[2] = getPreference("configParam14_2").toInteger()
        cModes[3] = getPreference("configParam14_3").toInteger()
        cModes[4] = getPreference("configParam14_4").toInteger()
        state.channelModes = cModes

        // Validate Paramter #14 settings:
        state.isRGBW = (state.channelModes[1] < 8) || (state.channelModes[2] < 8) || (state.channelModes[3] < 8) || (state.channelModes[4] < 8)
        state.isIN = (state.channelModes[1] == 8) || (state.channelModes[2] == 8) || (state.channelModes[3] == 8) || (state.channelModes[4] == 8)
        state.isOUT = (state.channelModes[1] > 8) || (state.channelModes[2] > 8) || (state.channelModes[3] > 8) || (state.channelModes[4] > 8)
        if (state.isRGBW & ((state.channelModes[1] != state.channelModes[2]) || (state.channelModes[1] != state.channelModes[3]) || (state.channelModes[1] != state.channelModes[4]))) {
            log.warn "${device.displayName}: updated(): Invalid combination of RGBW channels detected. All RGBW channels should be identical. You may get weird behaviour!"
        }
        if (state.isRGBW & (state.isIN || state.isOUT)) log.warn "${device.displayName}: updated(): Invalid combination of RGBW and IN/OUT channels detected. You may get weird behaviour!"

        // Call configure() and refresh():
        cmds = configure() + refresh()
        log.debug "Sending update commands: ${cmds}"
        return cmds
    } else {
        log.debug "updated(): Ran within last 2 seconds so aborting."
    }
}

/**
 *  configure() - runs when driver is installed, after installed is run. if capability Configuration exists, a Configure command is added to the ui.
 *
 *  Uses values from device preferences.
 **/
def configure() {
    if (state.debug) log.trace "${device.displayName}: configure()"

    def cmds = []

    // Note: Parameters #10,#14,#39,#44 have size: 2!
    // can't use scaledConfigurationValue to set parameters with size < 1 as there is a bug in the configurationV1.configurationSet class.
    //  See: https://community.smartthings.com/t/zwave-configurationv2-configurationreport-dev-question/9771
    // Instead, must use intToUnsignedByteArray(number,size) to convert to an unsigned byteArray manually.
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, configurationValue: [getPreference("configParam01").toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 6, size: 1, configurationValue: [getPreference("configParam06").toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, configurationValue: [getPreference("configParam08").toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 9, size: 1, configurationValue: [getPreference("configParam09").toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 10, size: 2, configurationValue: intToUnsignedByteArray(getPreference("configParam10").toInteger(), 2)).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 11, size: 1, configurationValue: [getPreference("configParam11").toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 12, size: 1, configurationValue: [getPreference("configParam12").toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 13, size: 1, configurationValue: [getPreference("configParam13").toInteger()]).format()
    //  Parameter #14 needs to be reconstituted from each 4-bit channel value.
    def p14A = (getPreference("configParam14_1").toInteger() * 0x10) + getPreference("configParam14_2").toInteger()
    def p14B = (getPreference("configParam14_3").toInteger() * 0x10) + getPreference("configParam14_4").toInteger()
    if (state.debug) log.debug "${device.displayName}: configure(): Setting Parameter #14 to: [${p14A},${p14B}]"
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 14, size: 2, configurationValue: [p14A.toInteger(), p14B.toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 16, size: 1, configurationValue: [getPreference("configParam16").toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 30, size: 1, configurationValue: [getPreference("configParam30").toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 38, size: 1, configurationValue: [getPreference("configParam38").toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 39, size: 2, configurationValue: intToUnsignedByteArray(getPreference("configParam39").toInteger(), 2)).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 42, size: 1, configurationValue: [getPreference("configParam42").toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 43, size: 1, configurationValue: [getPreference("configParam43").toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 44, size: 2, configurationValue: intToUnsignedByteArray(getPreference("configParam44").toInteger(), 2)).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 45, size: 1, configurationValue: [getPreference("configParam45").toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 71, size: 1, configurationValue: [getPreference("configParam71").toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 72, size: 1, configurationValue: [getPreference("configParam72").toInteger()]).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 73, size: 1, configurationValue: [getPreference("configParam73").toInteger()]).format()

    // Association Groups:
    cmds << zwave.associationV2.associationRemove(groupingIdentifier: 1, nodeId: []).format()
    cmds << zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: parseAssocGroup(configAssocGroup01, 5)).format()
    cmds << zwave.associationV2.associationRemove(groupingIdentifier: 2, nodeId: []).format()
    cmds << zwave.associationV2.associationSet(groupingIdentifier: 2, nodeId: parseAssocGroup(configAssocGroup02, 5)).format()
    cmds << zwave.associationV2.associationRemove(groupingIdentifier: 3, nodeId: []).format()
    cmds << zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: parseAssocGroup(configAssocGroup03, 5)).format()
    cmds << zwave.associationV2.associationRemove(groupingIdentifier: 4, nodeId: []).format()
    cmds << zwave.associationV2.associationSet(groupingIdentifier: 4, nodeId: parseAssocGroup(configAssocGroup04, 5)).format()
    cmds << zwave.associationV2.associationRemove(groupingIdentifier: 5, nodeId: []).format()
    cmds << zwave.associationV2.associationSet(groupingIdentifier: 5, nodeId: [zwaveHubNodeId]).format()
    // Add the SmartThings hub (controller) to Association Group #5.

    log.warn "${device.displayName}: configure(): Device Parameters are being updated. It is recommended to power-cycle the Fibaro device once completed."

    cmds = delayBetween(cmds, 500) + getConfigReport()
    log.debug "Sending configure commands: ${cmds}"
    return cmds
}

/**
 * runs first time driver loads, ie system startup when capability Initialize exists, a Initialize command is added to the ui.
 */
def initialize() {
    if (state.debug) log.trace "${device.displayName}: initialize()"
}

/**********************************************************************
 *  Capability-related Commands:
 **********************************************************************/

/**
 *  on() - Turn the switch on. [Switch Capability]
 *
 *  Only sends commands to RGBW/OUT channels to avoid altering the levels of INPUT channels.
 **/
def on() {
    log.info "${device.displayName}: on()"

    def cmds = []

    (1..4).each { channel ->
        if (8 != state.channelModes[channel]) {
            cmds << onChX(channel)
        }
    }

    return cmds.flatten()
}

/**
 *  off() - Turn the switch off. [Switch Capability]
 *
 *  Only sends commands to RGBW/OUT channels to avoid altering the levels of INPUT channels.
 **/
def off() {
    log.info "${device.displayName}: off()"

    def cmds = []
    (1..4).each { i ->
        if (8 != state.channelModes[i]) {
            cmds << offChX(i)
        }
    }

    return cmds.flatten()
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
    if (state.debug) log.trace "${device.displayName}: setLevel(): Level: ${level}"
    if (level > 100) level = 100
    if (level < 0) level = 0

    def cmds = []

    if ("SCALE" == configLevelSetMode) { // SCALE Mode:
        float currentMaxOutLevel = 0.0
        (1..4).each { i ->
            if (8 != state.channelModes[i]) {
                currentMaxOutLevel = Math.max(currentMaxOutLevel, device.latestValue("levelCh${i}").toInteger())
            }
        }

        if (0.0 == currentMaxOutLevel) { // All OUT levels are currently zero, so just set all to the new level:
            (1..4).each { i ->
                if (8 != state.channelModes[i]) {
                    cmds << setLevelChX(level.toInteger(), i)
                }
            }
        } else { // Scale the individual channel levels:
            float s = level / currentMaxOutLevel
            (1..4).each { i ->
                if (8 != state.channelModes[i]) {
                    cmds << setLevelChX((device.latestValue("levelCh${i}") * s).toInteger(), i)
                }
            }
        }
    } else { // SIMPLE Mode:
        (1..4).each { i ->
            if (8 != state.channelModes[i]) {
                cmds << setLevelChX(level.toInteger(), i)
            }
        }
    }

    return cmds.flatten()
}

/**
 *  poll() - Polls the device. [Polling Capability]
 *
 *  The SmartThings platform seems to poll devices randomly every 6-8mins.
 **/
def poll() {
    if (state.debug) log.trace "${device.displayName}: poll()"
    refresh()
}

/**
 *  refresh() - Refreshes values from the physical device. [Refresh Capability]
 **/
def refresh() {
    if (state.debug) log.trace "${device.displayName}: refresh()"
    def cmds = []

    cmds << zwave.switchAllV1.switchAllGet().format()

    // if (state.isIN) { // There are INPUT channels, so we must get channel levels using switchMultilevelGet: ch1 is the composite/average/all value
    (2..5).each {
        cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: it).encapsulate(zwave.switchMultilevelV1.switchMultilevelGet()).format()
    }
    // }
    // else { // There are no INPUT channels, so we can use switchColorGet for greater accuracy:
    //  (0..4).each { cmds << zwave.switchColorV3.switchColorGet(colorComponentId: it).format() }
    // }

    cmds << zwave.meterV3.meterGet(scale: 0).format() // Get energy MeterReport
    cmds << zwave.meterV3.meterGet(scale: 2).format() // Get power MeterReport
    delayBetween(cmds, 200)
}

/**********************************************************************
 *  Custom Commands:
 **********************************************************************/

/**
 *  reset() - Reset Accumulated Energy.
 **/
def reset() {
    if (state.debug) log.trace "${device.displayName}: reset()"

    state.lastReset = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
    sendEvent(name: "lastReset", value: state.lastReset)

    return [
            zwave.meterV3.meterReset().format(),
            zwave.meterV3.meterGet(scale: 0).format()
    ]
}

/**
 *  on*() - Set switch for an individual channel to "on".
 *
 *  These commands all map to onChX().
 **/
def onCh1() { onChX(1) }

def onCh2() { onChX(2) }

def onCh3() { onChX(3) }

def onCh4() { onChX(4) }

/**
 *  off*() - Set switch for an individual channel to "off".
 *
 *  These commands all map to offChX().
 **/
def offCh1() { offChX(1) }

def offCh2() { offChX(2) }

def offCh3() { offChX(3) }

def offCh4() { offChX(4) }

/**
 *  setLevel*() - Set level of an individual channel.
 *
 *  These commands all map to setLevelChX().
 **/
def setLevelCh1(level, rate = 1) { setLevelChX(level, 1) }

def setLevelCh2(level, rate = 1) { setLevelChX(level, 2) }

def setLevelCh3(level, rate = 1) { setLevelChX(level, 3) }

def setLevelCh4(level, rate = 1) { setLevelChX(level, 4) }

/**
 *  startProgram(programNumber) - Start a built-in animation program.
 **/
def startProgram(programNumber) {
    if (state.debug) log.trace "${device.displayName}: startProgram(): programNumber: ${programNumber}"

    if (state.isIN | state.isOUT) {
        log.warn "${device.displayName}: Built-in programs work with RGBW channels only, they will not function when using IN/OUT channels!"
    } else if (programNumber > 0 & programNumber <= 10) {
        (1..4).each {
            sendEvent(name: "savedLevelCh${it}", value: device.latestValue("levelCh${it}").toInteger(), displayed: false)
        } // Save levels for all channels.
        sendEvent(name: "activeProgram", value: programNumber, displayed: false)
        sendEvent(name: "colorName", value: "program")
        return zwave.configurationV1.configurationSet(configurationValue: [programNumber], parameterNumber: 72, size: 1).format()
    } else {
        log.warn "${device.displayName}: startProgram(): Invalid programNumber: ${programNumber}"
    }
}

/**
 *  start*() - Start built-in animation program by name.
 **/
def startFireplace() { startProgram(6) }

def startStorm() { startProgram(7) }

def startDeepFade() { startProgram(8) }

def startLiteFade() { startProgram(9) }

def startPolice() { startProgram(10) }

/**
 *  stopProgram() - Stop animation program (if running).
 **/
def stopProgram() {
    if (state.debug) log.trace "${device.displayName}: startProgram()"

    sendEvent(name: "activeProgram", value: 0, displayed: false)
    return on() // on() will automatically restore levels.
}

/**********************************************************************
 *  Private Helper Methods:
 **********************************************************************/

private getPreference(prefName) {
    log.trace "getPreference(${prefName})"

    def setValue = settings[prefName]
    if (setValue != null) {
        log.trace "Returning set value ${prefName}=${setValue}"
        return setValue
    }

    for (section in preferences?.sections) {
        for (input in section?.input) {
            if (input.name == prefName) {
                log.trace "Returning default value ${prefName}=${input.defaultValue}"
                return input.defaultValue
            }
        }
    }

    return null
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
            0x33: 1, // Color Control Command Class (V1)
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
    ]
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
 *  intToUnsignedByteArray(number, size)
 *
 *  Converts an unsigned int to an unsigned byte array of set size.
 **/
private intToUnsignedByteArray(number, size) {
    if (number < 0) {
        log.error "${device.displayName}: intToUnsignedByteArray(): Doesn't work with negative number: ${number}"
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
 * parseAssocGroup(string, maxNodes)
 *
 *  Converts a comma-delimited string into a list of integers.
 *  Checks that all elements are integer numbers, and removes any that are not.
 *  Checks that the final list contains no more than maxNodes.
 */
private parseAssocGroup(string, maxNodes) {
    if (state.debug) log.trace "${device.displayName}: parseAssocGroup(): Translating string: ${string}"

    if (string) {
        def nodeList = string.split(',')
        nodeList = nodeList.collect { node ->
            if (node.isInteger()) {
                node.toInteger()
            } else {
                log.warn "${device.displayName}: parseAssocGroup(): Cannot parse: ${node}"
            }
        }
        nodeList = nodeList.findAll() // findAll() removes the nulls.
        if (nodeList.size() > maxNodes) {
            log.warn "${device.displayName}: parseAssocGroup(): Number of nodes is greater than ${maxNodes}!"
        }
        return nodeList.take(maxNodes)
    } else {
        return []
    }
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
    if (state.debug) log.trace "${device.displayName}: zwaveEndPointEvent(): EndPoint ${sourceEndPoint} has value: ${value}"

    def channel = sourceEndPoint - 1
    def percent = Math.round(value * 100 / 255)

    if (1 == sourceEndPoint) { // EndPoint 1 is the aggregate channel, which is calculated later. IGNORE.
        if (state.debug) log.debug "${device.displayName}: zwaveEndPointEvent(): MultiChannelCmdEncap from endpoint 1 (aggregate) ignored ${percent}%."
    } else if ((sourceEndPoint > 1) & (sourceEndPoint < 6)) { // Physical channel #1..4

        // Update level:
        log.info "${device.displayName}: Channel ${channel} level is ${percent}%."
        sendEvent(name: "levelCh${channel}", value: percent, unit: "%")

        // Update switch:
        if (percent >= state.channelThresholds[channel].toInteger()) {
            log.info "${device.displayName}: Channel ${channel} is on."
            sendEvent(name: "switchCh${channel}", value: "on")
        } else {
            log.info "${device.displayName}: Channel ${channel} is off."
            sendEvent(name: "switchCh${channel}", value: "off")
        }
    } else {
        log.warn "${device.displayName}: SwitchMultilevelReport recieved from unknown endpoint: ${sourceEndPoint}"
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
    log.info "${device.displayName}: Switch is ${newSwitch}."
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
    log.info "${device.displayName}: Level is ${newLevel}."
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
    log.info "${device.displayName}: onX(): Setting channel ${channel} switch to on."

    def cmds = []
    if (channel < 1 || channel > 4) {
        log.warn "${device.displayName}: onX(): Channel ${channel} does not exist!"
    } else if (8 == state.channelModes[channel]) {
        log.warn "${device.displayName}: onX(): Channel ${channel} is an INPUT channel. Command not sent."
        cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: (channel + 1))
                .encapsulate(zwave.switchMultilevelV1.switchMultilevelGet()).format() // Endpoint = channel + 1
    } else {
        def newLevel = device.latestValue("savedLevelCh${channel}") ?: 100
        newLevel = (0 == newLevel.toInteger()) ? 99 : Math.round(newLevel.toInteger() * 99 / 100)
        // scale level for switchMultilevelSet.
        log.info "${device.displayName}: onX(): Channel ${channel} to: ${newLevel}"

        cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: (channel + 1))
                .encapsulate(zwave.switchMultilevelV1.switchMultilevelSet(value: newLevel.toInteger())).format()
        // Endpoint = channel + 1
        sendEvent(name: "savedLevelCh${channel}", value: null) // Wipe savedLevel.
        sendEvent(name: "activeProgram", value: 0) // Wipe activeProgram.
    }

    log.trace "Running " + cmds.size() + " commands: " + cmds
    return cmds
}

/**
 *  offChX() - Set switch for an individual channel to "off".
 *
 *  If channel is RGBW/OUT, save the level and turn off.
 *  If channel is an INPUT channel, don't issue command. Log warning instead.
 **/
private offChX(channel) {
    log.info "${device.displayName}: offX(): Setting channel ${channel} switch to off."

    def cmds = []
    if (channel > 4 || channel < 1) {
        log.warn "${device.displayName}: offX(): Channel ${channel} does not exist!"
    } else if (8 == state.channelModes[channel]) {
        log.warn "${device.displayName}: offX(): Channel ${channel} is an INPUT channel. Command not sent."
        cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: (channel + 1))
                .encapsulate(zwave.switchMultilevelV1.switchMultilevelGet()).format() // endPoint = channel + 1
    } else {
        sendEvent(name: "savedLevelCh${channel}", value: device.latestValue("levelCh${channel}").toInteger())
        // Save level to 'hidden' attribute.
        sendEvent(name: "activeProgram", value: 0) // Wipe activeProgram.
        log.info "${device.displayName}: onX(): Channel ${channel} to: 0"
        cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: (channel + 1))
                .encapsulate(zwave.switchMultilevelV1.switchMultilevelSet(value: 0)).format() // endPoint = channel + 1
    }

    log.trace "Running " + cmds.size() + " commands: " + cmds
    return cmds
}

/**
 *  setLevelChX() - Set level of an individual channel.
 *
 *  If channel is an INPUT channel, don't issue command. Log warning instead.
 *
 *  The Fibaro RGBW Controller does not support dimmingDuration. Instead,
 *  dimming durations are configured using device parameters (8/9/10/11).
 *
 **/
private setLevelChX(level, channel) {
    log.info "${device.displayName}: setLevelChX(): Setting channel ${channel} to level: ${level}."

    def cmds = []
    if (channel > 4 || channel < 1) {
        log.warn "${device.displayName}: setLevelChX(): Channel ${channel} does not exist!"
    } else if (8 == state.channelModes[channel]) {
        log.warn "${device.displayName}: setLevelChX(): Channel ${channel} is an INPUT channel. Command not sent."
    } else {
        if (level < 0) level = 0
        if (level > 100) level = 100
        level = Math.round(level * 99 / 100) // scale level for switchMultilevelSet.
        cmds << zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: (channel + 1)).encapsulate(zwave.switchMultilevelV1.switchMultilevelSet(value: level.toInteger())).format()
        // Endpoint = channel + 1
        sendEvent(name: "savedLevelCh${channel}", value: null) // Wipe savedLevel.
        sendEvent(name: "activeProgram", value: 0) // Wipe activeProgram.
    }

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
    if (state.debug) log.trace "${device.displayName}: getConfigReport()"
    def cmds = []

    cmds << zwave.configurationV1.configurationGet(parameterNumber: 1).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 6).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 8).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 9).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 10).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 11).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 12).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 13).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 14).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 16).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 30).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 38).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 39).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 42).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 43).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 44).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 45).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 71).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 72).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 73).format()

    // Request Association Reports:
    cmds << zwave.associationV2.associationGet(groupingIdentifier: 1).format()
    cmds << zwave.associationV2.associationGet(groupingIdentifier: 2).format()
    cmds << zwave.associationV2.associationGet(groupingIdentifier: 3).format()
    cmds << zwave.associationV2.associationGet(groupingIdentifier: 4).format()
    cmds << zwave.associationV2.associationGet(groupingIdentifier: 5).format()

    // Request Manufacturer, Version, Firmware Reports:
    cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
    cmds << zwave.versionV1.versionGet().format()
    cmds << zwave.firmwareUpdateMdV2.firmwareMdGet().format()

    cmds = delayBetween(cmds, 200)
    log.debug "Sending getConfigReport commands: ${cmds}"
    return cmds
}
