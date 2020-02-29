######################################################################
#       Filename: measurement.sh                                     #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Mai 31, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: measurement.sh
#
#   Description: 
#
#   Based on the selected conf file instantiates all the necessary 
#   components and starts them.
#
######################################################################


# Deafult waiting time without preemptioni in minutes
LOCAL_DEFAULT_WAITING_TIME=5
REMOTE_DEFAULT_WAITING_TIME=12


# Loads the variables from the  provided config file
echo "Config file selected: $1"
source $1

# Loads the variables from the scenario defined in the config file
echo "Scenario selected: $SCENARIO"
source $SCENARIO

# Extracts the execution environment from the emulator config file defined
# in the selected scenario folder
EXEC_ENV=$(cat ${SCENARIO_PATH}/config | awk -F"=|\"" '/^TARGET=/ {print $3}')
echo "Execution environment selected: $EXEC_ENV"

for (( iteration = 1; iteration <= $EXECUTION_NUMBER; iteration++ )); do

	if [[ $EXEC_ENV = "LOCAL" ]]; then
		echo "Hi LOCAL"
		echo "Scenario selected: $SCENARIO"
		echo ""
		echo "ITERATION number $iteration is being executed"

		# Copies measure-standard, measure-alternative and measure-flow-table-size to ../emulator/common
		echo "Copy scripts that perform measurements on switches to the right location: ../emulator/common"
		cp ./measure-standard.sh ../emulator/common
		cp ./measure-alternative.sh ../emulator/common
		cp ./measure-flow-table-size.sh ../emulator/common

		# Loads the locations for the local execution 
		source exec-local-conf.sh
		
		echo "Bootstrapping implementation location: $LOCAL_BOOTSTRAPPING_PATH"
		echo "VirtuWind-Network-Emulator location: $LOCAL_VIRTUWIND_EMULATOR_PATH"

		# Copy conf files to the configured execution environment
		echo "Copy $EMULATOR_CONFIG to $LOCAL_VIRTUWIND_EMULATOR_PATH"
		cp $EMULATOR_CONFIG $LOCAL_VIRTUWIND_EMULATOR_PATH
		echo "Copy $EXEC_SCRIPT to $LOCAL_BOOTSTRAPPING_PATH"
		cp $EXEC_SCRIPT $LOCAL_BOOTSTRAPPING_PATH
		echo "Copy $BOOTSTRAPPING_SETUP_CONFIG to $LOCAL_BOOTSTRAPPING_PATH"
		cp $BOOTSTRAPPING_SETUP_CONFIG $LOCAL_BOOTSTRAPPING_PATH
		echo "Copy $BOOTSTRAPPING_DHCP_CONFIG to $LOCAL_BOOTSTRAPPING_PATH"
		cp $BOOTSTRAPPING_DHCP_CONFIG $LOCAL_BOOTSTRAPPING_PATH

		# Starts the emulator
		cd $LOCAL_VIRTUWIND_EMULATOR_PATH 
		echo "Cleaning up the previous emulator state"
		echo $SUDO_PASSWORD | sudo -kS ./clean_up.sh
		echo "Starting the emulator"
		echo $SUDO_PASSWORD | sudo -kS ./emulate_network.sh
		cd -
		cd $LOCAL_BOOTSTRAPPING_PATH
		echo $SUDO_PASSWORD | sudo -kS ./exec_karaf_local_background.sh
		echo "Karaf instance started in the background"

		# Wait some time till the bootstrapping is finished
		# Return to the measurements-orchestrator folder
		cd -

		# Extract the number of expected controllers
		CON_NUM=$(cat $EMULATOR_CONFIG | awk -F"=" '/CON_NUM/ {print $2}')
		echo "Expected number of controllers: $CON_NUM"

		# Extract the scheme from the scenario config file
		BOOTSTRAPPING_SCHEME=$(cat $SCENARIO_PATH/config | awk -F"=" '/^BOOTSTRAPPING/ {print $2}' | awk -F"\"" '{print $2}')
		echo "Bootstrapping scheme detected: $BOOTSTRAPPING_SCHEME"
		echo -ne "Bootstrapping is being executed... PLEASE WAIT (0 %)\r"
		# Waiting LOCAL_DEFAULT_WAITING_TIME minutes for the Karaf to start and finish bootstrapping
		# Depending on the switch number may be necessary to vary
		# For the standard scheme check also periodically if the resilience is done and preempt the further waiting accordingly
		time_elapsed=$(( $LOCAL_DEFAULT_WAITING_TIME*60/5 )) 
		counter=0
		preempted=false
		while [[ $counter -le $time_elapsed && $preempted = false ]]; do
			echo -ne "Bootstrapping is being executed... PLEASE WAIT "
			for ((i=1;i<=$counter;i++)); do echo -ne "+"; done
			echo -ne " ($(( $counter*100/$time_elapsed ))%)\r"
			counter=$(( $counter + 1 ))
			for (( i = 0; i < $CON_NUM; i++ )); do
				res1=$(cat $LOCAL_BOOTSTRAPPING_PATH/distribution/opendaylight-karaf/target/assembly/data/log/bootstrapping.log | grep "Measurements done")
				res2=$(cat $LOCAL_BOOTSTRAPPING_PATH/distribution/opendaylight-karaf/target/assembly/data/log/karaf.log | grep "giving up")
				if [[ -n "$res1" || "$res2" ]]; then
					echo ""
					echo "Bootstrapping finished prior defined timeout: $(($time_elapsed * 5)) s -> preempt further waiting"
					preempted=true
				fi

			done
			sleep 5s
		done
		# Wait till the boootsrapping is done
		echo ""
		echo "Bootstrapping should be done till now"

		# Create a folder for the measurements data if necessary
		echo "Creating the folder for the measurements (if it does not already exist)"
		mkdir -p measurement-records-raw/local/$SCENARIO_PATH
		# Find out how many switch containers are instantiated
		TOPOLOGY=$(cat $SCENARIO_PATH/config | awk -F "=|\"" '/TOPOLOGY/ {print $3}')
		echo "Used topology: $TOPOLOGY"
		SW_NUM=$(cat $LOCAL_VIRTUWIND_EMULATOR_PATH/topologies/$TOPOLOGY.sh | awk -F "=" '/^SW_NUM/ {print $2}')
		echo "Emulator contains $SW_NUM switches"
		# For each switch extract measurement records and flow tables
		for (( i = 1; i <= $SW_NUM; i++ )); do
			# Extract flow table size
			echo "Extracting a flow table size"
			echo $SUDO_PASSWORD | sudo -kS docker exec -u root sw_$i /measure-flow-table-size.sh
			echo "Copying sw_$i measurement_record to the measurement-records-raw folder"
			mkdir -p measurement-records-raw/local/$SCENARIO_PATH/iteration_$iteration
			echo $SUDO_PASSWORD | sudo -kS docker cp sw_$i:/measurement_record measurement-records-raw/local/$SCENARIO_PATH/iteration_$iteration/sw_$i 
			echo "Extracting a flow table"
			echo $SUDO_PASSWORD | sudo -kS docker cp sw_$i:/flow_table measurement-records-raw/local/$SCENARIO_PATH/iteration_$iteration/sw_${i}_flow_table 
		done
		echo ""
		
		cd $LOCAL_BOOTSTRAPPING_PATH
		echo "Killing the current Karaf instance"
		echo $SUDO_PASSWORD | sudo -kS ./close_karaf_faster.sh
		cd -
		# Start a new iteration
	else
		echo "Hi REMOTE"
		echo "Scenario selected: $SCENARIO"
		echo ""
		echo "ITERATION number $iteration is being executed"
		

		# Loads the locations for the remote execution 
		source exec-remote-conf.sh
		# Loads the locations for the local machine 
		source exec-local-conf.sh

		# Copies measure-standard, measure-alternative and measure-flow-table-size to ../emulator/common
		echo "Copy scripts that perform measurements on switches to the right location: $REMOTE_EMULATOR_MACHINE:$REMOTE_VIRTUWIND_EMULATOR_PATH/emulator/common"
		scp ./measure-standard.sh $REMOTE_EMULATOR_MACHINE:$REMOTE_VIRTUWIND_EMULATOR_PATH/emulator/common
		scp ./measure-alternative.sh $REMOTE_EMULATOR_MACHINE:$REMOTE_VIRTUWIND_EMULATOR_PATH/emulator/common
		scp ./measure-flow-table-size.sh $REMOTE_EMULATOR_MACHINE:$REMOTE_VIRTUWIND_EMULATOR_PATH/emulator/common

		echo "Bootstrapping implementation locations: ${REMOTE_CONTROLLER_MACHINES[@]}"
		echo "VirtuWind-Network-Emulator location: $REMOTE_EMULATOR_MACHINE"
		
		# Copies measure-standard and measure-alternative to ../emulator/commonon remote
		echo "Copy scripts that perform measurements on switches to the right location: $REMOTE_EMULATOR_MACHINE:$REMOTE_VIRTUWIND_EMULATOR_PATH/emulator/common"
		scp ./measure-standard.sh $REMOTE_EMULATOR_MACHINE:$REMOTE_VIRTUWIND_EMULATOR_PATH/emulator/common
		scp ./measure-alternative.sh $REMOTE_EMULATOR_MACHINE:$REMOTE_VIRTUWIND_EMULATOR_PATH/emulator/common
		#TODO: later maybe sync the folders first (for now assumption that we pushed the code before exec the measurements)
		# Push emulator to the remote VM
		# Push Karaf to the remote VMs

		# Copy conf files to the configured execution environment
		echo "Copy $EMULATOR_CONFIG to $REMOTE_EMULATOR_MACHINE:$REMOTE_VIRTUWIND_EMULATOR_PATH"
		scp $EMULATOR_CONFIG $REMOTE_EMULATOR_MACHINE:$REMOTE_VIRTUWIND_EMULATOR_PATH
		CON_NUM=$(cat $EMULATOR_CONFIG | awk -F"=" '/CON_NUM/ {print $2}')
		echo "Expected number of controllers: $CON_NUM"
		for (( i = 0; i < $CON_NUM; i++ )); do
			echo "Copy $EXEC_SCRIPT to ${REMOTE_CONTROLLER_MACHINES[$i]}:$REMOTE_BOOTSTRAPPING_PATH"
			scp $EXEC_SCRIPT ${REMOTE_CONTROLLER_MACHINES[$i]}:$REMOTE_BOOTSTRAPPING_PATH
			echo "Copy $BOOTSTRAPPING_SETUP_CONFIG to ${REMOTE_CONTROLLER_MACHINES[$i]}:$REMOTE_BOOTSTRAPPING_PATH"
			scp $BOOTSTRAPPING_SETUP_CONFIG ${REMOTE_CONTROLLER_MACHINES[$i]}:$REMOTE_BOOTSTRAPPING_PATH
			echo "Copy $BOOTSTRAPPING_DHCP_CONFIG to ${REMOTE_CONTROLLER_MACHINES[$i]}:$REMOTE_BOOTSTRAPPING_PATH"
			scp $BOOTSTRAPPING_DHCP_CONFIG ${REMOTE_CONTROLLER_MACHINES[$i]}:$REMOTE_BOOTSTRAPPING_PATH
		done
		# Start emulator via SSH
		echo "Starting emulator"
		START_EMULATOR="cd $REMOTE_VIRTUWIND_EMULATOR_PATH; echo openflow | sudo -kS nohup ./emulate_network.sh > log 2>&1 < /dev/null &"
		ssh $REMOTE_EMULATOR_MACHINE $START_EMULATOR
		echo "Waiting some time to instantiate all switches in the emulator"
		echo -n "Starting emulator... PLEASE WAIT +"
		while [[ $(ssh $REMOTE_EMULATOR_MACHINE pgrep emulate) ]]; do
			sleep 5s
			echo -n "+"
		done
		echo ""
		echo "Emulator started"
		# Start Karaf instances on different VMs via SSH
		# Check for the number of controllers to see which script to start
		for (( i = 0; i < $CON_NUM; i++ )); do
			if [[ $CON_NUM -eq 1 ]]; then
				START_CONTROLLER="cd $REMOTE_BOOTSTRAPPING_PATH; echo openflow | sudo -kS nohup ./exec_karaf_remote_background.sh > /dev/null 2>&1 &" 
				echo "SSH to ${REMOTE_CONTROLLER_MACHINES[$i]}"	
				echo "Starting karaf instance..."
				ssh ${REMOTE_CONTROLLER_MACHINES[$i]} $START_CONTROLLER
			else
				START_CONTROLLER="cd $REMOTE_BOOTSTRAPPING_PATH; echo openflow | sudo -kS nohup ./exec_karaf_cluster.sh $(($i + 1)) > /dev/null 2>&1 &" 
				echo "SSH to ${REMOTE_CONTROLLER_MACHINES[$i]}"	
				echo "Starting karaf instance..."
				ssh ${REMOTE_CONTROLLER_MACHINES[$i]} $START_CONTROLLER
			fi
		done
		# Extract the scheme from the scenario config file
		BOOTSTRAPPING_SCHEME=$(cat $SCENARIO_PATH/config | awk -F"=" '/^BOOTSTRAPPING/ {print $2}' | awk -F"\"" '{print $2}')
		echo "Bootstrapping scheme detected: $BOOTSTRAPPING_SCHEME"
		echo -ne "Bootstrapping is being executed... PLEASE WAIT (0 %)\r"
		# Waiting REMOTE_DEFAULT_WAITING_TIME minutes for the Karaf to start and finish bootstrapping
		# Depending on the switch number may be necessary to vary
		# Loop will be stopped if the controllers fail to synchronize or if the bootstrapping is finished prior the timeout expiration
		time_elapsed=$(( $REMOTE_DEFAULT_WAITING_TIME*60/5 )) 
		counter=0
		preempted=false
		while [[ $counter -le $time_elapsed && $preempted = false ]]; do
			echo -ne "Bootstrapping is being executed... PLEASE WAIT "
			for ((i=1;i<=$counter;i++)); do echo -ne "+"; done
			echo -ne " ($(( $counter*100/$time_elapsed ))%)\r"
			counter=$(( $counter + 1 ))
			for (( i = 0; i < $CON_NUM; i++ )); do
				res1=$(ssh ${REMOTE_CONTROLLER_MACHINES[$i]} cat $REMOTE_BOOTSTRAPPING_PATH/distribution/opendaylight-karaf/target/assembly/data/log/bootstrapping.log | grep "Measurements done")
				res2=$(ssh ${REMOTE_CONTROLLER_MACHINES[$i]} cat $REMOTE_BOOTSTRAPPING_PATH/distribution/opendaylight-karaf/target/assembly/data/log/karaf.log | grep "giving up")
				#echo "res1:$res1 res2:$res2"
				if [[ -n "$res1" || "$res2" ]]; then
					echo ""
					echo "Bootstrapping finished prior defined timeout: $(($time_elapsed * 5)) s -> preempt further waiting"
					preempted=true
				fi

			done
			sleep 5s
		done
		# Wait till the boootsrapping is done
		echo ""
		echo "Bootstrapping should be done till now"

		# Before closing the emulator and Karaf instances we have to first extract the measurements data
		# Create a folder for the measurements data if necessary
		echo "Creating the folder for the measurements (if it does not already exist)"
		mkdir -p measurement-records-raw/remote/$SCENARIO_PATH/iteration_$iteration
		# Find out how many switch containers are instantiated
		TOPOLOGY=$(cat $SCENARIO_PATH/config | awk -F "=|\"" '/TOPOLOGY/ {print $3}')
		echo "Used topology: $TOPOLOGY"
		SW_NUM=$(cat $LOCAL_VIRTUWIND_EMULATOR_PATH/topologies/$TOPOLOGY.sh | awk -F "=" '/^SW_NUM/ {print $2}')
		echo "Emulator contains $SW_NUM switches"
		# For each switch extract measurement records
		for (( i = 1; i <= $SW_NUM; i++ )); do
			echo "Extract a flow table size"
			EXTRACT_FLOW_TABLE_SIZE_REMOTE="echo openflow | sudo -kS docker exec -u root sw_$i /measure-flow-table-size.sh"
			ssh $REMOTE_EMULATOR_MACHINE $EXTRACT_FLOW_TABLE_SIZE_REMOTE 
			echo "Copying sw_$i measurement_record to the measurement-records-raw folder remotely"
			EXTRACT_DATA_REMOTE="cd $REMOTE_VIRTUWIND_EMULATOR_PATH/measurements-orchestrator; mkdir -p measurement-records-raw/remote/$SCENARIO_PATH/iteration_$iteration; echo openflow | sudo -kS docker cp sw_$i:/measurement_record measurement-records-raw/remote/$SCENARIO_PATH/iteration_$iteration/sw_$i; echo openflow | sudo -kS docker cp sw_$i:/flow_table measurement-records-raw/remote/$SCENARIO_PATH/iteration_$iteration/sw_${i}_flow_table"
			ssh $REMOTE_EMULATOR_MACHINE $EXTRACT_DATA_REMOTE
			# Copy data to the local machine for further processing
			echo "Copying measurement-records-raw to the local machine"
			scp -r $REMOTE_EMULATOR_MACHINE:$REMOTE_VIRTUWIND_EMULATOR_PATH/measurements-orchestrator/measurement-records-raw/remote/$SCENARIO_PATH/iteration_$iteration/sw_${i}* $LOCAL_VIRTUWIND_EMULATOR_PATH/measurements-orchestrator/measurement-records-raw/remote/$SCENARIO_PATH/iteration_$iteration
		done

		# Kill the running programms
		# Closing Karaf instances
		echo "Closing Karaf instances"
		for (( i = 0; i < $CON_NUM; i++ )); do
			CLOSE_CONTROLLER="cd $REMOTE_BOOTSTRAPPING_PATH; echo openflow | sudo -kS nohup ./close_karaf_faster.sh > /dev/null 2>&1 &"
			echo "SSH to ${REMOTE_CONTROLLER_MACHINES[$i]}"	
			echo "Closing Karaf instance on ${REMOTE_CONTROLLER_MACHINES[$i]}"
			ssh ${REMOTE_CONTROLLER_MACHINES[$i]} $CLOSE_CONTROLLER
			echo "Checking if instance closed"
			echo -n "Closing Karaf... PLEASE WAIT +"
			while [[ $(ssh ${REMOTE_CONTROLLER_MACHINES[$i]} pgrep karaf) ]]; do
				echo -n "+" 
				sleep 1s
			done
			echo ""
			echo "Karaf instance closed on ${REMOTE_CONTROLLER_MACHINES[$i]}"
		done

		# Closing the emulator
		echo "Cleaning the emulator"
		CLEANING_EMULATOR="cd $REMOTE_VIRTUWIND_EMULATOR_PATH; echo openflow | sudo -kS nohup ./clean_up.sh > /dev/null 2>&1 &"
		ssh $REMOTE_EMULATOR_MACHINE $CLEANING_EMULATOR
		echo -n "Emulator cleaning in progress... PLEASE WAIT +"
		while [[ $(ssh $REMOTE_EMULATOR_MACHINE "echo openflow | sudo -kS docker ps | awk '/sw/'") ]]; do
			sleep 5s
			echo -n "+"
		done
		echo ""
		# next iteration 


	fi
done

