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
from datetime import datetime
from datetime import time
from datetime import date


def path_from_list(list):
    """Creates a string path from the list of strings

    :list: TODO
    :returns: TODO

    """
    path=""
    for item in list:
        path += item + "/"
    return path[:-1]


class Switch(object):

    """Parent class for all switches"""
    # time format of the extracted timestamps
    timeformat="%H:%M:%S.%f"

    def __init__(self, name):
        """Initial cnstructor """
        self.name = name
        self.ip_assigned_timestamps = []
        self.resilience_installed_absolute_timestamps = []
        self.resilience_installed_relative_timestamps = []
        self.flow_table_sizes = []

    def __eq__(self, other):
        """define how to compare equality between switch  and other object types """
        if type(other) is Switch:
            if self.name == other.name:
                return True
            else:
                return False
        elif type(other) is str:
            if self.name == other:
                return True
            else:
                return False
        else:
            return False

    def get_max_table_size(self, indexes=None):
        """get max table size for elements that are indexed
           via indexes
           if indexes is not provided then the max function is invoked
           one the entire sample set

        :indexes: indexes of elements on which max function should be executed
        :returns: TODO

        """
        if indexes == None:
            int_flow_table_size = [int(x) for x in self.flow_table_sizes if x != "Missing"]
            return max(int_flow_table_size)
        elif len(indexes) == 0:
            return "No successful iterations"
        else:
            int_flow_table_size = [int(x) for index, x in enumerate(self.flow_table_sizes) if x != "Missing" and index in indexes]
            return max(int_flow_table_size)
    
    def get_min_table_size(self, indexes=None):
        """returns min value in flow_table_sizes taking into account only elements
           defined in indexes 
           if indexes is not provided then min is computed on the whole sample set

        :indexes: indexes of elements on which max function should be executed
        :returns: TODO

        """
        if indexes == None:
            int_flow_table_size = [int(x) for x in self.flow_table_sizes if x != "Missing"]
            return min(int_flow_table_size)
        elif len(indexes) == 0:
            return "No successful iterations"
        else:
            int_flow_table_size = [int(x) for index, x in enumerate(self.flow_table_sizes) if x != "Missing" and index in indexes and int(x) != 0]
            return min(int_flow_table_size)
    

    def get_avg_table_size(self, indexes=None):
        """returns avg value for flow_table_sizes taking into account only elements
            defined in indexes
           if indexes is not provided then avg is computed on the whole sample set

        :indexes: indexes of elements on which max function should be executed
        :returns: TODO

        """
        if indexes == None:
            int_flow_table_size = [int(x) for x in self.flow_table_sizes if x != "Missing"]
            return float(sum(int_flow_table_size))/len(int_flow_table_size)
        elif len(indexes) == 0:
            return "No successful iterations"
        else:
            int_flow_table_size = [int(x) for index, x in enumerate(self.flow_table_sizes) if x != "Missing" and index in indexes and int(x) != 0]
            return float(sum(int_flow_table_size))/len(int_flow_table_size)

    def get_iteration_table_size(self, iteration):
        """returns flow table size of a switch measured in the given iteration

        :iteration: TODO
        :returns: TODO

        """
        return self.flow_table_sizes[iteration]
        

    def get_iter_num(self):
        """find out how many iterations this object stores

        :f: TODO
        :returns: TODO

        """
        return len(self.ip_assigned_timestamps)

    def add_ip_assigned_timestamp(self, timestamp):
        """add a new timestamp to the ip_assigned_timestamps

        :timestamp: TODO
        :returns: TODO

        """
        self.ip_assigned_timestamps.append(timestamp)
    
    def add_resilience_installed_absolute_timestamp(self, controllers_timestamps):
        """add a new dictionary timestamp to the resilience_installed_absolute_timestamps

        :timestamp: TODO
        :returns: TODO

        """
        self.resilience_installed_absolute_timestamps.append(controllers_timestamps) 

    def add_resilience_installed_relative_timestamp(self, controllers_timestamps):
        """add a new dictionary timestamp to the resilience_installed_relative_timestamps
        :returns: TODO

        """
        self.resilience_installed_relative_timestamps.append(controllers_timestamps)

    def add_flow_table_size(self, size):
        """add a new flow table size to flow_table_sizes

        """
        self.flow_table_sizes.append(size)

    def get_min_max_general(self, timestamp_string_list):
        """returns min max tuple of a provided argument

        :timestamp_string_list: TODO
        :returns: TODO

        """

        timestamp_datetime_list = [datetime.strptime(timestamp, self.timeformat) for timestamp in timestamp_string_list if timestamp != "Missing"]
        timestamp_max = timestamp_datetime_list[0]
        timestamp_min = timestamp_datetime_list[0]
        
        for timestamp in timestamp_datetime_list:
            if timestamp > timestamp_max:
                timestamp_max = timestamp
            if timestamp < timestamp_min:
                timestamp_min = timestamp

        return (timestamp_min, timestamp_max)

    def get_min_max_ip_assigned_timestamp(self):
        """returns a tuple of the minimal and maximal values found for
            the ip_assigned_timestamps list
        :returns: TODO

        """

        return self.get_min_max_general(self.ip_assigned_timestamps)

