/**
 *  HS-FS100+
 *
 *  Copyright 2019 HomeSeer
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * Version 1.0 2/14/19
 */
metadata {
  definition (
    name: "HomeSeer Flex Sensor", 
    namespace: "HomeSeer", 
    author: "support@homeseer.com"
  ) {
    capability "Water Sensor" 
    capability "Battery"
    capability "Configuration"
    capability "Refresh"
    capability "Temperature Measurement"
    
    attribute "lastCheckin", "string"   
    
    fingerprint mfr:"000C", prod:"0202", model:"0001"
  }
  
  preferences {   
    input "tempReporting", "enum",
      title: "Temperature Reporting Interval:",
      defaultValue: tempReportingSetting,
      required: false,
      displayDuringSetup: false,
      options: tempReportingOptions.collect { it.name }
        input "lightSensitivity", "enum",
      title: "Light Sensitivity (with light cable):",
      defaultValue: lightSensitivitySetting,
      required: false,
      displayDuringSetup: false,
      options: lightSensitivityOptions.collect { it.name }
        input "notificationBuzzer", "enum",
      title: "Notification Buzzer:",
      defaultValue: notificationBuzzerSetting,
      required: false,
      displayDuringSetup: false,
      options: notificationBuzzerOptions.collect { it.name }
        input "lightDetectionDelay", "enum",
      title: "Light Detection Delay (detect blinking):",
      defaultValue: lightDetectionDelaySetting,
      required: false,
      displayDuringSetup: false,
      options: lightDetectionDelayOptions.collect { it.name }
        input "waterDetectionBuzzerFrequency", "enum",
      title: "Water Detection Buzzer Frequency (with water cable):",
      defaultValue: waterDetectionBuzzerFrequencySetting,
      required: false,
      displayDuringSetup: false,
      options: waterDetectionBuzzerFrequencyOptions.collect { it.name }
            
            
  }
}


// Sets flag so that configuration is updated the next time it wakes up.
def updated() { 
  // This method always gets called twice when preferences are saved.
  if (!isDuplicateCommand(state.lastUpdated, 3000)) {   
    state.lastUpdated = new Date().time
    logTrace "updated()"


    logForceWakeupMessage "The configuration will be updated the next time the device wakes up."
    state.pendingChanges = true
  }   
}


// Initializes the device state when paired and updates the device's configuration.
def configure() {
  logTrace "configure()"
  def cmds = []
  def refreshAll = (!state.isConfigured || state.pendingRefresh || !settings?.ledEnabled)
  
  if (!state.isConfigured) {
    //logTrace "Waiting 1 second because this is the first time being configured"   
    sendEvent(getEventMap("light", "nolight", false))   
    //cmds << "delay 1000"
  }
  
  configData.sort { it.paramNum }.each { 
    cmds += updateConfigVal(it.paramNum, it.size, it.value, refreshAll) 
  }
  
  if (!cmds) {
    state.pendingChanges = false
  }
  
  if (refreshAll || canReportBattery()) {
    cmds << batteryGetCmd()
  }
  
  //initializeCheckin()
  //cmds << wakeUpIntervalSetCmd(checkinIntervalSettingMinutes)
    
  if (cmds) {
    logDebug "Sending configuration to device."
    return delayBetween(cmds, 1000)
  }
  else {
    return cmds
  } 
}


private updateConfigVal(paramNum, paramSize, val, refreshAll) {
  def result = []
  def configVal = state["configVal${paramNum}"]
  
  if (refreshAll || (configVal != val)) {
    result << configSetCmd(paramNum, paramSize, val)
    result << configGetCmd(paramNum)
  } 
  return result
}


