######################################################################
#       Filename: scenario1.sh                                       #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Mai 31, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: scenario1.sh
#
#   Description: 
#
#   Here define the emulator config and the KARAF configs that you
#   want to use for your scenario.
#
######################################################################

EMULATOR_CONFIG="$SCENARIO_PATH/config"

EXEC_SCRIPT="$SCENARIO_PATH/exec_karaf_cluster.sh"
BOOTSTRAPPING_SETUP_CONFIG="$SCENARIO_PATH/149-bootstrapping-manager-setup-REMOTE-CLUSTER.xml"
BOOTSTRAPPING_DHCP_CONFIG="$SCENARIO_PATH/150-bootstrapping-manager-dhcp-REMOTE.xml"