class SwitchStandard(Switch):
    """Switch configured for the standard measurements"""

    def __init__(self, name):
        super(SwitchStandard, self).__init__(name)
        self.initial_rules_installed_absolute_timestamps = []
        self.initial_rules_installed_relative_timestamps = []
        
    def add_initial_rules_installed_absolute_timestamps(self, timestamp):
        """add a new timestamp to the initial_rules_installed_absolute_timestamps

        :timestamp: TODO
        :returns: TODO

        """
        self.initial_rules_installed_absolute_timestamps.append(timestamp)    

    def add_initial_rules_installed_relative_timestamps(self, timestamp):
        """add a new timestamp to the initial_rules_installed_relative_timestamps

        :timestamp: TODO
        :returns: TODO

        """
        self.initial_rules_installed_relative_timestamps.append(timestamp)    


    def convert_to_csv_string_absolute(self):
        """convert the current object state to the csv string
        :returns: TODO

        """
        iter_num = self.get_iter_num()
        # find the max number of controllers by examining each iteration
        max = 0
        for iter in range(iter_num):
            if self.resilience_installed_absolute_timestamps[iter] != "Missing":
                if(max < len(self.resilience_installed_absolute_timestamps[iter].keys())):
                    max = iter
        #print("iter_num:" + str(iter_num))
        # create a header
        if self.resilience_installed_absolute_timestamps[max] == "Missing":
            print("None of the controllers were found! Something went wrong during the bootstrapping procesure")
            return "Unsuccessful bootstrapping"
        controllers=list(self.resilience_installed_absolute_timestamps[max].keys())
        csv_string="Flow table size, IP timestamp, Initial OF rules timestamp, "
        for index, controllerip in enumerate(sorted(controllers), start=1):
            if index == len(controllers):
                csv_string += "Resilience installed for " + controllerip 
            else:
                csv_string += "Resilience installed for " + controllerip + ", "
        csv_string += "\n"
            
        line_string = ""
        for i in range(iter_num):
            line_string += self.flow_table_sizes[i] + ", " 
            line_string += self.ip_assigned_timestamps[i] + ", " 
            line_string += self.initial_rules_installed_absolute_timestamps[i] + ", " 
            for index, key in enumerate(sorted(controllers), start=1):
                #print("Keys:")
                #for item in self.resilience_installed_absolute_timestamps:
                #    print(sorted(item.keys()))
                try:
                    if index == len(controllers):
                        line_string += self.resilience_installed_absolute_timestamps[i][key] 
                    else:
                        line_string += self.resilience_installed_absolute_timestamps[i][key] + ", "
                except (KeyError, TypeError):
                    if index == len(controllers):
                        line_string += "Missing"
                    else:
                        line_string += "Missing, "
                    
            line_string += "\n"
            csv_string += line_string
            #print(csv_string)
            line_string = ""

        return csv_string
    
    def convert_to_csv_string_relative(self):
        """convert the current object state to the csv string
        :returns: TODO

        """
        iter_num = self.get_iter_num()
        # find the max number of controllers by examining each iteration
        max = 0
        for iter in range(iter_num):
            if self.resilience_installed_relative_timestamps[iter] != "Missing":
                if(max < len(self.resilience_installed_relative_timestamps[iter].keys())):
                     max = iter
        #print("iter_num:" + str(iter_num))
        # create a header
        if self.resilience_installed_relative_timestamps[max] == "Missing":
            print("None of the controllers were found! Something went wrong during the bootstrapping procesure")
            return "Unsuccessful bootstrapping"
        controllers=list(self.resilience_installed_relative_timestamps[max].keys())
        csv_string="Flow table size, IP timestamp, Initial OF rules timestamp, "
        for index, controllerip in enumerate(sorted(controllers), start=1):
            if index == len(controllers):
                csv_string += "Resilience installed for " + controllerip 
            else:
                csv_string += "Resilience installed for " + controllerip + ", "
        csv_string += "\n"
            
        line_string = ""
        for i in range(iter_num):
            line_string += self.flow_table_sizes[i] + ", " 
            line_string += self.ip_assigned_timestamps[i] + ", " 
            line_string += self.initial_rules_installed_relative_timestamps[i] + ", " 
            for index, key in enumerate(sorted(controllers), start=1):
                #print("Keys:")
                #for item in self.resilience_installed_relative_timestamps:
                #    print(sorted(item.keys()))
                try:
                    if index == len(controllers):
                        line_string += self.resilience_installed_relative_timestamps[i][key] 
                    else:
                        line_string += self.resilience_installed_relative_timestamps[i][key] + ", "
                except (KeyError, TypeError):
                    if index == len(controllers):
                        line_string += "Missing"
                    else:
                        line_string += "Missing, "
                    
            line_string += "\n"
            csv_string += line_string
            #print(csv_string)
            line_string = ""

        return csv_string


    def export_csv_to_file_absolute(self, file):
        """creates a csv file of the switch measurements

        :file: TODO
        :returns: TODO

        """
        csv_string = StringIO.StringIO(self.convert_to_csv_string_absolute())

        #print(csv_string.getvalue())
        # if the path to the file does not exist create it
        if not os.path.exists(os.path.dirname(file)):
            try:
                os.makedirs(os.path.dirname(file))
            except OSError as exc: # Guard against race condition
                if exc.errno != errno.EEXIST:
                    raise
    
        # open file and write in it line by line in a csv format
        with open(file, "a+") as output:
            for line in csv_string:
                output.write(line)
    
    def export_csv_to_file_relative(self, file):
        """creates a csv file of the switch measurements

        :file: TODO
        :returns: TODO

        """
        csv_string = StringIO.StringIO(self.convert_to_csv_string_relative())

        #print(csv_string.getvalue())
        # if the path to the file does not exist create it
        if not os.path.exists(os.path.dirname(file)):
            try:
                os.makedirs(os.path.dirname(file))
            except OSError as exc: # Guard against race condition
                if exc.errno != errno.EEXIST:
                    raise
    
        # open file and write in it line by line in a csv format
        with open(file, "a+") as output:
            for line in csv_string:
                output.write(line)





