#!/bin/bash
sudo ip link add veth19 type veth peer name veth20
sudo ifconfig veth20 10.10.0.23