private initializeCheckin() {
  // Set the Health Check interval so that it can be skipped once plus 2 minutes.
  def checkInterval = ((checkinIntervalSettingMinutes * 2 * 60) + (2 * 60))
  
  sendEvent(name: "checkInterval", value: checkInterval, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}


// Forces the configuration to be resent to the device the next time it wakes up.
def refresh() { 
  logForceWakeupMessage "The sensor data will be refreshed the next time the device wakes up."
  state.pendingRefresh = true
  configure()
}


private logForceWakeupMessage(msg) {
  logDebug "${msg}  You can force the device to wake up immediately by removing and re-inserting the battery."
}


// Processes messages received from device.
def parse(String description) {
  def result = []
    
  logTrace "parse description: $description"
  
  sendEvent(name: "lastCheckin", value: convertToLocalTimeString(new Date()), displayed: false, isStateChange: true)  
  
  def cmd = zwave.parse(description, commandClassVersions)
  if (cmd) {
    result += zwaveEvent(cmd)
  }
  else {
    logDebug "Unable to parse description: $description"
  }
  return result
}


private getCommandClassVersions() {
  [
    0x30: 2,  // Sensor Binary
    0x25: 1,    // switch binary
    0x31: 5,  // Sensor Multilevel
    0x59: 1,  // AssociationGrpInfo
    0x5A: 1,  // DeviceResetLocally
    0x5E: 2,  // ZwaveplusInfo
    0x70: 1,  // Configuration
    0x71: 3,  // Notification v3
    0x72: 2,  // ManufacturerSpecific
    0x73: 1,  // Powerlevel
    0x80: 1,  // Battery
    0x84: 2,  // WakeUp
    0x85: 2,  // Association
    0x86: 1   // Version (2)
  ]
}


// Updates devices configuration, requests battery report, and/or creates last checkin event.
def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd)
{
  def result = []
  
  if (state.pendingChanges != false) {
    result += configure()
  }
  else if (state.pendingRefresh || canReportBattery()) {
    result << batteryGetCmd()
  }
  else {
    logTrace "Skipping battery check because it was already checked within the last ${batteryReportingIntervalSetting}."
  }
  
  //if (result) {
  //  result << "delay 2000"
  //}
    
  result << wakeUpNoMoreInfoCmd()
  
  return sendResponse(result)
}


private sendResponse(cmds) {
  logTrace "sendResponse $cmds"

  sendHubCommand(cmds.collect{ new hubitat.device.HubAction(it.format()) }, 1000)

  return []
}


// Creates the event for the battery level.
def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
  logTrace "BatteryReport: $cmd"
  def val = (cmd.batteryLevel == 0xFF ? 1 : cmd.batteryLevel)
  if (val > 100) {
    val = 100
  }
  state.lastBatteryReport = new Date().time 
  logDebug "Battery ${val}%"
  [
    createEvent(getEventMap("battery", val, null, null, "%"))
  ]
} 


// Stores the configuration values so that it only updates them when they've changed or a refresh was requested.
def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {  
  def name = configData.find { it.paramNum == cmd.parameterNumber }?.name
  if (name) { 
    def val = hexToInt(cmd.configurationValue, cmd.size)
  
    logDebug "${name} = ${val}"
  
    state."configVal${cmd.parameterNumber}" = val
  }
  else {
    logDebug "Parameter ${cmd.parameterNumber}: ${cmd.configurationValue}"
  }
  state.isConfigured = true
  state.pendingRefresh = false  
  return []
}


// Creates water events.
def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
  def result = [] 
  logTrace "NotificationReport: $cmd"
  
  if (cmd.notificationType == 0x14) {
    switch (cmd.event) {
      case 0:
        logDebug "Sensor No Light"        
        result << createEvent(getEventMap("light", "nolight"))
        break
      case 1:
        logDebug "Sensor Detects Light"
        result << createEvent(getEventMap("light", "light"))
        break
            case 2:
        logDebug "Sensor Detects Color Change"
        result << createEvent(getEventMap("light", "colorchange"))
        break
      default:
        logDebug "Sensor is ${cmd.event}"
    }
  }
  return result
}


def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
  logTrace "SensorMultilevelReport: ${cmd}"
    
  def result = []
  if (cmd.sensorType == 1) {
    result += handleTemperatureEvent(cmd)
  }
  else {
    logDebug "Unknown Sensor Type: ${cmd}"
  } 
  return result
}