class SwitchAlternative(Switch):
    """Switch configured for the alternative measurements"""

    def __init__(self, name):
        super(SwitchAlternative, self).__init__(name)
        self.initial_rules_phaseI_installed_absolute_timestamps = []
        self.initial_rules_phaseII_installed_absolute_timestamps = []
        self.initial_rules_phaseI_installed_relative_timestamps = []
        self.initial_rules_phaseII_installed_relative_timestamps = []

    def add_initial_rules_phaseI_installed_absolute_timestamps(self, timestamp):
        """add a new timestamp ti the initial_rules_phaseI_installed_absolute_timestamps

        :timestamp: TODO
        :returns: TODO
        """
        self.initial_rules_phaseI_installed_absolute_timestamps.append(timestamp)

    def add_initial_rules_phaseII_installed_absolute_timestamps(self, timestamp):
        """add a new timestamp to the initial_rules_phaseII_installed_absolute_timestamps

        :timestamp: TODO
        :returns: TODO

        """
        self.initial_rules_phaseII_installed_absolute_timestamps.append(timestamp)

    def add_initial_rules_phaseI_installed_relative_timestamps(self, timestamp):
        """add a new timestamp to the initial_rules_phaseI_installed_relative_timestamps

        :timestamp: TODO
        :returns: TODO

        """
        self.initial_rules_phaseI_installed_relative_timestamps.append(timestamp)

    def add_initial_rules_phaseII_installed_relative_timestamps(self, timestamp):
        """add a new timestamp to the initial_rules_phaseII_installed_relative_timestamps

        :timestamp: TODO
        :returns: TODO

        """
        self.initial_rules_phaseII_installed_relative_timestamps.append(timestamp)
        
    def convert_to_csv_string_absolute(self):
        """convert the current object state to the csv string
        :returns: TODO

        """
        iter_num = self.get_iter_num()
        # find the max number of controllers by examining each iteration
        max = 0
        for iter in range(iter_num):
            if self.resilience_installed_absolute_timestamps[iter] != "Missing":
                if(max < len(self.resilience_installed_absolute_timestamps[iter].keys())):
                    max = iter
        # create a header
        if self.resilience_installed_absolute_timestamps[max] == "Missing":
            print("None of the controllers were found! Something went wrong during the bootstrapping procesure")
            return "Unsuccessful bootstrapping"
        controllers=list(self.resilience_installed_absolute_timestamps[max].keys())
        csv_string="Flow table size, IP timestamp, Initial OF Phase I timestamp, Initial OF PhaseII timestamp, "
        for index, controllerip in enumerate(sorted(controllers), start=1):
            if index == len(controllers):
                csv_string += "Resilience installed for " + controllerip 
            else:
                csv_string += "Resilience installed for " + controllerip + ", "
        csv_string += "\n"
            
        line_string = ""
        for i in range(iter_num):
            line_string += self.flow_table_sizes[i] + ", " 
            line_string += self.ip_assigned_timestamps[i] + ", " 
            line_string += self.initial_rules_phaseI_installed_absolute_timestamps[i] + ", " 
            line_string += self.initial_rules_phaseII_installed_absolute_timestamps[i] + ", " 
            for index, key in enumerate(sorted(controllers), start=1):
                try:
                    if index == len(controllers):
                        line_string += self.resilience_installed_absolute_timestamps[i][key]
                    else:
                        line_string += self.resilience_installed_absolute_timestamps[i][key] + ", " 
                except (KeyError, TypeError):
                    if index == len(controllers):
                        line_string += "Missing"
                    else:
                        line_string += "Missing, "

            line_string += "\n"
            csv_string += line_string
            line_string = ""

        return csv_string
    
    def convert_to_csv_string_relative(self):
        """convert the current object state to the csv string
        :returns: TODO

        """
        iter_num = self.get_iter_num()
        # find the max number of controllers by examining each iteration
        max = 0
        for iter in range(iter_num):
            if self.resilience_installed_relative_timestamps[iter] != "Missing":
                if(max < len(self.resilience_installed_relative_timestamps[iter].keys())):
                    max = iter
        # create a header
        if self.resilience_installed_relative_timestamps[max] == "Missing":
            print("None of the controllers were found! Something went wrong during the bootstrapping procesure")
            return "Unsuccessful bootstrapping"
        controllers=list(self.resilience_installed_relative_timestamps[max].keys())
        csv_string="Flow table size, IP timestamp, Initial OF Phase I timestamp, Initial OF PhaseII timestamp, "
        for index, controllerip in enumerate(sorted(controllers), start=1):
            if index == len(controllers):
                csv_string += "Resilience installed for " + controllerip 
            else:
                csv_string += "Resilience installed for " + controllerip + ", "
        csv_string += "\n"
            
        line_string = ""
        for i in range(iter_num):
            line_string += self.flow_table_sizes[i] + ", " 
            line_string += self.ip_assigned_timestamps[i] + ", " 
            line_string += self.initial_rules_phaseI_installed_relative_timestamps[i] + ", " 
            line_string += self.initial_rules_phaseII_installed_relative_timestamps[i] + ", " 
            for index, key in enumerate(sorted(controllers), start=1):
                try:
                    if index == len(controllers):
                        line_string += self.resilience_installed_relative_timestamps[i][key]
                    else:
                        line_string += self.resilience_installed_relative_timestamps[i][key] + ", " 
                except (KeyError, TypeError):
                    if index == len(controllers):
                        line_string += "Missing"
                    else:
                        line_string += "Missing, "

            line_string += "\n"
            csv_string += line_string
            line_string = ""

        return csv_string


    def export_csv_to_file_absolute(self, file):
        """creates a csv file of the switch measurements

        :file: TODO
        :returns: TODO

        """
        csv_string = StringIO.StringIO(self.convert_to_csv_string_absolute())

        # if the path to the file does not exist create it
        if not os.path.exists(os.path.dirname(file)):
            try:
                os.makedirs(os.path.dirname(file))
            except OSError as exc: # Guard against race condition
                if exc.errno != errno.EEXIST:
                    raise
    
        # open file and write in it line by line in a csv format
        with open(file, "a+") as output:
            for line in csv_string:
                output.write(line)
    
    def export_csv_to_file_relative(self, file):
        """creates a csv file of the switch measurements

        :file: TODO
        :returns: TODO

        """
        csv_string = StringIO.StringIO(self.convert_to_csv_string_relative())

        # if the path to the file does not exist create it
        if not os.path.exists(os.path.dirname(file)):
            try:
                os.makedirs(os.path.dirname(file))
            except OSError as exc: # Guard against race condition
                if exc.errno != errno.EEXIST:
                    raise
    
        # open file and write in it line by line in a csv format
        with open(file, "a+") as output:
            for line in csv_string:
                output.write(line)
        

