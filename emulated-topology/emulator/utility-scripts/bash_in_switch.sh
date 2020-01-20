#!/bin/bash

######################################################################
#       Filename: bash_in_switch.sh                                  #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 17, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: bash_in_switch.sh
#
#   Description: 
#
#   Opens bash in the given switch docker container. 
######################################################################

sudo docker exec -u root -it "$1" bash
