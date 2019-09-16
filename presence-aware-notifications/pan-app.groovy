/**
 *
 *  Copyright 2019 Eric Dalquist
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
 *  Changelog:
 *    - 2019.09.15: Initial Version
 */
import groovy.transform.Field

@Field String PRESENCE_SETTING_PREFIX = "presence_"

definition(
    name: "Presence Aware Notifications",
    namespace: "edalquist",
    author: "Eric Dalquist",
    description: "Wraps existing Notification devices and only sends if a linked Presence Sensor is present",
    category: "Notification",
    iconUrl: "",
    iconX2Url: ""
  )

preferences {
  deviceSelectPage();
}

def deviceSelectPage() {
  page(name: "deviceSelectPage", title: "Configure Foobar", install: true, uninstall: true) {
    section {
      input("notificationDevices", "capability.notification", title: "Select Notification Devices", multiple: true, required: true, submitOnChange: true)
    }
    section {
      if (notificationDevices) {
        // Render a Presence selector for each notification device
        notificationDevices.each{ notificationDevice ->
          input(getPresenceDeviceSettingName(notificationDevice.getId()),
            "capability.presenceSensor", title: "Presence Sensor for ${notificationDevice}:", description: "", required: true)
          // TODO support multi-select with ALL/ANY toggle
          // TODO support present vs away
        }
      }
    }
  }
}


def installed() {
  initialize()
}
def updated() {
  initialize()
}
def initialize() {
  log.info "Initialized with settings: ${settings}"
  logChildren();

  // All presence settings that exist now
  def presenceKeys = new HashSet(settings.keySet().findAll{ it.startsWith(PRESENCE_SETTING_PREFIX)});

  // Track child device IDs that should exist
  expectedChildIds = new HashSet();

  // Add or Update child devices for all selected notification devices
  notificationDevices.each{ notificationDevice ->
    childId = "pan_" + notificationDevice.getId();
    expectedChildIds.add(childId);

    child = getChildDevice(childId);
    if (child == null) {
      child = addChildDevice("edalquist", "Presence Aware Notifier", childId, null,
        [name: notificationDevice.getName() + " (Presence Aware)", isComponent: true]);
    }
    child.setNotificationId(notificationDevice.getId());
    presenceKeys.remove(getPresenceDeviceSettingName(notificationDevice.getId()));
  }

  // Cleanup any remaining child devices that are not longer configured
  allChildDevices.each { child ->
    if (!expectedChildIds.contains(child.getDeviceNetworkId())) {
      log.info "Deleting old childApp: ${child}"
      deleteChildDevice(child.getDeviceNetworkId());
    }
  }

  // Cleanup any old presence settings
  presenceKeys.each { presenceKey ->
    log.info "Deleting old presence key: ${presenceKeys}"
    app.removeSetting(presenceKey);
  }

  logChildren();
}
def logChildren() {
  log.info "There are ${allChildDevices.size()} child devices"
  allChildDevices.each {child ->
    log.info "Child device: ${child}"
  }
}

def speak(notificationDeviceId, message) {
  if (shouldNotify(notificationDeviceId)) {
    log.info "(present) speak(${notificationDeviceId}, ${message})"
    getNotificationDevice(notificationDeviceId).speak(message);
  } else {
    log.info "(not present) speak(${notificationDeviceId}, ${message})"
  }
}

def deviceNotification(notificationDeviceId, message) {
  if (shouldNotify(notificationDeviceId)) {
    log.info "(present) deviceNotification(${notificationDeviceId}, ${message})"
    getNotificationDevice(notificationDeviceId).deviceNotification(message);
  } else {
    log.info "(not present) deviceNotification(${notificationDeviceId}, ${message})"
  }
}

private shouldNotify(notificationDeviceId) {
  log.info "Child Device:    ${notificationDeviceId}"
  pd = settings[getPresenceDeviceSettingName(notificationDeviceId)]
  log.info "Presence Device: ${pd}"
  log.info "Presence: ${pd.currentPresence}"
  return pd.currentPresence == "present"
}

private getNotificationDevice(notificationDeviceId) {
  notificationDevices.each{ notificationDevice ->
    if (notificationDevice.getId() == notificationDeviceId) {
      return notificationDevice;
    }
  }
}

private getPresenceDeviceSettingName(notificationDeviceId) {
  return PRESENCE_SETTING_PREFIX + notificationDeviceId
}