class Scenario(object):

    """Holds scenario processed data"""
    timeformat="%H:%M:%S.%f"


    def __init__(self, path):
        """Init constructor """
        self.path = path
        self.switches = {}
        self.measurement_type = ""
        self.succ_iteration_indexes = []
        self.unsucc_iteration_indexes = []

    def __str__(self):
        """define how to print the object of this type
        :returns: TODO

        """
        return self.path

    def __repr__(self):
        """ object representation
        :returns: TODO

        """
        return self.path

    def add_switch(self, switch):
        self.switches[switch.name] = switch

    def process_scenario(self):
        """process scenario folder provided via path attribute
        :returns: TODO

        """
        for item in sorted(os.listdir(self.path), key = lambda name: int(name.split("_")[1]) ):
            # process each iteration of the scenario
            print("Processing iteration: " + item)
            status = self.process_iteration(item)
            if status == True:
                self.succ_iteration_indexes.append(int(item.split("_")[1]) - 1)
            else:
                self.unsucc_iteration_indexes.append(int(item.split("_")[1]) - 1)

    def process_iteration(self, iteration):
        """process each iteration 

        :iteration: TODO
        :returns: true: if an iteration had fully successfull bootstrapping
                  false: otherwise

        """
        files = os.listdir(self.path + "/" + iteration)
        # status list for each file (switch)
        status_list = []
        for file in files:
            # only match sw_x files not sw_x_flow_table
            if file[-1].isdigit():
                print("Processing file: " + file)
                status = self.process_file(self.path + "/" + iteration + "/" + file)
                status_list.append(status)
        # Creating an iteration status
        if False not in status_list:
            return True
        else:
            return False

    def process_file(self, file):
        """process each switch file in the current iteration

        :file: TODO
        :returns: true: if a switch was fully bootstrapped
                  false: otherwise

        """
        if file.split("/")[-1] in self.switches:
            #print("Switch already exists")
            # check the measurement type
            measurement_type = self.check_measurement_type(file)
            # extract and add new values
            status = self.extract_and_add_new_values(file, measurement_type)
            return status

        else:
            #print("Switch does not exists")
            # check the measurement type
            measurement_type = self.check_measurement_type(file)

            # create a new object
            switch = object()
            if measurement_type == "STANDARD":
                switch = SwitchStandard(file.split("/")[-1])
            elif measurement_type == "ALTERNATIVE":
                switch = SwitchAlternative(file.split("/")[-1])
            else:
                print("Unsupported measurement type")
                return False

            # add the new object to the switches list
            self.add_switch(switch)
            #print(self.switches)
             
            # extract and add new values
            status = self.extract_and_add_new_values(file, measurement_type)
            return status

    def check_measurement_type(self, file):
        """extract and check the first line of a switch measurement file to determine which measurement type is being stored in a file

        :file: TODO
        :returns: TODO

        """
        with open(file) as f:
            first_line = f.readline()
        if "STANDARD" in first_line:
            if self.measurement_type == "":
                self.measurement_type = "STANDARD"
            return "STANDARD"
        elif "ALTERNATIVE" in first_line:
            if self.measurement_type == "":
                self.measurement_type = "ALTERNATIVE"
            return "ALTERNATIVE"
        else:
            if self.measurement_type == "":
                self.measurement_type = "OTHER"
            return "OTHER"
        
    def extract_and_add_new_values(self, file, measurement_type):
        """go through the actual file, line by line, extracts measurement values and stores them in the corresponding Switch object

        :file: TODO
        :returns: true: if there are no any "Missing" values in the file
                  false: if "Missing" value is present

        """
        print(file)
        # Initial assumption that file contains all information, i.e. no missing values
        status = True
        # time format of the extracted timestamps
        #print("extract_and_add_new_values->file: " + file)
        # TODO: use simpler coding of lines (this will be slow)
        if measurement_type == "STANDARD":
            switch_temp = SwitchStandard(file.split("/")[-1])
        elif measurement_type == "ALTERNATIVE":
            switch_temp = SwitchAlternative(file.split("/")[-1])
        else:
            print("Wrong type")
            return False

        resilience_cash = {}
        resilience_relative_cash = {}
        with open(file) as measurement:
            for line in measurement:
                if "IP assigned" in line:
                    timestamp = self.extract_after_arrow(line)
                    switch_temp.add_ip_assigned_timestamp(timestamp)
                elif "Initial OF installed" in line:
                    timestamp = self.extract_after_arrow(line)
                    switch_temp.add_initial_rules_installed_absolute_timestamps(timestamp)
                elif "Phase I installed" in line:
                    timestamp = self.extract_after_arrow(line)
                    switch_temp.add_initial_rules_phaseI_installed_absolute_timestamps(timestamp)
                elif "Phase II installed" in line:
                    timestamp = self.extract_after_arrow(line)
                    switch_temp.add_initial_rules_phaseII_installed_absolute_timestamps(timestamp)
                elif "Flow table" in line:
                    size = self.extract_after_arrow(line)
                    switch_temp.add_flow_table_size(size)
                elif "Resilience" in line:
                    timestamp = self.extract_after_arrow(line)
                    resilience_cash[self.extract_controller(line)] = timestamp
                    # get the IP assigned timestamp for the current iteration
                    if len(switch_temp.ip_assigned_timestamps) == 0:
                        resilience_relative_cash[self.extract_controller(line)] = "Missing"
                        status = False
                        print(status)
                    else:
                        ip_assigned_timestamp = switch_temp.ip_assigned_timestamps[0]
                        # find a difference to the IP assigned timestamp
                        timedelta = datetime.strptime(timestamp, self.timeformat) - datetime.strptime(ip_assigned_timestamp, self.timeformat)
                        resilience_relative_cash[self.extract_controller(line)] = self.timedelta_to_str(timedelta) 
                    #print("Resilience cash:")
                    #print(resilience_cash)
                    
            #print("----------------------------")
            # at the end of file put the resilience cash in the object
            switch_temp.add_resilience_installed_absolute_timestamp(resilience_cash)
            if "iteration_5" in file:
                print(resilience_cash)
                print(len(resilience_cash))
                print(len(switch_temp.resilience_installed_absolute_timestamps[0]))
            # at the end of file put the resiliencei relative cash in the object
            switch_temp.add_resilience_installed_relative_timestamp(resilience_relative_cash)
            #print("----------------------------")
           
            #print(self.switches[file])
            file=file.split("/")[-1]
            # check if some data is missing and add the data to the scenario list of switches
            if len(switch_temp.ip_assigned_timestamps) == 0:
                self.switches[file].add_ip_assigned_timestamp("Missing")
                status = False
                print(status)
            else:
                #print(self.switches[file].ip_assigned_timestamps)
                self.switches[file].add_ip_assigned_timestamp(switch_temp.ip_assigned_timestamps[0])
                #print(self.switches[file].ip_assigned_timestamps)

            if len(switch_temp.flow_table_sizes) == 0:
                self.switches[file].add_flow_table_size("Missing")
                status = False
                print(status)
            elif switch_temp.flow_table_sizes[0] == "0":
                # When one of the switches does not have any rules consider that bootstrapping procedure has failed
                self.switches[file].add_flow_table_size("Missing")
                status = False
                print(status)
            else:
                self.switches[file].add_flow_table_size(switch_temp.flow_table_sizes[0])
            
            if len(switch_temp.resilience_installed_absolute_timestamps[0]) == 0:
                self.switches[file].add_resilience_installed_absolute_timestamp("Missing")
                self.switches[file].add_resilience_installed_relative_timestamp("Missing")
                status = False
                print(status)
            else:
                self.switches[file].add_resilience_installed_absolute_timestamp(switch_temp.resilience_installed_absolute_timestamps[0])
                self.switches[file].add_resilience_installed_relative_timestamp(switch_temp.resilience_installed_relative_timestamps[0])

            if measurement_type == "STANDARD":
                if len(switch_temp.initial_rules_installed_absolute_timestamps) == 0:
                    self.switches[file].add_initial_rules_installed_absolute_timestamps("Missing")
                    self.switches[file].add_initial_rules_installed_relative_timestamps("Missing")
                    status = False
                    print(status)
                else:
                    timestamp = switch_temp.initial_rules_installed_absolute_timestamps[0]
                    self.switches[file].add_initial_rules_installed_absolute_timestamps(timestamp)
                    # get the IP assigned timestamp for the current iteration and compute time delta
                    if len(switch_temp.ip_assigned_timestamps) == 0:
                        self.switches[file].add_initial_rules_installed_relative_timestamps("Missing")
                        status = False
                        print(status)
                    else:
                        ip_assigned_timestamp=switch_temp.ip_assigned_timestamps[0]
                        # find a difference to the IP assigned timestamp
                        timedelta=datetime.strptime(timestamp, self.timeformat) - datetime.strptime(ip_assigned_timestamp, self.timeformat)
                        self.switches[file].add_initial_rules_installed_relative_timestamps(self.timedelta_to_str(timedelta))
            elif measurement_type == "ALTERNATIVE":
                if len(switch_temp.initial_rules_phaseI_installed_absolute_timestamps) == 0:
                    self.switches[file].add_initial_rules_phaseI_installed_absolute_timestamps("Missing")
                    self.switches[file].add_initial_rules_phaseI_installed_relative_timestamps("Missing")
                    status = False
                    print(status)
                else:
                    timestamp = switch_temp.initial_rules_phaseI_installed_absolute_timestamps[0]
                    self.switches[file].add_initial_rules_phaseI_installed_absolute_timestamps(timestamp)
                    # get the IP assigned timestamp for the current iteration and compute time delta
                    if len(switch_temp.ip_assigned_timestamps) == 0:
                        self.switches[file].add_initial_rules_phaseI_installed_relative_timestamps("Missing")
                        status = False
                        print(status)
                    else:
                        ip_assigned_timestamp=switch_temp.ip_assigned_timestamps[0]
                        # find a difference to the IP assigned timestamp
                        timedelta=datetime.strptime(timestamp, self.timeformat) - datetime.strptime(ip_assigned_timestamp, self.timeformat)
                        self.switches[file].add_initial_rules_phaseI_installed_relative_timestamps(self.timedelta_to_str(timedelta))
                if len(switch_temp.initial_rules_phaseII_installed_absolute_timestamps) == 0:
                    self.switches[file].add_initial_rules_phaseII_installed_absolute_timestamps("Missing")
                    self.switches[file].add_initial_rules_phaseII_installed_relative_timestamps("Missing")
                    status = False
                    print(status)
                else:
                    timestamp = switch_temp.initial_rules_phaseII_installed_absolute_timestamps[0]
                    self.switches[file].add_initial_rules_phaseII_installed_absolute_timestamps(timestamp)
                    # get the IP assigned timestamp for the current iteration and compute time delta
                    if len(switch_temp.ip_assigned_timestamps) == 0:
                        self.switches[file].add_initial_rules_phaseII_installed_relative_timestamps("Missing")
                        status = False
                        print(status)
                    else:
                        ip_assigned_timestamp=switch_temp.ip_assigned_timestamps[0]
                        # find a difference to the IP assigned timestamp
                        timedelta=datetime.strptime(timestamp, self.timeformat) - datetime.strptime(ip_assigned_timestamp, self.timeformat)
                        self.switches[file].add_initial_rules_phaseII_installed_relative_timestamps(self.timedelta_to_str(timedelta))
        return status

    def timedelta_to_str(self, timedelta):
        """convert timedelta object to the string format:
            H:M:S.mS

        :timedelta: TODO
        :returns: TODO

        """
        # time reference, just for calculation purposes
        timereference=datetime(2018, 7, 1, 0, 0, 0, 0)

        return (timereference + timedelta).strftime("%H:%M:%S.%f")[:-3]


    def extract_after_arrow(self, line):
        """filter out a timestamp from the line; no white spaces

        :line: TODO
        :returns: TODO
        """
        timestamp = line.split(">")[1].strip()
        return timestamp

    def extract_controller(self, line):
        """filter out a controller ip address from the resilience measurement line

       :line: TODO
       :returns: TODO

       """
        controller=re.findall( r'[0-9]+(?:\.[0-9]+){3}', line)[0]
        return controller

    def get_max_datetime_from_list(self, l):
        """returns max element of a datetime list

        :l: TODO
        :returns: TODO

        """
        if len(l) > 0:
            l_datetime = [datetime.strptime(timestamp, self.timeformat) for timestamp in l]
            max_timestamp = l_datetime[0]
            for timestamp in l_datetime:
                if timestamp == "Missing":
                    return "Missing"
                if timestamp > max_timestamp:
                    max_timestamp = timestamp
            return max_timestamp
        else:
            return "Missing"

    def get_iteration_summary(self, iteration):
        """returns a string containing iteration summary for the scenario:

            first switch that got IP address: time
            last switch that was provided with resilience: time
            bootstrapping time: time 

            If one of the switches contains missing data, that indicates
            that bootstrapping has not been successful, ignore those iterations

        :iteration: TODO
        :returns: TODO

        """
        if iteration in self.unsucc_iteration_indexes:
            return "Unsuccessful bootstrapping"
        # time reference, just for calculation purposes
        timereference=datetime(2018, 7, 1, 0, 0, 0, 0)
        #print("ITERATION DEBUG:" + str(iteration))
        list_ip_assigned_iteration = [(switch.name.split("/")[-1] , switch.ip_assigned_timestamps[iteration]) for switch in self.switches.values() if switch.ip_assigned_timestamps[iteration] != "Missing"]
        list_ip_assigned_iteration_processed = map(lambda x: (x[0], datetime.strptime(x[1], self.timeformat)), list_ip_assigned_iteration) 
        list_resilience_all_controllers_iteration = map(lambda x: (x.name.split("/")[-1], x.resilience_installed_absolute_timestamps[iteration].values()), self.switches.values())
        list_resilience_max_value_per_switch_iteration = []
        for item in list_resilience_all_controllers_iteration:
            max_timestamp_of_all_controllers = self.get_max_datetime_from_list(item[1]) 
            list_resilience_max_value_per_switch_iteration.append((item[0], max_timestamp_of_all_controllers))
        # find a switch that first got an IP
        try:
            bootstrap_start = min(list_ip_assigned_iteration_processed, key=lambda x: x[1])
        except Exception as e:
            return "Unsuccessful bootstrapping"
            
        # find a switch that was last provided with resilience
        try:
            bootstrap_end = max(list_resilience_max_value_per_switch_iteration, key=lambda x: x[1])
        except TypeError as e:
            return "Unsuccessful bootstrapping"
        try:
            bootstrapping_time = bootstrap_end[1] - bootstrap_start[1]
        except TypeError as e:
            return "Unsuccessful bootstrapping"

        summary = "Bootstrapping summary:\n" + \
                  "Start -> " + bootstrap_start[0] + ": " + bootstrap_start[1].strftime(self.timeformat) + "\n" + \
                  "End -> " + bootstrap_end[0] + ": " + bootstrap_end[1].strftime(self.timeformat) + "\n" + \
                  "Bootstrapping time: " + (timereference + bootstrapping_time).strftime(self.timeformat) + "\n"

        return summary

    def get_iteration_bootstrapping_time(self, iteration):
        """returns datetime object containing measured bootstrapping_time for the given scenario iteration

        :iteration: TODO
        :returns: TODO

        """
        if iteration in self.unsucc_iteration_indexes:
            return "Unsuccessful bootstrapping"
        # time reference, just for calculation purposes
        timereference=datetime(2018, 7, 1, 0, 0, 0, 0)
        list_ip_assigned_iteration = [(switch.name.split("/")[-1] , switch.ip_assigned_timestamps[iteration]) for switch in self.switches.values() if switch.ip_assigned_timestamps[iteration] != "Missing"]
        list_ip_assigned_iteration_processed = map(lambda x: (x[0], datetime.strptime(x[1], self.timeformat)), list_ip_assigned_iteration) 
        list_resilience_all_controllers_iteration = map(lambda x: (x.name.split("/")[-1], x.resilience_installed_absolute_timestamps[iteration].values()), self.switches.values())
        list_resilience_max_value_per_switch_iteration = []
        for item in list_resilience_all_controllers_iteration:
            max_timestamp_of_all_controllers = self.get_max_datetime_from_list(item[1]) 
            list_resilience_max_value_per_switch_iteration.append((item[0], max_timestamp_of_all_controllers))
        # find a switch that first got an IP
        try:
            bootstrap_start = min(list_ip_assigned_iteration_processed, key=lambda x: x[1])
        except Exception as e:
            return "Unsuccessful bootstrapping"
            
        # find a switch that was last provided with resilience
        try:
            bootstrap_end = max(list_resilience_max_value_per_switch_iteration, key=lambda x: x[1])
        except TypeError as e:
            return "Unsuccessful bootstrapping"
        try:
            bootstrapping_time = bootstrap_end[1] - bootstrap_start[1]
        except TypeError as e:
            return "Unsuccessful bootstrapping"

        return bootstrapping_time

    def create_output_iteration_summary(self, iteration, output_folder):
        """creates a file with an iteration summary and saves it in
           the given output_folder

        :output_folder: TODO
        :returns: TODO

        """
        file = output_folder + "/summaries_per_iteration/iteration" + str(iteration + 1) + "_summary" 
        summary = StringIO.StringIO(self.get_iteration_summary(iteration))

        #print(csv_string.getvalue())
        # if the path to the file does not exist create it
        if not os.path.exists(os.path.dirname(file)):
            try:
                os.makedirs(os.path.dirname(file))
            except OSError as exc: # Guard against race condition
                if exc.errno != errno.EEXIST:
                    raise
    
        # open file and write in it line by line in a csv format
        with open(file, "a+") as output:
            for line in summary:
                output.write(line)
    
    def create_output_scenario_summary(self, output_folder):
        """creates a file with a scenario summary and saves it in
           the given output_folder

        :output_folder: TODO
        :returns: TODO

        """
        file = output_folder + "/summary" 
        summary = self.get_scenario_summary()
        summary = StringIO.StringIO(summary)

        #print(csv_string.getvalue())
        # if the path to the file does not exist create it
        if not os.path.exists(os.path.dirname(file)):
            try:
                os.makedirs(os.path.dirname(file))
            except OSError as exc: # Guard against race condition
                if exc.errno != errno.EEXIST:
                    raise
    
        # open file and write in it line by line in a csv format
        with open(file, "a+") as output:
            for line in summary:
                output.write(line)

    def get_scenario_summary(self):
        """creates a summary for the whole scenario
           A summary contains the following info:

           ###################################
           SCENARIO: scenario name
           ###################################

           TOPOLOGY: used topology
           CON_NUM: used number of controllers
           CON_POSITION: controllers` placement
           SCHEME: used bootstrapping scheme

           RESULT:

           SUCCESS RATE: how many iterations were successful

           BEST BOOTSTRAPPING TIME: fastest bootstrapping time
           WORST BOOTSTRAPPING TIME: slowest bootstrapping time
           AVG BOOTSTRAPPING TIME: average bootstrapping time

           ###################################
           FLOW TABLE SIZE ANALYSIS SUMMARY
           ###################################

           Switch lowest avg highest
           switch name, smallest table size, average table size, biggest table size 


           REQUIREMENT: ASSUMES THAT PREVIOUSLY SUMMARIES FOR EACH ITERATION WERE CREATED
                        OTHERWISE AN UNDEFINED BEHAVIOR CAN BE EXPECTED


        :f: TODO
        :returns: TODO

        """
        summary = "###################################\n" + \
                "SCENARIO: " + self.path.split("/")[-1] + "\n" \
                "###################################\n\n" 
        # parse scenario config file and extract relevant info for the summary
        scenario_info = self.get_scenario_info()
        summary += "TOPOLOGY: " + scenario_info["TOPOLOGY"] + "\n"
        summary += "CON_NUM: " + scenario_info["CON_NUM"] + "\n"
        summary += "CON_POSITION: " + scenario_info["CON_POSITION"] + "\n"
        summary += "SCHEME: " + scenario_info["BOOTSTRAPPING"] + "\n\n"
        # parse each iteration summary file and extract and compute necessary data
        results = self.process_iteration_summaries(scenario_info["TARGET"])
        summary += "###################################\n"
        summary += "RESULT:\n"
        summary += "###################################\n\n"
        summary += "SUCCESS RATE: " + results["SUCCESS RATE"] + "\n\n"
        summary += "BEST BOOTSTRAPPING TIME: " + results["BEST BOOTSTRAPPING TIME"] + "\n"
        summary += "WORST BOOTSTRAPPING TIME: " + results["WORST BOOTSTRAPPING TIME"] + "\n"
        summary += "AVG BOOTSTRAPPING TIME: " + results["AVG BOOTSTRAPPING TIME"] + "\n\n"

        summary += "######################################################\n"
        summary += "FLOW TABLE SIZE ANALYSIS (all iterations) \n" 
        summary += "######################################################\n\n"
        summary += "Switch | lowest | highest | avg \n"
        for switch in self.switches.values():
            lowest_table_size = switch.get_min_table_size()
            highest_table_size = switch.get_max_table_size()
            avg_table_size = switch.get_avg_table_size()
            summary += switch.name.split("/")[-1] + " | " + str(lowest_table_size) + " | " + str(highest_table_size) + \
                    " | " + str(avg_table_size) + "\n"
        
        summary += "\n"
        summary += "######################################################\n"
        summary += "FLOW TABLE SIZE ANALYSIS (successful iterations) \n" 
        summary += "######################################################\n\n"
        summary += "Switch | lowest | highest | avg \n"
        for switch in self.switches.values():
            lowest_table_size = switch.get_min_table_size(self.succ_iteration_indexes)
            highest_table_size = switch.get_max_table_size(self.succ_iteration_indexes)
            avg_table_size = switch.get_avg_table_size(self.succ_iteration_indexes)
            summary += switch.name.split("/")[-1] + " | " + str(lowest_table_size) + " | " + str(highest_table_size) + \
                    " | " + str(avg_table_size) + "\n"

        return summary

    def get_scenario_info(self):
        """parse the config file located in ./scenarios/scenarioX/config
           extract values for topology, number of controllers, controllers' position,
           and used scheme

        :returns: TODO

        """
        config_file = "./scenarios/" + self.path.split("/")[-1] + "/config"

        # regex filters for each entry
        target_re = re.compile(r'^TARGET=')
        bootstrapping_scheme_re = re.compile(r'^BOOTSTRAPPING=')
        con_num_re = re.compile(r'^CON_NUM=')
        con_position_re = re.compile(r'^CON_POSITION=')
        topology_re = re.compile(r'^TOPOLOGY=')

        # return map
        res={}

        with open(config_file) as f:
            for line in f:
                if target_re.match(line) is not None:
                    res["TARGET"] = line.split("=")[1]
                elif bootstrapping_scheme_re.match(line) is not None:
                    res["BOOTSTRAPPING"] = line.split("=")[1]
                elif con_num_re.match(line) is not None:
                    res["CON_NUM"] = line.split("=")[1]
                elif con_position_re.match(line) is not None:
                    res["CON_POSITION"] = line.split("=")[1]
                elif topology_re.match(line) is not None:
                    res["TOPOLOGY"] = line.split("=")[1]

        return res


    def process_iteration_summaries(self, target):
        """process all iteration summaries and return the following results:

            BEST BOOTSTRAPPING TIME
            WORST BOOTSTRAPPING TIME
            AVG BOOTSTRAPPING TIME
            SUCCESS RATE 

        :f: TODO
        :returns: TODO

        """
        target = target.replace('"','')
        dir_to_process = "./measurement-records-processed-absolute/" + target.lower().strip() + "/scenarios/" + self.path.split("/")[-1] + "/summaries_per_iteration"
        print(dir_to_process)

        # match regex patterns
        unsuccessful_re = re.compile(r'^Unsuccessful bootstrapping')
        bootstrapping_time_re = re.compile(r'^Bootstrapping time')

        # initialization of counters
        unsuccessful_counter = 0
        successful_counter = 0

        # list that stores all bootstrapping times
        bootstrapping_times_list = []

        for directory, subdirectories, files in os.walk(dir_to_process):
            for file in files:
                with open(os.path.join(directory,file)) as f:
                    for line in f:
                        print(line)
                        if unsuccessful_re.match(line) is not None:
                            unsuccessful_counter += 1
                            break
                        elif bootstrapping_time_re.match(line) is not None:
                            bootstrapping_times_list.append(line.split(":",1)[1].strip())
                            successful_counter += 1


        if successful_counter == 0:
            success_rate = str(successful_counter) + "/" + str(unsuccessful_counter + successful_counter)
            res = {"BEST BOOTSTRAPPING TIME": "NONE", "WORST BOOTSTRAPPING TIME": "NONE", \
                "AVG BOOTSTRAPPING TIME": "NONE", "SUCCESS RATE": success_rate }
            return res
        else:
            # convert bootstrapping times to datetime objects
            bootstrapping_times_list_datetime = [datetime.strptime(timestamp, self.timeformat) for timestamp in bootstrapping_times_list]

            min_bootstrapping_time = min(bootstrapping_times_list_datetime)
            max_bootstrapping_time = max(bootstrapping_times_list_datetime)
            avg_bootstrapping_time = self.avg_time(bootstrapping_times_list_datetime)
            success_rate = str(successful_counter) + "/" + str(unsuccessful_counter + successful_counter)

            res = {"BEST BOOTSTRAPPING TIME": min_bootstrapping_time.strftime(self.timeformat), "WORST BOOTSTRAPPING TIME": max_bootstrapping_time.strftime(self.timeformat), \
                    "AVG BOOTSTRAPPING TIME": avg_bootstrapping_time.strftime(self.timeformat), "SUCCESS RATE": success_rate}
            return res
                        
    def avg_time(self, datetimes):
        total = sum(dt.hour * 3600 * 1000 + dt.minute * 60 * 1000 + dt.second*1000 + dt.microsecond/1000 for dt in datetimes)
        avg = total / len(datetimes)
        hours, rest = divmod(int(avg), 3600 * 1000)
        minutes, rest = divmod(rest, 60 * 1000)
        seconds, rest = divmod(rest, 1000)
        microseconds = rest * 1000
        return datetime.combine(date(1900, 1, 1), time(hours, minutes, seconds, microseconds))


    def create_output_absolute(self, output_folder):
        """creates processed output files for a scenario object

        :output_folder: TODO
        :returns: TODO

        """
        for switch in self.switches:
            print("Creating a csv file for " + switch)
            print("Output folder: " + output_folder)
            self.switches[switch].export_csv_to_file_absolute(output_folder + "/" + self.switches[switch].name.split("/")[-1])
        
        # get the number of iterations for the scenario
        print("Number of switches in the scenario " + self.path + ": " + str(len(self.switches.values())))
        try:
            scenario_iter_num = self.switches.values()[0].get_iter_num()
        except IndexError as e:
            print("For the scenario " + self.path + " the selected topology was not available in the topology emulator folder")
            return
        # create a summary report for each scenario
        for i in range(scenario_iter_num):
            self.create_output_iteration_summary(i, output_folder)
        self.create_output_scenario_summary(output_folder)
    
    def create_output_relative(self, output_folder):
        """creates processed output files for a scenario object

        :output_folder: TODO
        :returns: TODO

        """
        for switch in self.switches:
            print("Creating a csv file for " + switch)
            print("Output folder: " + output_folder)
            self.switches[switch].export_csv_to_file_relative(output_folder + "/" + self.switches[switch].name.split("/")[-1])

        # get the number of iterations for the scenario
        print("Number of switches in the scenario " + self.path + ": " + str(len(self.switches.values())))
        try:
            scenario_iter_num = self.switches.values()[0].get_iter_num()
        except IndexError as e:
            print("For the scenario " + self.path + " the selected topology way not available in the topology emulator folder")
            return
        # create a summary report for each scenario
        for i in range(scenario_iter_num):
            self.create_output_iteration_summary(i, output_folder)
        self.create_output_scenario_summary(output_folder)
           

