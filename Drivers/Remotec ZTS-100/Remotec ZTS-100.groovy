/**
 *  Remotec ZTS-100 driver for Hubitat
 *
 *  Copyright 2020 Drew Covi
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
 *  Version 1.0 - 11/01/2020     Initial Version
 */
metadata {
	definition (name: "Remotec ZTS-100", namespace: "drewcovi", author: "Drew Covi") {
		capability "Refresh"
        capability "Battery"
		capability "Actuator"
		capability "Temperature Measurement"
        capability "Thermostat"
		capability "Thermostat Fan Mode"
		capability "Configuration"
		capability "Sensor"
        capability "Switch"
       
		command "SensorCal", [[name:"calibration",type:"ENUM", description:"Number of degrees to add/subtract from thermostat sensor", constraints:["-10", "-9", "-8", "-7", "-6", "-5", "-4", "-3", "-2", "-1", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"]]]
		command "resetFilter"
		
		attribute "thermostatFanState", "string"
		attribute "currentSensorCal", "number"
	}
		preferences {
        		input "swingDegrees", "enum", title: "Temperature Swing", description: "Number of degrees above/below setpoint before unit turns on", multiple: false, defaultValue: "2", options: ["1","2","3","4"], required: false, displayDuringSetup: true
        		input "tempDiff", "enum", title: "Temperature Differential", description: "Number of degrees between turning ON/OFF Stage 1 and Stage 2. Only applies to multi-stage heating and cooling.", multiple: false, defaultValue: "2", options: ["1","2","3","4"], required: false, displayDuringSetup: true
			input "maxHeatSP", "number", title: "Maximum Heat Setpoint", description: "Maximum temperature the heating setpoint can be set to. (Valid Values: 41F to 99F)", multiple: false, defaultValue: "99", range: "41..99", required: false, displayDuringSetup: true
			input "minCoolSP", "number", title: "Minimum Cool Setpoint", description: "Minimum temperature the cooling setpoint can be set to. (Valid Values: 41F to 99F)", multiple: false, defaultValue: "41", range: "41..99", required: false, displayDuringSetup: true
			input "filterMonths", "enum", title: "Filter months", description: "Number of months between filter replacement notifications", multiple: false, defaultValue: "6", options: ["1","2","3","4","5","6"], required: false, displayDuringSetup: true
			input "autoReportTemp", "enum", title: "Temperature Auto Report Degrees", description: "Degrees Different Before Auto Reporting Temperature", multiple: false, defaultValue: "4", options: ["0","1","2","3","4","5","6","7","8"], required: false, displayDuringSetup: true
			input "autoReportTime", "enum", title: "Temperature Auto Report Time Interval", description: "Time Interval Before Auto Reporting Temperature", multiple: false, defaultValue: "2", options: ["0","1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16"], required: false, displayDuringSetup: true
			input "logEnable", "bool", title: "Enable debug logging", defaultValue: false
		}
            
}

// parse events into attributes
def parse(String description) {
	if (logEnable) log.debug "Parse...START"
	if (logEnable) log.debug "Parsing '${description}'"
	def map = createEvent(zwaveEvent(zwave.parse(description, [0x20:1, 0x31:5, 0x40:3, 0x42:2, 0x43:2, 0x44:4, 0x45:2, 0x59:2, 0x5A:1, 0x5E:2, 0x70:1, 0x72:2, 0x73:1, 0x7A:3, 0x85:2, 0x86:2, 0x98:1])))
    	if (logEnable) log.debug "In parse, map is $map"
	return null
}

//
// Handle commands from Thermostat
//
def setHeatingSetpoint(double degrees) {
	if (logEnable) log.debug "setHeatingSetpoint...START"
    def locationScale = getTemperatureScale()
    if (logEnable) log.debug "stateScale is $state.scale"
    if (logEnable) log.debug "locationScale is $locationScale"
	def p = (state.precision == null) ? 1 : state.precision
	if (logEnable) log.debug "precision is $p"
    if (logEnable) log.debug "setpoint requested is $degrees"
    if (logEnable) log.debug "setHeatingSetpoint...END"
    //zwave.thermostatSetpointV2.thermostatSetpointSet(setpointType: 1, scale: getTemperatureScale()=="F" ? 1:0 , precision: 0, scaledValue: value)
    return delayBetween([
        zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: 1, scale: 1, precision: p, scaledValue: degrees).format(),
        zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1).format()
    ],1000);
}

def setCoolingSetpoint(double degrees) {
	if (logEnable) log.debug "setCoolingSetpoint...START"
    def locationScale = getTemperatureScale()
    if (logEnable) log.debug "stateScale is $state.scale"
    if (logEnable) log.debug "locationScale is $locationScale"
	def p = (state.precision == null) ? 1 : state.precision
	if (logEnable) log.debug "precision is $p"
    if (logEnable) log.debug "setpoint requested is $degrees"
    if (logEnable) log.debug "setCoolingSetpoint...END"
	return delayBetween([
        zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: 2, scale: 1, precision: p, scaledValue: degrees).format(),
		zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 2).format()
    ], 1000);
}

