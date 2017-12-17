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
input "thermostat1", "capability.thermostat", title: "Which Master Thermostat?", multiple: false, required: true
input "thermostat2", "capability.thermostat", title: "Which Slave Thermostat?", multiple: false, required: true
input "heaters", "capability.switch", title: "Turn this switch on when heat is called for.", multiple: true, required: false
input "tempDiff", "number", title: "Temperature Difference Between Master and Slave?", required: true, defaultValue: 2
input "VacantCoolTemp", "number", title: "Cool SetPoint when vacant?", required: true, defaultValue: 85
input "VacantHeatTemp", "number", title: "Heat SetPoint when vacant?", required: true, defaultValue: 65
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
  subscribe(thermostat1, "thermostatSetpoint", setPointChangeHandler)
  //subscribe(thermostat1, "temperature", tempChangedHandler)
  subscribe(thermostat1, "thermostatOperatingState", OperatingStateChangedHandler)
  subscribe(onlyWhenPresent, "presence", PresenceChangeHandler)
  runEvery5Minutes(ThermostatPoll)
}

def ThermostatPoll(){
	def presence = onlyWhenPresent.latestValue("presence")
   	    if(presence == "present")
        {
        	log.debug "Polling the Master thermostat."
			thermostat1.poll()
        }
}

def PresenceChangeHandler(evt){
	def presence = onlyWhenPresent.latestValue("presence")
	def MTmode = thermostat1.latestValue("thermostatMode")
	def STmode = thermostat2.latestValue("thermostatMode")
   	    if(presence != "present")
        {
            log.info "Presence changed to: ${presence}.  Turning off heaters."
            heaters.each() {it.off()}
            if (STMode == "cool")
            {
          		log.info "Resetting temp to ${VacantCoolTemp}"
                thermostat2.setThermostatMode("cool")
            	thermostat2.setCoolingSetpoint(VacantCoolTemp)
            }
            else if (STMode == "heat")
            {
            	log.info "Resetting temp to ${VacantHeatTemp}"
                thermostat2.setThermostatMode("heat")
            	thermostat2.setHeatingSetpoint(VacantHeatTemp)
            }
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
    
    log.info "Current Master Thermostat Mode: ${MTmode}"
	log.info "Current Slave Thermostat Mode: ${STmode}"
	log.info "Current Temp Reading From Master Thermostat: ${MTTempReading}"
    log.info "Current Operating State of Master Thermostat: ${OperatingState}"
    
    if(presence == "present"){
        if(OperatingState == "heating"){
            log.info "Master thermostat is calling for heat.  Turning on heaters."
            heaters.each() {it.on()}
        }
        else if(OperatingState == "cooling")
        {
        	log.info "Master thermostat is calling for cooling."
            heaters.each() {it.off()}
        } 
        else
        {
            log.info "Not in heat or cool mode.  Turning off heaters."
            heaters.each() {it.off()}
        }
    }
    else
    {
    	log.info "Guests are not present.  Turning off heaters."
        heaters.each() {it.off()}
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
  
  	//log.debug "Current Slave Mode: ${STmode} | Master Mode: ${MTmode}"
	//log.debug "Current Master Temp Reading: ${MTTempReading}"
    
    //Turn on switch if the slave mode is cool and its coolder than the setpoint.
    //if(presence == "present"){
    //    if(STmode == "cool" && MTTempReading < (MThermostatTemp - ActiontempDiff)){
    //        log.debug "Turning on switch."
    //        heaters.each() {it.on()}
    //    } else 
    //        log.debug "Turning off switch."
    //        heaters.each() {it.off()}
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


    log.info "Thermostat SetPoint(M): ${MThermostatTemp}"
    log.info "Thermostat SetPoint(S): ${SThermostatTemp}"
    log.info "Current Temp Difference(S-M): ${difference}"
    log.info "Current Mode(M): ${mode}"

    if(presence == "present")
    {
          if(difference != tempDiff)
          {
              def NewTemp = (MThermostatTemp + tempDiff)
              if (mode == "cool")
              {
              	  heaters.each() {it.off()}
              	  log.info "${thermostat2} sync'ed with ${thermostat1} with an offset of ${tempDiff} degrees. Now at ${NewTemp}."
                  thermostat2.setCoolingSetpoint(NewTemp)
              }
              else
              {
                //thermostat2.setHeatingSetpoint(NewTemp)
              }
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