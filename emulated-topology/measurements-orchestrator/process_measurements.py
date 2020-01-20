######################################################################
#       Filename: data-processing.py                                 #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Jul 07, 2018                                          #
#                                                                    #
######################################################################  

import os
import re
import StringIO
import shutil
from utilities import *
import datetime
import subprocess

bashCommand = "rm -r *backup*"
try:
    output = subprocess.check_output(['bash','-c', bashCommand])
except subprocess.CalledProcessError as e:
    print("All backup folders already deleted")


# Name of the folder where the processed data is stored
processed_folder_absolute = "measurement-records-processed-absolute"
processed_folder_relative = "measurement-records-processed-relative"
# Creating folders
if os.path.exists(processed_folder_absolute):
    # make a backup of the old data
    shutil.copytree(processed_folder_absolute, processed_folder_absolute + "-backup-" +  datetime.datetime.now().strftime('%d-%m-%Y-%H-%M-%S'))
    # Clear previous data if exists
    shutil.rmtree(processed_folder_absolute, ignore_errors=True)
    # create a fresh one 
    os.mkdir(processed_folder_absolute)
else:
    shutil.rmtree(processed_folder_absolute, ignore_errors=True)

if os.path.exists(processed_folder_relative):
    # make a backup of the old data
    shutil.copytree(processed_folder_relative, processed_folder_relative + "-backup-" +  datetime.datetime.now().strftime('%d-%m-%Y-%H-%M-%S'))
    # Clear previous data if exists
    shutil.rmtree(processed_folder_relative, ignore_errors=True)
    # create a fresh one 
    os.mkdir(processed_folder_relative)
else:
    shutil.rmtree(processed_folder_relative, ignore_errors=True)

scenarios=[]

# find all scenarios for which the measurements have been done
for dirname, dirnames, filenames in os.walk('./measurement-records-raw'):
    # print path to all subdirectories first.
    expr=(subdirname for subdirname in dirnames if re.match('scenario[0-9]', subdirname))
    for subdirname in expr:
        print(os.path.join(dirname, subdirname))
        scenario = Scenario(os.path.join(dirname, subdirname))
        scenarios.append(scenario)

    # print path to all filenames.
    #expr=(filename for filename in filenames if re.match('sw_*', filename))
    #for filename in expr:
    #    scenario = Scenario(path_from_list(dirname.split("/")[1:-1]))
    #    print(scenario.path)
    #    print(os.path.join(dirname, filename))

print("Considered scenarios:")
print(scenarios)
print("")
for scenario in scenarios:
    print("Processing scenario: " + scenario.path.split("/")[-1])
    scenario.process_scenario()
    print(scenario.succ_iteration_indexes)
    print(scenario.unsucc_iteration_indexes)
    scenario.create_output_absolute(processed_folder_absolute + "/" + scenario.path.split("/",2)[2])
    scenario.create_output_relative(processed_folder_relative + "/" + scenario.path.split("/",2)[2])
    #for switch in scenario.switches.values():
    #    print(switch.name)
    #    print(switch.get_min_max_ip_assigned_timestamp())
    print(scenario.get_iteration_summary(0))