def off() {
	if (logEnable) log.debug "Switching to off mode..."
    return delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 0).format(),
        zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format(),
	    zwave.thermostatModeV1.thermostatModeGet().format()
    ], 1000)
}

def on() {
	if (logEnable) log.debug "Executing 'on'"
	log.warn "ON is too ambiguous for a multi-state thermostat, so it does nothing in this driver. Use setThermostatMode or the AUTO/COOL/HEAT commands."
}

def heat() {
	if (logEnable) log.debug "Switching to heat mode..."
	return delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 1).format(),
		zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format(),
		zwave.thermostatModeV1.thermostatModeGet().format()
	], 1000)   
}

def emergencyHeat() {
	if (logEnable) log.debug "Switching to emergency heat mode..."
    	return delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 4).format(),
		zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format(),
		zwave.thermostatModeV1.thermostatModeGet().format()
	], 1000)
}

def cool() {
	if (logEnable) log.debug "Switching to cool mode..."
    	return delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 2).format(),
		zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format(),
		zwave.thermostatModeV1.thermostatModeGet().format()
	], 1000)
}

def setThermostatMode(value) {
	if (logEnable) log.debug "Executing 'setThermostatMode'"
	if (logEnable) log.debug "value: " + value
	def cmds = []
	switch (value) {
		case "auto":
		    if (logEnable) log.debug "Switching to auto mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 3).format()
			break
		case "off":
			if (logEnable) log.debug "Switching to off mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 0).format()
			break
		case "heat":
			if (logEnable) log.debug "Switching to heat mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 1).format()
			break
		case "cool":
			if (logEnable) log.debug "Switching to cool mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 2).format()
			break
		case "emergency heat":
			if (logEnable) log.debug "Switching to emergency heat mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 4).format()
			break
	}
	cmds << zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format()
	cmds << zwave.thermostatModeV1.thermostatModeGet().format()
	return delayBetween(cmds, 1000)
}

def fanOn() {
	if (logEnable) log.debug "Switching fan to on mode..."
    return delayBetween([
		zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: 1).format(),
		zwave.thermostatFanStateV1.thermostatFanStateGet().format()
	], 1000)   
}

def fanAuto() {
	if (logEnable) log.debug "Switching fan to auto mode..."
    return delayBetween([
		zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: 0).format(),
		zwave.thermostatFanStateV1.thermostatFanStateGet().format()
	], 1000)
}

def fanCirculate() {
	if (logEnable) log.debug "Fan circulate mode not supported for this thermostat type..."
	log.warn "Fan circulate mode not supported for this thermostat type..."
    /*
	sendEvent(name: "currentfanMode", value: "Fan Cycle Mode" as String)
	delayBetween([
		zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: 6).format(),
		zwave.thermostatFanModeV3.thermostatFanModeGet().format(),
                zwave.thermostatFanStateV1.thermostatFanStateGet().format()
	], 3000)
	*/
}

