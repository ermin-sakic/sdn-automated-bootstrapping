#!/bin/bash

h1_id=`sudo docker ps | grep ho_1 | cut -d ' ' --f 1`
h2_id=`sudo docker ps | grep ho_2 | cut -d ' ' --f 1`

sudo docker exec -u root -it $h1_id ping -c 1 10.0.0.35
sudo docker exec -u root -it $h2_id ping -c 1 10.0.0.33
