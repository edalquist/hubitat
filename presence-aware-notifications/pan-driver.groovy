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
 *
 */
definition (name: "Presence Aware Notifier", namespace: "edalquist", author: "Eric Dalquist") {
  capability "Notification"
  capability "Actuator"
  capability "Speech Synthesis"
}

preferences {
}

def installed() {
  initialize()
}

def updated() {
  initialize()
}

def initialize() {
}

def speak(message) {
  log.info "speak: ${state.notificationDeviceId} - ${message}";
  parent.speak(state.notificationDeviceId, message);
}

def deviceNotification(message) {
  log.info "deviceNotification: ${state.notificationDeviceId} - ${message}";
  parent.deviceNotification(state.notificationDeviceId, message);
}

def setNotificationId(notificationDeviceId) {
  log.info "setNotificationId: ${notificationDeviceId}";
  state.notificationDeviceId = notificationDeviceId;
}
