#!/bin/bash

docker stop $(docker ps -a -q)
docker rm $(docker ps -a -q)

# Clean up all network namespaces except for the default one
echo "Network namespaces cleaned!"
ip netns | xargs -I {} sudo ip netns delete {}

# NOTE:
# If you want to remove Controller' netns then filter out only netns names
# without the part "(id: somenum)"
