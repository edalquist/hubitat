/*
    Basic Z-Wave tool

    Copyright 2016, 2017, 2018 Hubitat Inc.  All Rights Reserved

    2018-11-28 maxwell
        -add command hints
    2018-11-09 maxwell
        -add association and version reports

    usage:
        -replace existing driver with this driver
        -set your paremeters
        -replace this driver with previous driver

    WARNING!
        --Setting device parameters is an advanced feature, randomly poking values to a device
        can lead to unexpected results which may result in needing to perform a factory reset
        or possibly bricking the device
        --Refer to the device documentation for the correct parameters and values for your specific device
        --Hubitat cannot be held responsible for misuse of this tool or any unexpected results generated by its use
*/

import groovy.transform.Field

metadata {
    definition(name: "Basic Z-Wave tool", namespace: "hubitat", author: "Mike Maxwell") {

        command "getAssociationReport"
        command "getVersionReport"
        command "getCommandClassReport"
        command "getParameterReport", [[name: "parameterNumber", type: "NUMBER", description: "Parameter Number (omit for a complete listing of parameters that have been set)", constraints: ["NUMBER"]]]
        command "setParameter", [[name: "parameterNumber", type: "NUMBER", description: "Parameter Number", constraints: ["NUMBER"]], [name: "size", type: "NUMBER", description: "Parameter Size", constraints: ["NUMBER"]], [name: "value", type: "NUMBER", description: "Parameter Value", constraints: ["NUMBER"]]]

    }
}

def parse(String description) {
    log.debug description
    def cmd = zwave.parse(description, [0x85: 1, 0x86: 1])
    if (cmd) {
        zwaveEvent(cmd)
    }
}

//Z-Wave responses
def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    log.info "VersionReport- zWaveLibraryType:${zwLibType.find { it.key == cmd.zWaveLibraryType }.value}"
    log.info "VersionReport- zWaveProtocolVersion:${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
    log.info "VersionReport- applicationVersion:${cmd.applicationVersion}.${cmd.applicationSubVersion}"
}

def zwaveEvent(hubitat.zwave.commands.associationv1.AssociationReport cmd) {
    log.info "AssociationReport- groupingIdentifier:${cmd.groupingIdentifier}, maxNodesSupported:${cmd.maxNodesSupported}, nodes:${cmd.nodeId}"
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    log.info "ConfigurationReport- parameterNumber:${cmd.parameterNumber}, size:${cmd.size}, value:${cmd.scaledConfigurationValue}"
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionCommandClassReport cmd) {
    cmdClassHex = "0x${intToHexStr(cmd.requestedCommandClass)}"
    log.info "CommandClassReport- class:${cmdClassHex} - ${zwCmdClass[cmdClassHex]}, version:${cmd.commandClassVersion}"
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    def encapCmd = cmd.encapsulatedCommand()
    def result = []
    if (encapCmd) {
        result += zwaveEvent(encapCmd)
    } else {
        log.warn "Unable to extract encapsulated cmd from ${cmd}"
    }
    return result
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    log.debug "skip: ${cmd}"
}

//cmds
def getVersionReport() {
    cmds = secureCmd(zwave.versionV1.versionGet())
    log.debug "getVersionReport: " + cmds
    return cmds
}

def setParameter(parameterNumber = null, size = null, value = null) {
    if (parameterNumber == null || size == null || value == null) {
        log.warn "incomplete parameter list supplied..."
        log.info "syntax: setParameter(parameterNumber,size,value)"
    } else {
        cmds = delayBetween([
                secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: parameterNumber, size: size)),
                secureCmd(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber))
        ], 500)
        log.debug "setParameter: " + cmds
        return cmds
    }
}

def getAssociationReport() {
    def cmds = []
    1.upto(5, {
        cmds.add(secureCmd(zwave.associationV1.associationGet(groupingIdentifier: it)))
    })
    log.debug "getAssociationReport: " + cmds
    return cmds
}

def getParameterReport(param = null) {
    def cmds = []
    if (param) {
        cmds = [secureCmd(zwave.configurationV1.configurationGet(parameterNumber: param))]
    } else {
        0.upto(255, {
            cmds.add(secureCmd(zwave.configurationV1.configurationGet(parameterNumber: it)))
        })
    }
    log.debug "getParameterReport: " + cmds
    return cmds
}

def getCommandClassReport() {
    def cmds = []
    def ic = getDataValue("inClusters").split(",").collect { hexStrToUnsignedInt(it) }
    ic.each {
        if (it) cmds.add(secureCmd(zwave.versionV1.versionCommandClassGet(requestedCommandClass: it)))
    }
    cmds = delayBetween(cmds, 500)
    log.debug "getParameterReport: " + cmds
    return cmds
}

def installed() {}

def configure() {}

def updated() {}

private secureCmd(cmd) {
    if (getDataValue("zwaveSecurePairingComplete") == "true") {
        return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        return cmd.format()
    }
}

