######################################################################
#       Filename: create_scenario.py                                 #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Jul 20, 2018                                          #
#                                                                    #
######################################################################  
import os
import re
import shutil
import fileinput

scenarios_file="./describe_scenarios_remote_final"
execution_number=30

scenarios=[]
# parse scenarios_file
with open(scenarios_file) as f:
    for line in f:
        if not line.isspace():
            print("Processing: " + line)
            parsed_line = line.split(",")
            temp = {}
            temp["scenario_name"] = parsed_line[0].strip()
            temp["topology"] = parsed_line[1].strip()
            temp["scheme"] = parsed_line[2].strip()
            temp["environment"] = parsed_line[3].strip()
            temp["con_num"] = parsed_line[4].strip()
            temp["con_pos"] = parsed_line[5].strip()

            scenarios.append(temp)

# for each defined scenario 
for scenario in scenarios:
    #create folder with the scenario name
    scenario_dir = "scenarios/" + scenario["scenario_name"]
    if not os.path.exists(scenario_dir):
        os.mkdir(scenario_dir)
    # based on the selected environment and scheme copy necessary xml config files in the dir
    # copy also necessary startup scriptsi and other template config files
    if scenario["environment"] == "LOCAL":
        if scenario["scheme"] == "STANDARD":
            shutil.copy("../../demo-sdn/149-bootstrapping-manager-setup-LOCAL.xml", scenario_dir)
            shutil.copy("../../demo-sdn/150-bootstrapping-manager-dhcp-LOCAL.xml", scenario_dir)
            shutil.copy("./scenarios/templates/exec_karaf_local_background_standard.sh", scenario_dir + "/exec_karaf_local_background.sh")
            shutil.copy("./scenarios/templates/scenario_local_standard.sh", scenario_dir + "/scenario.sh")
        elif scenario["scheme"] == "ALTERNATIVE":
            shutil.copy("../../demo-sdn/149-bootstrapping-manager-alternative-setup-LOCAL.xml", scenario_dir)
            shutil.copy("../../demo-sdn/150-bootstrapping-manager-alternative-dhcp-LOCAL.xml", scenario_dir)
            shutil.copy("./scenarios/templates/exec_karaf_local_background_alternative.sh", scenario_dir + "/exec_karaf_local_background.sh")
            shutil.copy("./scenarios/templates/scenario_local_alternative.sh", scenario_dir + "/scenario.sh")
    elif scenario["environment"] == "REMOTE":
        if scenario["scheme"] == "STANDARD":
            if int(scenario["con_num"]) > 1:
                shutil.copy("../../demo-sdn/149-bootstrapping-manager-setup-REMOTE-CLUSTER.xml", scenario_dir)
                shutil.copy("../../demo-sdn/150-bootstrapping-manager-dhcp-REMOTE.xml", scenario_dir)
                shutil.copy("./scenarios/templates/exec_karaf_cluster_standard.sh", scenario_dir + "/exec_karaf_cluster.sh")
                shutil.copy("./scenarios/templates/scenario_remote_standard_cluster.sh", scenario_dir + "/scenario.sh")
            elif int(scenario["con_num"]) == 1:
                shutil.copy("../../demo-sdn/149-bootstrapping-manager-setup-REMOTE.xml", scenario_dir)
                shutil.copy("../../demo-sdn/150-bootstrapping-manager-dhcp-REMOTE.xml", scenario_dir)
                shutil.copy("./scenarios/templates/exec_karaf_remote_background_standard.sh", scenario_dir + "/exec_karaf_remote_background.sh")
                shutil.copy("./scenarios/templates/scenario_remote_standard.sh", scenario_dir + "/scenario.sh")
        elif scenario["scheme"] == "ALTERNATIVE":
            if int(scenario["con_num"]) > 1: #(x)"
                shutil.copy("../../demo-sdn/149-bootstrapping-manager-alternative-setup-REMOTE-CLUSTER.xml", scenario_dir)
                shutil.copy("../../demo-sdn/150-bootstrapping-manager-alternative-dhcp-REMOTE.xml", scenario_dir)
                shutil.copy("./scenarios/templates/exec_karaf_cluster_alternative.sh", scenario_dir + "/exec_karaf_cluster.sh")
                shutil.copy("./scenarios/templates/scenario_remote_alternative_cluster.sh", scenario_dir + "/scenario.sh")
            elif int(scenario["con_num"]) == 1: # (x)
                shutil.copy("../../demo-sdn/149-bootstrapping-manager-alternative-setup-REMOTE.xml", scenario_dir)
                shutil.copy("../../demo-sdn/150-bootstrapping-manager-alternative-dhcp-REMOTE.xml", scenario_dir)
                shutil.copy("./scenarios/templates/exec_karaf_remote_background_alternative.sh", scenario_dir + "/exec_karaf_remote_background.sh")
                shutil.copy("./scenarios/templates/scenario_remote_alternative.sh", scenario_dir + "/scenario.sh")
    else:
        print("Wrong describe_scenarios file format")
    # for each template config file change necessary lines in order for the scenario to work correctly
    # processing config file
    shutil.copy("./scenarios/templates/config", scenario_dir)
    for line in fileinput.input(scenario_dir + "/config", inplace=True):
         # inside this loop the STDOUT will be redirected to the file
         # the comma after each print statement is needed to avoid double line breaks
         if "TARGET=\"LOCAL\"" in line:
             print line.replace("TARGET=\"LOCAL\"","TARGET=\"" + scenario["environment"] + "\""),
         elif "BOOTSTRAPPING=\"ALTERNATIVE\"" in line:
             line = re.sub(r"^BOOTSTRAPPING=\"ALTERNATIVE\"", "BOOTSTRAPPING=\"" + scenario["scheme"] + "\"", line)
             print line,
         elif "CON_NUM=1" in line:
             print line.replace("CON_NUM=1", "CON_NUM=" + scenario["con_num"]),
         elif "TOPOLOGY=\"line8\"" in line:
             print line.replace("TOPOLOGY=\"line8\"", "TOPOLOGY=\"" + scenario["topology"] + "\""),
         elif "CON_POSITION=(1)" in line:
             print line.replace("CON_POSITION=(1)", "CON_POSITION=" + scenario["con_pos"]),
         else:
             print line,
    
    # create conf files automatically that are necessary for the orchestrator
    # extract numbers from the name
    nums=scenario["scenario_name"][-2:]
    if not nums[0].isdigit():
        nums=nums[-1]
    #print(nums)
    config_name = "./configs/conf" + nums + ".sh"
    shutil.copy("./scenarios/templates/conf1.sh", config_name)
    for line in fileinput.input(config_name, inplace=True):
        if "SCENARIO_PATH=\"scenarios/scenario1\"" in line:
            print line.replace("SCENARIO_PATH=\"scenarios/scenario1\"", "SCENARIO_PATH=\"scenarios/scenario" + nums + "\""),
        elif "EXECUTION_NUMBER=20" in line:
            print line.replace("EXECUTION_NUMBER=20", "EXECUTION_NUMBER=" + str(execution_number)),
        else:
            print line,

print(scenarios)


        

