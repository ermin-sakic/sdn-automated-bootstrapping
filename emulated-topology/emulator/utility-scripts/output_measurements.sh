#!/bin/bash

echo "Cleaning old measurements!"
rm -r measurements
echo "Creating new measurements folder!"
mkdir measurements
for i in $(seq 1 $1); 
do
	sudo docker exec -u root sw_$i /measure-flow-table-size.sh
	sudo docker cp sw_$i:/measurement_record ./measurements/sw_$i
	echo "Extracting for sw_$i done"
done

echo "Creating the summary"

touch ./measurements/summary

for i in $(seq 1 $1);
do
	echo "++++++++++++++++++++++++++" >> ./measurements/summary
	echo "SW_$i results:" >> ./measurements/summary
	cat ./measurements/sw_$i >> ./measurements/summary
	echo "++++++++++++++++++++++++++" >> ./measurements/summary
done


touch ./measurements/ip_assigned_sorted
for i in $(seq 1 $1);
do
	echo "++++++++++++++++++++++++++" >> ./measurements/summary
	echo "SW_$i results:" >> ./measurements/summary
	cat ./measurements/sw_$i >> ./measurements/summary
	echo "++++++++++++++++++++++++++" >> ./measurements/summary
done