def setThermostatFanMode(value) {
	if (logEnable) log.debug "Executing 'setThermostatFanMode'"
	if (logEnable) log.debug "value: " + value
	def cmds = []
	switch (value) {
		case "on":
			if (logEnable) log.debug "Switching fan to ON mode..."
			cmds << zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: 1).format()
			break
		case "auto":
			if (logEnable) log.debug "Switching fan to AUTO mode..."
			cmds << zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: 0).format()
			break
		default:
			log.warn "Fan Mode $value unsupported."
			break
	}
	cmds << zwave.thermostatFanStateV1.thermostatFanStateGet().format()
	return delayBetween(cmds, 1000)
}

def auto() {
	if (logEnable) log.debug "Switching to auto mode..."
    return delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 3).format(),
		zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format(),
        zwave.thermostatModeV1.thermostatModeGet().format()
	], 1000)
}

def setSchedule() {
	if (logEnable) log.debug "Executing 'setSchedule'"
	log.warn "setSchedule does not do anything with this driver."
}

def refresh() {
	if (logEnable) log.debug "Executing 'refresh'"
	if (logEnable) log.debug "....done executing refresh"
	return delayBetween([
		zwave.thermostatModeV2.thermostatModeGet().format(),
		zwave.thermostatOperatingStateV2.thermostatOperatingStateGet().format(),
		zwave.thermostatFanModeV3.thermostatFanModeGet().format(),
		zwave.thermostatFanStateV1.thermostatFanStateGet().format(),
		zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1).format(),
		zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 2).format()
	], 1000)
    
}

def configure() {
	if (logEnable) log.debug "Executing 'configure'"
	if (logEnable) log.debug "zwaveHubNodeId: " + zwaveHubNodeId
	if (logEnable) log.debug "....done executing 'configure'"
	
	return delayBetween([
		zwave.thermostatModeV2.thermostatModeSupportedGet().format(),
		zwave.thermostatFanModeV3.thermostatFanModeSupportedGet().format(),
        zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId]).format(),
		zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:[zwaveHubNodeId]).format(),
        zwave.associationV1.associationSet(groupingIdentifier:3, nodeId:[zwaveHubNodeId]).format()
	], 1000)
}

def SensorCal(value) {
	value = value.toInteger()
	if (logEnable) log.debug "SensorCal value: " + value
	def SetCalValue
	if (value < 0) {
		SetCalValue = value == -1 ? 255 : value == -2 ? 254 : value == -3 ? 253 : value == -4 ? 252 : value == -5 ? 251 : value == -6 ? 250 : value == -7 ? 249 : value == -8 ? 248 : value == -9 ? 247 : value == -10 ? 246 : "unknown"
		if (logEnable) log.debug "SetCalValue: " + SetCalValue
	} else {
		SetCalValue = value.toInteger()
		if (logEnable) log.debug "SetCalValue: " + SetCalValue
	}
	return delayBetween([
		zwave.configurationV1.configurationSet(configurationValue: [0, SetCalValue] , parameterNumber: 13, size: 2).format(),
		zwave.configurationV1.configurationGet(parameterNumber: 13).format()
		], 1000)
}
def monthsToHours(months)
{
    months = months.toInteger()
    return Math.max(500,Math.min(Math.round((months*730)/100)*100, 4000))
}