private handleTemperatureEvent(cmd) {
  def result = []
  def cmdScale = cmd.scale == 1 ? "F" : "C"
  
  def val = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)  
    
  if ("$val".endsWith(".")) {
    val = safeToInt("${val}"[0..-2])
  }
      
  result << createEvent(createEventMap("temperature", val, null, "Temperature ${val}${getTemperatureScale()}", getTemperatureScale()))
  return result
}


private createEventMap(name, value, displayed=null, desc=null, unit=null) { 
  def eventMap = [
    name: name,
    value: value,
    displayed: (displayed == null ? ("${getAttrVal(name)}" != "${value}") : displayed),
    isStateChange: true
  ]
  if (unit) {
    eventMap.unit = unit
  }
  if (desc && eventMap.displayed) {
    logDebug desc
    eventMap.descriptionText = "${device.displayName} - ${desc}"
  }
  else {
    logTrace "Creating Event: ${eventMap}"
  }
  return eventMap
}


private getAttrVal(attrName) {
  try {
    return device?.currentValue("${attrName}")
  }
  catch (ex) {
    logTrace "$ex"
    return null
  }
}


// Ignoring event because sensor binary events are being handled by notification report.
def zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
  // logTrace "SensorBinaryReport: $cmd"
  return []
}


// Logs unexpected events from the device.
def zwaveEvent(hubitat.zwave.Command cmd) {
  logDebug "Unhandled Command: $cmd"
  return []
}


private getEventMap(name, value, displayed=null, desc=null, unit=null) {  
  def isStateChange = (device.currentValue(name) != value)
  displayed = (displayed == null ? isStateChange : displayed)
  def eventMap = [
    name: name,
    value: value,
    displayed: displayed,
    isStateChange: isStateChange
  ]
  if (desc) {
    eventMap.descriptionText = desc
  }
  if (unit) {
    eventMap.unit = unit
  } 
  logTrace "Creating Event: ${eventMap}"
  return eventMap
}


private wakeUpIntervalSetCmd(minutesVal) {
  state.checkinIntervalMinutes = minutesVal
  logTrace "wakeUpIntervalSetCmd(${minutesVal})"
  
  return zwave.wakeUpV2.wakeUpIntervalSet(seconds:(minutesVal * 60), nodeid:zwaveHubNodeId)
}


private wakeUpNoMoreInfoCmd() {
  return zwave.wakeUpV2.wakeUpNoMoreInformation()
}


private batteryGetCmd() {
  return zwave.batteryV1.batteryGet()
}


private configGetCmd(paramNum) {
  return zwave.configurationV1.configurationGet(parameterNumber: paramNum)
}


private configSetCmd(paramNum, size, val) {
  return zwave.configurationV1.configurationSet(parameterNumber: paramNum, size: size, scaledConfigurationValue: val)
}


private canReportBattery() {
  def reportEveryMS = (batteryReportingIntervalSettingMinutes * 60 * 1000)
    
  return (!state.lastBatteryReport || ((new Date().time) - state.lastBatteryReport > reportEveryMS)) 
}




// Settings
private getTempReportingSetting() {
  return settings?.tempReporting ?: findDefaultOptionName(tempReportingOptions)
}

private getLightSensitivitySetting() {
  return settings?.lightSensitivity ?: findDefaultOptionName(lightSensitivityOptions)
}

private getNotificationBuzzerSetting() {
  return settings?.notificationBuzzer ?: findDefaultOptionName(notificationBuzzerOptions)
}

private getLightDetectionDelaySetting() {
  return settings?.lightDetectionDelay ?: findDefaultOptionName(lightDetectionDelayOptions)
}

private getWaterDetectionBuzzerFrequencySetting() {
  return settings?.waterDetectionBuzzerFrequency ?: findDefaultOptionName(waterDetectionBuzzerFrequencyOptions)
}




