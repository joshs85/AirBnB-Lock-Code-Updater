definition(
name: "Thermostat Sync When Present",
namespace: "joshs85",
author: "Joshua S.",
description: "Adjust a thermostat based on the setting of another thermostat",
category: "Green Living",
iconUrl: "http://icons.iconarchive.com/icons/icons8/windows-8/512/Science-Temperature-icon.png",
iconX2Url: "http://icons.iconarchive.com/icons/icons8/windows-8/512/Science-Temperature-icon.png"
)

section
{
input "thermostat1", "capability.thermostat", title: "Which Master Thermostat?", multi: false, required: true
input "thermostat2", "capability.thermostat", title: "Which Slave Thermostat?", multi: false, required: true
input "heater", "capability.switch", title: "Turn this switch on when heat is called for.", multi: false, required: false
input "tempDiff", "number", title: "Temperature Difference Between Master and Slave?", required: true, defaultValue: 2
input "ActiontempDiff", "number", title: "Action Threshold Difference Deg?", required: true, defaultValue: 2
input "sendPushMessage", "bool", title: "Send a push notification?", required: false, defaultValue: true
input "sendSMS", "phone", title: "Send as SMS?", required: false, defaultValue: null
input "onlyWhenPresent", "capability.presenceSensor", title: "Only run when this person is present.", required: false
}

def installed(){
  log.debug "Installed called with ${settings}"
  init()
}

def updated(){
  log.debug "Updated called with ${settings}"
  unsubscribe()
  init()
}

def init(){
//nIn(60, "temperatureHandler")
  subscribe(thermostat1, "thermostatSetpoint", setPointChangeHandler)
  subscribe(thermostat1, "temperature", tempChangedHandler)
  subscribe(thermostat1, "thermostatOperatingState", OperatingStateChangedHandler)
  subscribe(onlyWhenPresent, "presence", PresenceChangeHandler)
  runEvery5Minutes(ThermostatPoll)
}

def ThermostatPoll(){
	def presence = onlyWhenPresent.latestValue("presence")
   	    if(presence == "present")
        {
			thermostat1.poll()
        }
}

def PresenceChangeHandler(evt){
	def presence = onlyWhenPresent.latestValue("presence")
   	    if(presence != "present")
        {
            log.debug "Presence changed to: ${presence}.  Turning off switch."
            heater.off()
        }
}

def OperatingStateChangedHandler(evt){
	log.debug "OperatingState Changed Handler Begin"
	def MThermostatTemp = thermostat1.latestValue("thermostatSetpoint")
	def SThermostatTemp = thermostat2.latestValue("thermostatSetpoint")
	def MTmode = thermostat1.latestValue("thermostatMode")
	def STmode = thermostat2.latestValue("thermostatMode")
	def MTTempReading = thermostat1.latestValue("temperature")
    def OperatingState = thermostat1.latestValue("thermostatOperatingState")
    def presence = onlyWhenPresent.latestValue("presence")
    
    log.debug "Current Master Thermostat Mode: ${MTmode}"
	log.debug "Current Slave Thermostat Mode: ${STmode}"
	log.debug "Current Temp Reading From Master Thermostat: ${MTTempReading}"
    log.debug "Current Operating State of Master Thermostat: ${OperatingState}"
    
    if(presence == "present"){
        if(OperatingState == "heating"){
            log.debug "Turning on switch."
            heater.on()
        }
        else if(OperatingState == "cooling")
        {
        	log.debug "Master thermostat is calling for cooling."
            heater.off()
        } 
        else
        {
            log.debug "Turning off switch."
            heater.off()
        }
    }
}

def tempChangedHandler(evt){
	log.debug "Temperature Changed Handler Begin"
	def MThermostatTemp = thermostat1.latestValue("thermostatSetpoint")
	def SThermostatTemp = thermostat2.latestValue("thermostatSetpoint")
	def MTmode = thermostat1.latestValue("thermostatMode")
	def STmode = thermostat2.latestValue("thermostatMode")
	def MTTempReading = thermostat1.latestValue("temperature")
    def presence = onlyWhenPresent.latestValue("presence")
  
  	log.debug "Current Slave Mode: ${STmode} | Master Mode: ${MTmode}"
	log.debug "Current Master Temp Reading: ${MTTempReading}"
    
    //Turn on switch if the slave mode is cool and its coolder than the setpoint.
    //if(presence == "present"){
    //    if(STmode == "cool" && MTTempReading < (MThermostatTemp - ActiontempDiff)){
    //        log.debug "Turning on switch."
    //        heater.on()
    //    } else {
    //        log.debug "Turning off switch."
    //        heater.off()
    //    }
    //}
}

def setPointChangeHandler(evt) {
	log.debug "SetPoint Change Handler Begin"
	//get the latest temp readings and compare
    def MThermostatTemp = thermostat1.latestValue("thermostatSetpoint")
    def SThermostatTemp = thermostat2.latestValue("thermostatSetpoint")
    def mode = thermostat1.latestValue("thermostatMode")
    def difference = (SThermostatTemp - MThermostatTemp)
    def presence = onlyWhenPresent.latestValue("presence")


    log.debug "Thermostat(M): ${MThermostatTemp}"
    log.debug "Thermostat(S): ${SThermostatTemp}"
    log.debug "Temp Diff: ${tempDiff}"
    log.debug "Current Temp Difference: ${difference}"
    log.debug "Current Mode: ${mode}"

    if(presence == "present")
    {
          if(difference != tempDiff)
          {
              def NewTemp = (MThermostatTemp + tempDiff)
              def msg = "${thermostat2} sync'ed with ${thermostat1} with an offset of ${tempDiff} degrees. Now at ${NewTemp}."
              if (mode == "cool")
              {
                  thermostat2.setCoolingSetpoint(NewTemp)
              }
              else
              {
                thermostat2.setHeatingSetpoint(NewTemp)
              }
              thermostat2.poll()
              log.debug msg
              sendMessage(msg)
          }
    }
}

private sendMessage(msg){
  if (sendPushMessage == true) {
    sendPush(msg)
  }
  if (sendSMS != null) {
    sendSms(sendSMS, msg) 
  }

}