def updated() {
	if (logEnable) log.debug "Executing 'updated'"

    if (logEnable) {
		log.debug "debug logging is enabled."
		runIn(1800,logsOff)
	}

	def paramSwing, paramDiff, paramDeadband, paramMaxHeatSP, paramMinCoolSP, paramFilter, paramLED, paramSleepTimer, paramReportTemp, paramReportTime
	
	if (settings.swingDegrees) {
	    paramSwing = settings.swingDegrees.toInteger()
    } else {
        paramSwing = 2
    }
	if (logEnable) log.debug "paramSwing: " + paramSwing
	
	if (settings.tempDiff) {
	    paramDiff = settings.tempDiff.toInteger()
    } else {
        paramDiff = 2
    }
	if (logEnable) log.debug "paramDiff: " + paramDiff

	if (settings.deadbandDegrees) {
	    paramDeadband = settings.deadbandDegrees.toInteger()
    } else {
        paramDeadband = 4
    }
	if (logEnable) log.debug "paramDeadband: " + paramDeadband
	
	if (settings.maxHeatSP) {
	    paramMaxHeatSP = settings.maxHeatSP.toInteger()
		if (paramMaxHeatSP>(99-paramDeadband)) {paramMaxHeatSP=99-paramDeadband}
		paramMaxHeatSP = (paramMaxHeatSP + "0")
		paramMaxHeatSP = paramMaxHeatSP.toInteger()
    } else {
        paramMaxHeatSP = 930
    }
	if (logEnable) log.debug "paramMaxHeatSP: " + paramMaxHeatSP

	if (settings.minCoolSP) {
	    paramMinCoolSP = settings.minCoolSP.toInteger()
		if (paramMinCoolSP<(41+paramDeadband)) {paramMinCoolSP=41+paramDeadband}
		paramMinCoolSP = (paramMinCoolSP + "0")
		paramMinCoolSP = paramMinCoolSP.toInteger()
    } else {
        paramMinCoolSP = 470
    }
	if (logEnable) log.debug "paramMinCoolSP: " + paramMinCoolSP
	
	if (settings.filterMonths) {
	    paramFilter = monthsToHours(settings.filterMonths)
    } else {
        paramFilter = 500
    }
	if (logEnable) log.debug "paramFilter: " + paramFilter

	if (settings.autoReportTemp) {
		paramReportTemp = settings.autoReportTemp.toInteger()
    } else {
        paramReportTemp = 1
    }
	if (logEnable) log.debug "paramReportTemp: " + paramReportTemp

	if (settings.autoReportTime) {
		paramReportTime = settings.autoReportTime.toInteger()
    } else {
        paramReportTime = 0
    }
	if (logEnable) log.debug "paramReportTime: " + paramReportTime
	
	
	if (logEnable) log.debug "....done executing 'updated'"
	
	return delayBetween([
		zwave.configurationV1.configurationSet(parameterNumber: 1, size: 2, configurationValue: [0,paramSwing]).format(),
		zwave.configurationV1.configurationGet(parameterNumber: 1).format(),
		zwave.configurationV1.configurationSet(parameterNumber: 2, size: 2, configurationValue: [0,paramDiff]).format(),
		zwave.configurationV1.configurationGet(parameterNumber: 2).format(),
		zwave.configurationV1.configurationSet(parameterNumber: 3, size: 4, configurationValue: [0,paramFilter]).format(),
		zwave.configurationV1.configurationGet(parameterNumber: 3).format(),
        //zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, configurationValue: [0,paramScale]).format(),
		//zwave.configurationV1.configurationGet(parameterNumber: 5).format(),
		zwave.configurationV1.configurationSet(parameterNumber: 6, size: 2, scaledConfigurationValue: paramMaxHeatSP).format(),
		zwave.configurationV1.configurationGet(parameterNumber: 6).format(),
		zwave.configurationV1.configurationSet(parameterNumber: 7, size: 2, scaledConfigurationValue: paramMinCoolSP).format(),
		zwave.configurationV1.configurationGet(parameterNumber: 7).format(),
		//zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, configurationValue: [0,paramEasyMode]).format(),
		//zwave.configurationV1.configurationGet(parameterNumber: 8).format(),
		//zwave.configurationV1.configurationSet(parameterNumber: 9, size: 1, configurationValue: [0,paramTimeFormat]).format(),
		//zwave.configurationV1.configurationGet(parameterNumber: 9).format(),
		zwave.configurationV1.configurationSet(parameterNumber: 11, size: 2, configurationValue: [0,paramReportTemp]).format(),
		zwave.configurationV1.configurationGet(parameterNumber: 11).format(),
		zwave.configurationV1.configurationSet(parameterNumber: 12, size: 2, configurationValue: [0,paramReportTime]).format(),
		zwave.configurationV1.configurationGet(parameterNumber: 12).format()
		], 500)
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

// Reset filter counter on thermostat
def resetFilter() {
    if (logEnable) log.debug "Executing 'resetFilter'"
    def months
    def paramValue
    
    months = settings.filterMonths.toInteger()

    if (settings.filterMonths) {
	paramValue = monthsToHours(months)
    } else {
        paramValue = 500
    }
	if (logEnable) {log.debug "Resetting filter counter to $months months ($paramValue hours)"}

	if (logEnable) log.debug "....done executing 'resetFilter'"
	
	return delayBetween([
		zwave.configurationV1.configurationSet(parameterNumber: 3, size: 2, configurationValue: [0,paramValue]).format(),
		zwave.configurationV1.configurationGet(parameterNumber: 3).format()
	], 1000)
}

//
// Handle updates from thermostat
//
private zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	if (logEnable) log.debug "---CONFIGURATION REPORT V1--- ${device.displayName} sent parameterNumber:${cmd.parameterNumber}, size:${cmd.size}, value:${cmd.scaledConfigurationValue}"
    def config = cmd.scaledConfigurationValue
	def CalValue
    if (cmd.parameterNumber == 13) {
		if (logEnable) log.debug "cmd.scaledConfigurationValue: " + config
		if (cmd.parameterNumber == 10) {
			if (config.toInteger() > 10) {
				CalValue = config == 255 ? "-1" : config == 254 ? "-2" : config == 253 ? "-3" : config == 252 ? "-4" : config == 251 ? "-5" : config == 250 ? "-6" : config == 249 ? "-7" : config == 248 ? "-8" : config == 247 ? "-9" : config == 246 ? "-10" : "unknown"
				if (logEnable) log.debug "CalValue: " + CalValue
				sendEvent([name:"currentSensorCal", value: CalValue, displayed:true, unit: getTemperatureScale(), isStateChange:true])
			} else {
				CalValue = config
				if (logEnable) log.debug "CalValue: " + CalValue
				sendEvent([name:"currentSensorCal", value: CalValue, displayed:true, unit: getTemperatureScale(), isStateChange:true])
			}
    	}
	}
	if (logEnable) log.debug "Parameter: ${cmd.parameterNumber} value is: ${config}"
}

private zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    // setup basic reports for missed operating state changes
    debug
    if (cmd.value.toInteger()==0xFF) {
        if (device.currentValue("thermostatOperatingState")!="heating" || device.currentValue!="cooling") sendToDevice(zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format())
    } else {
        if (device.currentValue("thermostatOperatingState")=="heating" || device.currentValue=="cooling") sendToDevice(zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format())
    }
}
private zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
	if (logEnable) log.debug "SensorMultilevelReport...START"
	if (logEnable) log.debug "cmd: $cmd"
	def map = [:]
	if (logEnable) log.debug "cmd.sensorType: " + cmd.sensorType
	if (cmd.sensorType.toInteger() == 1) {
        map.value = cmd.scaledSensorValue
		map.unit = getTemperatureScale()
		map.name = "temperature"
	}
	if (logEnable) log.debug "In SensorMultilevelReport map is $map"
    sendEvent(map)
	if (logEnable) log.debug "SensorMultilevelReport...END"
	
}

private zwaveEvent(hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd)
{
	if (logEnable) log.debug "ThermostatSetPointReport...START"
	def cmdScale = cmd.scale == 1 ? "F" : "C"
    if (logEnable) log.debug "cmdScale is $cmd.scale before (this is the state variable), and $cmdScale after"
    if (logEnable) log.debug "setpoint requested is $cmd.scaledValue and unit is $cmdScale"
	def map = [:]
	map.value = cmd.scaledValue
	map.unit = getTemperatureScale()
	map.displayed = true
    if (logEnable) log.debug "value is $map.value and unit is $map.unit"
	switch (cmd.setpointType) {
		case 1:
			map.name = "heatingSetpoint"
			break;
		case 2:
			map.name = "coolingSetpoint"
			break;
		default:
			return [:]
	}
	state.size = cmd.size
	state.scale = cmd.scale
	state.precision = cmd.precision
    if (logEnable) log.debug "In ThermostatSetpointReport map is $map"
    
        updateDataValue(map.name.toString(), map.value.toString())
	sendEvent([name: map.name, value: map.value, displayed:true, unit: map.unit, isStateChange:true])

	// Update thermostatSetpoint based on mode and operatingstate
	def tos = getDataValue("thermostatOperatingState")
	def tm = getDataValue("thermostatMode")
	def lrm = getDataValue("lastRunningMode")
	def csp = getDataValue("coolingSetpoint")
	def hsp = getDataValue("heatingSetpoint")
    
    if (logEnable) log.debug "tos: "+tos+", tm: "+tm+", lrm:"+lrm+", csp:"+csp+", hsp:"+hsp

	if (lrm == null) {
		if (tm == "cool") {
			updateDataValue("lastRunningMode", "cool")
			lrm = "cool"
		} else {
			if (tm == "heat") {
				updateDataValue("lastRunningMode", "heat")
				lrm = "heat"
			} else {
				if (tm == "auto") {
					updateDataValue("lastRunningMode", "heat")
					lrm = "heat"
				}
			}	
		}
	}
	
	def map2 = [:]
	map2.value = cmd.scaledValue
	map2.unit = getTemperatureScale()
	map2.displayed = true
	map2.name = "thermostatSetpoint"
	
	if ((tos == "idle") && tm == "auto") {
		if (lrm == "cool") {
			if (map.name == "coolingSetpoint") {
				if (logEnable) log.debug "thermostatSetpoint being set to " + map.value
				updateDataValue("thermostatSetpoint", map.value.toString())
				map2.value = map.value
				
			} else { 
				if (logEnable) log.debug "thermostatSetpoint being set to " + csp
				updateDataValue("thermostatSetpoint", csp)
				map2.value = csp
			}	
		}
		if (getDataValue("lastRunningMode") == "heat") {
			if (map.name == "heatingSetpoint") {
				if (logEnable) log.debug "thermostatSetpoint being set to " + map.value
				updateDataValue("thermostatSetpoint", map.value.toString())
				map2.value = map.value
			} else { 
				if (logEnable) log.debug "thermostatSetpoint being set to " + hsp
				updateDataValue("thermostatSetpoint", hsp)
				map2.value = hsp
			}	
		}
	}
	
	if (tm == "cool") {		
			if (map.name == "coolingSetpoint") {
				if (logEnable) log.debug "thermostatSetpoint being set to " + map.value
				updateDataValue("thermostatSetpoint", map.value.toString())
				map2.value = map.value
			} else { 
				if (logEnable) log.debug "thermostatSetpoint being set to " + csp
				updateDataValue("thermostatSetpoint", csp)
				map2.value = csp
			}	
	}

	if (tm == "heat") {		
			if (map.name == "heatingSetpoint") {
				if (logEnable) log.debug "thermostatSetpoint being set to " + map.value
				updateDataValue("thermostatSetpoint", map.value.toString())
				map2.value = map.value
			} else { 
				if (logEnable) log.debug "thermostatSetpoint being set to " + hsp
				updateDataValue("thermostatSetpoint", hsp)
				map2.value = hsp
			}	
	}

	sendEvent([name: map2.name, value: map2.value, displayed:true, unit: map2.unit, isStateChange:false])

	if (logEnable) log.debug "thermostatSetpoint is " + getDataValue("thermostatSetpoint")
	
	
	if (logEnable) log.debug "ThermostatSetPointReport...END"
	return map
	
}

