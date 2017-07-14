/**
 *  AirBnb Virtual Presence Device
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
metadata {
	definition (name: "AirBnb Virtual Presence Device", namespace: "joshs85", author: "Joshua Spain") {
        capability "Presence Sensor"
		command "arrived"
		command "departed"
    }

	// simulator metadata
	simulator {
	}

	// UI tile definitions
	tiles {
        standardTile("presence", "device.presence", width: 3, height: 4, canChangeBackground: true) {
			state("present", labelIcon:"st.presence.tile.mobile-present", backgroundColor:"#53a7c0")
			state("not present", labelIcon:"st.presence.tile.mobile-not-present", backgroundColor:"#ffffff")
		}
		main (["presence"])
		details(["presence"])
	}
}

def parse(String description) {

}

// handle commands
def arrived() {
	sendEvent(name: "presence", value: "present")
}


def departed() {
    sendEvent(name: "presence", value: "not present")
}