@Field Map zwLibType = [
        0 : "N/A",
        1 : "Static Controller",
        2 : "Controller",
        3 : "Enhanced Slave",
        4 : "Slave",
        5 : "Installer",
        6 : "Routing Slave",
        7 : "Bridge Controller",
        8 : "Device Under Test (DUT)",
        9 : "N/A",
        10: "AV Remote",
        11: "AV Device"
]

@Field Map zwCmdClass = [
        "0x00": "No Operation",
        "0x01": "Zwave Cmd Class",
        "0x02": "Zensor Net",
        "0x20": "Basic",
        "0x21": "Controller Replication",
        "0x22": "Application Status",
        "0x23": "Zip",
        "0x24": "Security Panel Mode",
        "0x25": "Switch Binary",
        "0x26": "Switch Multilevel",
        "0x27": "Switch All",
        "0x28": "Switch Toggle Binary",
        "0x29": "Switch Toggle Multilevel",
        "0x2A": "Chimney Fan",
        "0x2B": "Scene Activation",
        "0x2C": "Scene Actuator Conf",
        "0x2D": "Scene Controller Conf",
        "0x2E": "Security Panel Zone",
        "0x2F": "Security Panel Zone Sensor",
        "0x30": "Sensor Binary",
        "0x31": "Sensor Multilevel",
        "0x32": "Meter",
        "0x33": "Color Control",
        "0x34": "Network Management Inclusion",
        "0x35": "Meter Pulse",
        "0x36": "Basic Tariff Info",
        "0x37": "Hrv Status",
        "0x38": "Thermostat Heating",
        "0x39": "Hrv Control",
        "0x3A": "Dcp Config",
        "0x3B": "Dcp Monitor",
        "0x3C": "Meter Tbl Config",
        "0x3D": "Meter Tbl Monitor",
        "0x3E": "Meter Tbl Push",
        "0x3F": "Prepayment",
        "0x40": "Thermostat Mode",
        "0x41": "Prepayment Encapsulation",
        "0x42": "Thermostat Operating State",
        "0x43": "Thermostat Setpoint",
        "0x44": "Thermostat Fan Mode",
        "0x45": "Thermostat Fan State",
        "0x46": "Climate Control Schedule",
        "0x47": "Thermostat Setback",
        "0x48": "Rate Tbl Config",
        "0x49": "Rate Tbl Monitor",
        "0x4A": "Tariff Config",
        "0x4B": "Tariff Tbl Monitor",
        "0x4C": "Door Lock Logging",
        "0x4D": "Network Management Basic",
        "0x4E": "Schedule Entry Lock",
        "0x4F": "Zip6lowpan",
        "0x50": "Basic Window Covering",
        "0x51": "Mtp Window Covering",
        "0x52": "Network Management Proxy",
        "0x53": "Schedule",
        "0x54": "Network Management Primary",
        "0x55": "Transport Service",
        "0x56": "Crc16 Encap",
        "0x57": "Application Capability",
        "0x58": "Zip Nd",
        "0x59": "Association Grp Info",
        "0x5A": "Device Reset Locally",
        "0x5B": "Central Scene",
        "0x60": "Multi Channel",
        "0x60": "Multi Instance",
        "0x62": "Door Lock",
        "0x63": "User Code",
        "0x70": "Configuration",
        "0x71": "Alarm",
        "0x71": "Notification",
        "0x72": "Manufacturer Specific",
        "0x73": "Powerlevel",
        "0x75": "Protection",
        "0x76": "Lock",
        "0x77": "Node Naming",
        "0x7A": "Firmware Update Md",
        "0x7B": "Grouping Name",
        "0x7C": "Remote Association Activate",
        "0x7D": "Remote Association",
        "0x80": "Battery",
        "0x81": "Clock",
        "0x82": "Hail",
        "0x84": "Wake Up",
        "0x85": "Association",
        "0x86": "Version",
        "0x87": "Indicator",
        "0x88": "Proprietary",
        "0x89": "Language",
        "0x8A": "Time",
        "0x8B": "Time Parameters",
        "0x8C": "Geographic Location",
        "0x8E": "Multi Channel Association",
        "0x8E": "Multi Instance Association",
        "0x8F": "Multi Cmd",
        "0x90": "Energy Production",
        "0x91": "Manufacturer Proprietary",
        "0x92": "Screen Md",
        "0x93": "Screen Attributes",
        "0x94": "Simple Av Control",
        "0x95": "Av Content Directory Md",
        "0x96": "Av Renderer Status",
        "0x97": "Av Content Search Md",
        "0x98": "Security",
        "0x99": "Av Tagging Md",
        "0x9A": "Ip Configuration",
        "0x9B": "Association Command Configuration",
        "0x9C": "Sensor Alarm",
        "0x9D": "Silence Alarm",
        "0x9E": "Sensor Configuration",
        "0xEF": "Mark",
        "0xF0": "Non Interoperable"
]