private zwaveEvent(hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport cmd)
{
	log.debug "thermostatOperatingStateV1...START"
	log.warn "thermostatOperatingStateV1 called. This currently has no fuction in this driver."
	log.debug "thermostatOperatingStateV1...START"
}


private zwaveEvent(hubitat.zwave.commands.thermostatoperatingstatev2.ThermostatOperatingStateReport cmd)
{
	if (logEnable) log.debug "ThermostatOperatingStateReport...START"
	def map = [:]
	switch (cmd.operatingState) {
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_IDLE:
			map.value = "idle"
            break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_HEATING:
			map.value = "heating"
           	break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_COOLING:
			map.value = "cooling"
            break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_FAN_ONLY:
			map.value = "fan only"
			break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_PENDING_HEAT:
			map.value = "pending heat"
            break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_PENDING_COOL:
			map.value = "pending cool"
            break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_VENT_ECONOMIZER:
			map.value = "vent economizer"
            break
	}
	map.name = "thermostatOperatingState"
    if (logEnable) log.debug "In ThermostatOperatingStateReport map is $map"
    sendEvent(map)
    updateDataValue(map.name.toString(),map.value.toString())
	if (logEnable) log.debug "ThermostatOperatingStateReport...END"
}

private zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport cmd) {
	if (logEnable) log.debug "ThermostatModeReport...START"
	if (logEnable) log.debug "cmd: $cmd"
	def map = [name: "thermostatMode", data:[supportedThermostatModes: state.supportedModes]]
	switch (cmd.mode) {
		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_OFF:
			map.value = "off"
			break
		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_HEAT:
			map.value = "heat"
			break
		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_AUXILIARY_HEAT:
			map.value = "emergency heat"
			break
		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_COOL:
			map.value = "cool"
			break
		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_AUTO:
			map.value = "auto"
			break
	}
	
	// Sync lastRunningMode and thermostatSetpoint if the mode is changed
	if (getDataValue("lastRunningMode") != map.value) {
		if (map.value == "cool") {
			updateDataValue("lastRunningMode", "cool")
			def csp = getDataValue("coolingSetpoint")
			updateDataValue("thermostatSetpoint", csp.toString())
			sendEvent([name: "thermostatSetpoint", value: csp, displayed:true, unit: getTemperatureScale(), isStateChange:true])
		} else {
			if (map.value == "heat") {
				updateDataValue("lastRunningMode", "heat")
				def hsp = getDataValue("heatingSetpoint")
				updateDataValue("thermostatSetpoint", hsp.toString())
				sendEvent([name: "thermostatSetpoint", value: hsp, displayed:true, unit: getTemperatureScale(), isStateChange:true])
			}
		}											  
	}
	
	sendEvent(map)
    updateDataValue(map.name.toString(),map.value.toString())
	if (logEnable) log.debug "ThermostatModeReport...END"
}