// Configuration Parameters
private getConfigData() {
  return [
    [paramNum: 3, name: "Temperature Reporting Interval", value: convertOptionSettingToInt(tempReportingOptions, tempReportingSetting), size: 1],
    [paramNum: 1, name: "Light Sensitivity (with light cable)", value: convertOptionSettingToInt(lightSensitivityOptions, lightSensitivitySetting), size: 1],
    [paramNum: 4, name: "Notification Buzzer", value: convertOptionSettingToInt(notificationBuzzerOptions, notificationBuzzerSetting), size: 1],
    [paramNum: 5, name: "Light Detection Delay (detect blinking)", value: convertOptionSettingToInt(lightDetectionDelayOptions, lightDetectionDelaySetting), size: 1],
    [paramNum: 2, name: "Water Detectiong Buzzer Frequency (with water cable)", value: convertOptionSettingToInt(WaterDetectionBuzzerFrequencyOptions, WaterDetectionBuzzerFrequencySetting), size: 1]
  ] 
}


private getTempReportingOptions() {
  [
    [name: "30 Seconds", value: 30],
    [name: formatDefaultOptionName("1 Minute"), value: 60],
    [name: "3 Minutes", value: 180],
    [name: "4 Minutes", value: 240]
  ]
}

private getLightSensitivityOptions() {
  [
    [name: formatDefaultOptionName("High"), value: 0],
    [name: "Medium", value: 1],
    [name: "Low", value: 2]
  ]
}

private getNotificationBuzzerOptions() {
  [
    [name: "Disabled", value: 0],
    [name: formatDefaultOptionName("Enabled"), value: 1],
  ]
}

private getLightDetectionDelayOptions() {
  [   
    [name: formatDefaultOptionName("0 Seconds"), value: 0],
    [name: "1 Second", value: 1],
    [name: "2 Seconds", value: 2],
    [name: "3 Seconds", value: 3],
    [name: "4 Seconds", value: 4],
    [name: "5 Seconds", value: 5],
    [name: "6 Seconds", value: 6],
    [name: "7 Seconds", value: 7],
    [name: "8 Seconds", value: 8],
    [name: "9 Seconds", value: 9],
    [name: "10 Seconds", value: 10],
    [name: "11 Seconds", value: 11],
    [name: "12 Seconds", value: 12],
    [name: "13 Seconds", value: 13],
    [name: "14 Seconds", value: 14],
    [name: "15 Seconds", value: 15],
    [name: "16 Seconds", value: 16],
    [name: "17 Seconds", value: 17],
    [name: "18 Seconds", value: 18],
    [name: "19 Seconds", value: 19],
    [name: "20 Seconds", value: 20]
  ]
}

private getWaterDetectionBuzzerFrequencyOptions() {
  [   
    [name: formatDefaultOptionName("Every 10 Minutes"), value: 10],
    [name: "Every 5 Minutes", value: 5],
    [name: "Every 30 Minutes", value: 30]
  ]
}






private convertOptionSettingToInt(options, settingVal) {
  return safeToInt(options?.find { "${settingVal}" == it.name }?.value, 0)
}


private formatDefaultOptionName(val) {
  return "${val}${defaultOptionSuffix}"
}


private findDefaultOptionName(options) {
  def option = options?.find { it.name?.contains("${defaultOptionSuffix}") }
  return option?.name ?: ""
}


private getDefaultOptionSuffix() {
  return "   (Default)"
}


private safeToInt(val, defaultVal=-1) {
  return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}


private safeToDec(val, defaultVal=-1) {
  return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal
}


private hexToInt(hex, size) {
  if (size == 2) {
    return hex[1] + (hex[0] * 0x100)
  }
  else {
    return hex[0]
  }
}


private canCheckin() {
  // Only allow the event to be created once per minute.
  def lastCheckin = device.currentValue("lastCheckin")
  return (!lastCheckin || lastCheckin < (new Date().time - 60000))
}


private convertToLocalTimeString(dt) {
  def timeZoneId = location?.timeZone?.ID
  if (timeZoneId) {
    return dt.format("MM/dd/yyyy hh:mm:ss a", TimeZone.getTimeZone(timeZoneId))
  }
  else {
    return "$dt"
  } 
}


private isDuplicateCommand(lastExecuted, allowedMil) {
  !lastExecuted ? false : (lastExecuted + allowedMil > new Date().time) 
}


private logDebug(msg) {
  if (settings?.debugOutput || settings?.debugOutput == null) {
    log.debug "$msg"
  }
}


private logTrace(msg) {
//logDebug "$msg"
  log.trace "$msg"
}
