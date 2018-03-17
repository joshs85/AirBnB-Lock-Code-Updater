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
input "EmergHeatThreshold", "number", title: "Turn on space heaters when difference in setpoint and actual temp greater than.", required: true, defaultValue: 15
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
  subscribe(thermostat1, "temperature", tempChangedHandler)
  subscribe(thermostat1, "thermostatOperatingState", OperatingStateChangedHandler)
  subscribe(thermostat2, "thermostatOperatingState", OperatingStateChangedHandler)
  subscribe(thermostat2, "thermostatMode", OperatingStateChangedHandler)
  subscribe(onlyWhenPresent, "presence", PresenceChangeHandler)
  runEvery5Minutes(ThermostatPoll)
  StateSync()
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
	log.debug "PresenceChange Change Handler Begin"
	StateSync()
}

def OperatingStateChangedHandler(evt){
	log.debug "OperatingState Change Handler Begin"
	StateSync()
}

def tempChangedHandler(evt){
	log.debug "Temperature Changed Handler Begin"
    StateSync()
}

def setPointChangeHandler(evt) {
	log.debug "SetPoint Change Handler Begin"
	StateSync()
}

private StateSync()
{
	log.debug "StateSync Begin"
	def MThermostatTemp = thermostat1.latestValue("thermostatSetpoint")
	def SThermostatTemp = thermostat2.latestValue("thermostatSetpoint")
	def MTmode = thermostat1.latestValue("thermostatMode")
	def STmode = thermostat2.latestValue("thermostatMode")
	def MTTempReading = thermostat1.latestValue("temperature")
    def STTempReading = thermostat2.latestValue("temperature")
    def MTOperatingState = thermostat1.latestValue("thermostatOperatingState")
    def STOperatingState = thermostat2.latestValue("thermostatOperatingState")
    def presence = onlyWhenPresent.latestValue("presence")
    if (SThermostatTemp != null){
    	def difference = (SThermostatTemp - MThermostatTemp)
        log.info "Current Set Point Difference(S-M): ${difference}"
    }
    log.info "(M) Thermostat Mode: ${MTmode}"
	log.info "(S) Thermostat Mode: ${STmode}"
    log.info "(M) Operating State: ${MTOperatingState}"
    log.info "(S) Operating State: ${STOperatingState}"
	log.info "(M) Temp: ${MTTempReading}"
    log.info "(S) Temp: ${STTempReading}"
    log.info "(M) Set Point ${MThermostatTemp}"
    log.info "(S) Set Point ${SThermostatTemp}"

    
    if(presence == "present"){
        if(MTOperatingState == "heating"){
            def TempDiff = (MThermostatTemp - MTTempReading)
            log.info "MThermostatTemp - MTTempReading = ${TempDiff}"
        	if (STOperatingState == "idle" && SThermostatTemp == null)
            {
				log.info "Master thermostat is calling for heat.  Slave is in eco.  Turning on space heaters."
            	heaters.each() {it.on()}
            }
            else if (TempDiff > EmergHeatThreshold)
            {
            	log.info "Master thermostat is calling for heat.  Temp Diff > ${EmergHeatThreshold}. Turning on space heaters."
            	heaters.each() {it.on()}
            }
            else
            {
            	log.info "Master thermostat is calling for heat.  Turning off space heaters and syncing temp."
                heaters.each() {it.off()}
                if(difference != tempDiff)
                {
                    def NewTemp = (MThermostatTemp + tempDiff)
                    log.info "Syncing Heating SetPoint to ${NewTemp}"
                    if (state.LastAction == null)
                    {
                        state.LastSHeatSetpoint = SThermostatTemp
                        state.LastAction = "heat"
                    }
                    thermostat2.setHeatingSetpoint(NewTemp)
                }
            }
        }
        else if(MTOperatingState == "cooling")
        {
        	log.info "Master thermostat is calling for cooling. Turning Space Heaters Off and syncing temp."
            heaters.each() {it.off()}
            if(difference != tempDiff)
          	{
            	def NewTemp = (MThermostatTemp + tempDiff)
				log.info "Syncing Cooling SetPoint to ${NewTemp}"
                if (state.LastAction == null)
                {
                	state.LastAction = "cool"
                    state.LastSCoolSetpoint = SThermostatTemp
                }
                thermostat2.setCoolingSetpoint(NewTemp)
            }
        }
        else
        {
            log.info "Not in heat or cool mode.  Turning off heaters."
            heaters.each() {it.off()}
			RestoreST()
        }
    }
    else
    {
        log.info "Presence changed to: ${presence}.  Turning off heaters."
        heaters.each() {it.off()}
        if (STMode == "cool")
        {
            log.info "(S) Resetting cool temp to ${VacantCoolTemp}"
            thermostat1.setThermostatMode("cool")
            thermostat1.setCoolingSetpoint(VacantCoolTemp)
        }
        else if (STMode == "heat")
        {
            log.info "(S) Resetting heat temp to ${VacantHeatTemp}"
            thermostat1.setThermostatMode("heat")
            thermostat1.setHeatingSetpoint(VacantHeatTemp)
        }
        RestoreST()
    }
}

private RestoreST()
{
    if (state.LastAction == "heat")
    {
    	log.info "Restoring slave ${state.LastAction} setpoint to ${state.LastSHeatSetpoint}"
        thermostat2.setHeatingSetpoint(state.LastSHeatSetpoint)
        state.LastAction = null
        state.LastSHeatSetpoint = null
    }
    else if (state.LastAction == "cool")
    {
    	log.info "Restoring slave ${state.LastAction} setpoint to ${state.LastSHeatSetpoint}"
        thermostat2.setCoolingSetpoint(state.LastSCoolSetpoint)
        state.LastAction = null
        state.LastSCoolSetpoint = null
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