private zwaveEvent(hubitat.zwave.commands.thermostatmodev1.ThermostatModeSupportedReport cmd) {
	if (logEnable) log.debug "V1 ThermostatModeSupportedReport...START"
	if (logEnable) log.debug "cmd: " + cmd
	def supportedModes = ""
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if (cmd.off) { supportedModes += "off " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.heat) { supportedModes += "heat " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.auxiliaryemergencyHeat) { supportedModes += "emergencyHeat " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.cool) { supportedModes += "cool " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.auto) { supportedModes += "auto " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if (supportedModes) {
		state.supportedModes = supportedModes
	} else {
		supportedModes = "off heat cool auto "
		state.supportedModes = supportedModes
	}
	
	if (logEnable) log.debug "state.supportedModes: " + state.supportedModes
	if (logEnable) log.debug "V1 ThermostatModeSupportedReport...END"
}


private zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeSupportedReport cmd) {
	if (logEnable) log.debug "V2 ThermostatModeSupportedReport...START"
	if (logEnable) log.debug "cmd: " + cmd
	def supportedModes = ""
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if (cmd.off) { supportedModes += "off " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.heat) { supportedModes += "heat " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.auxiliaryemergencyHeat) { supportedModes += "emergencyHeat " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.cool) { supportedModes += "cool " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.auto) { supportedModes += "auto " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
		if (supportedModes) {
		state.supportedModes = supportedModes
	} else {
		supportedModes = "off heat cool auto "
		state.supportedModes = supportedModes
	}
	if (logEnable) log.debug "state.supportedModes: " + state.supportedModes
	if (logEnable) log.debug "V2 ThermostatModeSupportedReport...END"
}

private zwaveEvent(hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeSupportedReport cmd) {
	if (logEnable) log.debug "ThermostatFanModeSupportedReport...START"
	if (logEnable) log.debug "cmd: " + cmd
	//def supportedFanModes = "fanAuto fanOn fanCirculate "
	def supportedFanModes = ""
	if (cmd.auto) { supportedFanModes += "auto " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (cmd.autoHigh) { supportedFanModes += "autoHigh " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (cmd.autoMedium) { supportedFanModes += "autoMedium " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (cmd.circulation) { supportedFanModes += "circulation " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (cmd.high) { supportedFanModes += "high " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (cmd.humidityCirculation) { supportedFanModes += "humidityCirculation " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (cmd.low) { supportedFanModes += "low " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (cmd.medium) { supportedFanModes += "medium " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (supportedFanModes) {
		state.supportedFanModes = supportedFanModes
	} else {
		supportedFanModes = "auto on "
		state.supportedFanModes = supportedFanModes
	}
	if (logEnable) log.debug "ThermostatFanModeSupportedReport...END"
}

private zwaveEvent(hubitat.zwave.commands.thermostatfanstatev1.ThermostatFanStateReport cmd) {
	if (logEnable) log.debug "ThermostatFanStateReport...START"
	if (logEnable) log.debug "cmd: " + cmd
	def map = [name: "thermostatFanState", unit: ""]
	switch (cmd.fanOperatingState) {
		case 0:
			map.value = "idle"
            sendEvent(name: "thermostatFanState", value: "not running")
			break
		case 1:
			map.value = "running"
            sendEvent(name: "thermostatFanState", value: "running")
			break
		case 2:
			map.value = "running high"
            sendEvent(name: "thermostatFanState", value: "running high")
			break
	}
    if (logEnable) log.debug "In ThermostatFanStateReport map is $map"
    if (logEnable) log.debug "ThermostatFanStateReport...END"
}

private zwaveEvent(hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport cmd) {
    if (logEnable) log.debug "ThermostatFanModeReport...START"
	def map = [:]
	switch (cmd.fanMode) {
		case hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport.FAN_MODE_AUTO_LOW:
			map.value = "fanAuto"
            break
		case hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport.FAN_MODE_LOW:
			map.value = "fanOn"
            break
		case hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport.FAN_MODE_CIRCULATION:
			map.value = "fanCirculate"
            break
	}
	map.name = "thermostatFanMode"
	map.displayed = false
    	if (logEnable) log.debug "In ThermostatFanModeReport map is $map"
    	sendEvent(map)
	if (logEnable) log.debug "ThermostatFanModeReport...END"
}

private zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
    if (logEnable) log.debug "In BatteryReport"
    //def result = []
    def map = [:]
    map = [ name: "battery", unit: "%" ]
    if (cmd.batteryLevel == 0xFF) {
        map.value = 1
        map.descriptionText = "${device.displayName} battery is low"
    } else {
        map.value = cmd.batteryLevel
    }
    sendEvent(map)
    return map
}

def updateState(String name, String value) {
	if (logEnable) log.debug "updateState...START"
	state[name] = value
	updateDataValue(name, value)
	if (logEnable) log.debug "updateState...END"
}

private zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	if (logEnable) log.debug "BasicReport...START"
	if (logEnable) log.debug "Zwave event received: $cmd"
	if (logEnable) log.debug "BasicReport...END"
}

private zwaveEvent(hubitat.zwave.Command cmd) {
	log.warn "Unexpected zwave command $cmd"
}
