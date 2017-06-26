/**
 *  Airbnb Lock Code Updater
 *
 *  Copyright 2017 Joshua Spain
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
 */
definition(
    name: "AirBnB Lock Code Updater",
    namespace: "joshs85",
    author: "Joshua Spain",
    description: "Creates an HTTP API to update the codes for a lock",
    category: "",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    oauth: true)

import groovy.json.JsonSlurper

preferences {
  section("Choose a Door Lock") {
    input "doorlock", "capability.lockCodes"
  }
  section("LockCode position in lock") {
    input "CodePosition", "number", required: false, title: "Code Position"
  }
}

def installed() {
  if (!state.accessToken){OAuthToken()}
  log.debug "Installed with settings: ${settings}"
  log.debug "API URL : ${getApiServerUrl()}/api/smartapps/installations/${app.id}/lock/code"
  log.debug "access_token: ${state.accessToken}"
  initialize()
}

def updated() {
  if (!state.accessToken){OAuthToken()}
  log.debug "Updated with settings: ${settings}"
  log.debug "API URL : ${getApiServerUrl()}/api/smartapps/installations/${app.id}/lock/code"
  log.debug "access_token: ${state.accessToken}"
  unsubscribe()
  initialize()
}

def initialize() {
  subscribe(doorlock, "codeReport", codeReportEvent);
  subscribe(doorlock, "codeChanged", codeChangedEvent);
  listCode()
}

mappings {
  path("/lock/code") {
    action: [
      GET: "listCode",
      POST: "updateCode",
      DELETE: "deleteCode"
    ]
  }
}

def listCode() {
  log.debug "List Code | Requesting code ${CodePosition.toInteger()} from lock."
  doorlock.requestCode(CodePosition.toInteger())
  return true
}

def updateCode() {
  log.debug "update request received: params: ${params}"
  if (state.CurrentLockCode.toInteger() != params.code.toInteger()) {
      doorlock.setCode(CodePosition.toInteger(), params.code)
      sendPush(doorlock.label + " door code " + CodePosition.toInteger() + " is being set to " + params.code)
      return true
  } 
  else {
      log.debug "Requested code and current code are the same. No changes made."
      return false
  }
}

def deleteCode() {
  log.debug "starting deleteCode"
  doorlock.deleteCode(CodePosition.toInteger())
  return ["num":CodePosition.toInteger()]
}

def codeReportEvent(evt) {
  def lock = evt.device
  def user = evt.value as Integer
  def code = evt.data ? new JsonSlurper().parseText(evt.data)?.code : "" // Not all locks return a code due to a bug in the base Z-Wave lock device code
  def desc = evt.descriptionText // Description can have "is set" or "was added" or "changed" when code was added successfully
  if (user == CodePosition.toInteger()) {
  	  state.CurrentLockCode = evt.jsonData.code
      log.debug "Code Report | ${desc}. Code: ${code}"
  }
}

def codeChangedEvent(evt) {
  def lock = evt.device
  def user = evt.value as Integer
  def code = evt.data ? new JsonSlurper().parseText(evt.data)?.code : "" // Not all locks return a code due to a bug in the base Z-Wave lock device code
  def desc = evt.descriptionText // Description can have "is set" or "was added" or "changed" when code was added successfully
  if (user == CodePosition.toInteger()){
      log.debug "Code Changed | ${desc}. Requesting a listCode to update my state."
      listCode()
  }
}

def OAuthToken(){
	try {
        createAccessToken()
		log.debug "Creating new Access Token"
	} catch (e) { log.error "Access Token not defined. OAuth may not be enabled. Go to the SmartApp IDE settings to enable OAuth." }
}