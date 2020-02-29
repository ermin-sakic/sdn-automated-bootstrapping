#!/bin/sh

find /sys/class/net/ -maxdepth 1 -name 'veth*' |
    while read device; do
        basename "$device"
    done |
    while read interface; do
        printf "%s\n" "$interface"
        ethtool $interface | grep -q 'Link detected: yes' || {
            echo "  down"
            echo
            continue
        }
        lldptool get-tlv -n -i "$interface" | sed -e "s/^/  /"
        echo
    done