def convert_to_millis(time):
    """ Converts time given as a string in the format "hh:mm:ss.millis" to the integer millisecond value 

    :time: string "hh:mm:ss.millis" 
    :returns: integer milliseconds

    """
    if isinstance(time, str):
        time_split = time.split(":")
        hours = int(time_split[0])
        minutes = int(time_split[1])
        seconds = int(time_split[2].split(".")[0])
        milliseconds = int(time_split[2].split(".")[1])

        return hours*3600*1000 + minutes*60*1000 + seconds*1000 + milliseconds
    
    elif isinstance(time, datetime):            
        hours = time.hour
        minutes = time.minute
        seconds = time.second
        milliseconds = time.microsecond/1000

        return hours*3600*1000 + minutes*60*1000 + seconds*1000 + milliseconds

    else:
        return None

def avg_time(datetimes):
    total = sum(dt.hour * 3600 * 1000 + dt.minute * 60 * 1000 + dt.second*1000 + dt.microsecond/1000 for dt in datetimes)
    avg = total / len(datetimes)
    hours, rest = divmod(int(avg), 3600 * 1000)
    minutes, rest = divmod(rest, 60 * 1000)
    seconds, rest = divmod(rest, 1000)
    microseconds = rest * 1000
    return datetime.combine(date(1900, 1, 1), time(hours, minutes, seconds, microseconds))
        


