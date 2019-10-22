# Switch connected devices via LLDP 

## Summary

This feature allows to detect devices connected to particular switch
by catching LLDP packets. Design of the feature is based on multi-table feature design.

## API

New boolean field `detect_connected_devices_lldp` will be added to switch properties.
After setting it to `true` by API `PUT /{switch-id}/properties` several rules will be installed on the switch.
Description of these rules you can find in section `Detecting of connected devices via LLDP `.

New API will be created to get a list of devices connected to Switch: `GET /{switch_id}/devices?since={time}`

`flow_id` - Switch ID
`since` - Optional param. If specified only devices which were seen since this time will be returned.

This API returns following body:

~~~
{
  "ports": [
      {
         "port_number": int,
         "lldp": [{
            "vlan": int,
            "macAddress": string,
            "chassisId": string,
            "portId": string,
            "ttl": int,
            "portDescription": string,
            "systemName": string,
            "systemDescription": string,
            "systemCapabilities": string,
            "managementAddress": string,
            "timeFirstSeen": string,
            "timeLastSeen": string
           },
           ***
         ],
      },
      ***
   ]
}

~~~

The following fields are optional:

* portDescription
* systemName
* systemDescription
* systemCapabilities
* managementAddress

## Detecting of connected devices via LLDP 

To detect connected devices via LLDP we will catch LLDP packets from each switch port
and send them to controller to analyze it in Connected Devices Storm Topology.

There are two types of switch ports: customer and ISL. Rules will be different for each port type.

Full description of new rules you can find in
[this](https://drive.google.com/file/d/1yM64eNeHL1u5RDuToqUTUczrx-7naaK4/view?usp=sharing) doc.

Short description:

![Catching of LLDP customer](lldp_catching_customer.png "Catching of LLDP customer")

## Switch rules

New action `go to table 1` will be added to ingress rule of Flow if detection of connected devices is enabled.
This action will send a copy of each customer's packet to switch table 1.
In this table only LLDP packets will be matched and sent to controller.
LLDP match rule will be look like:

```
[FLOW_ID0]
    ofp_version      = 4
    ControllerGroup  = Management
    [MATCHFIELDS]
        OFPXMT_OFB_ETH_DST = 01:80:C2:00:00:0E (LLDP packet mac address)
        OFPXMT_OFB_ETH_TYPE = 0x88CC (LLDP eth type) 
        OFPXMT_OFB_IN_PORT = XXX (import port) 
        OFPXMT_OFB_VLAD_ID = XXX (vlan id if has) 
    [INSTRUCTIONS]
        [OFPIT_APPLY_ACTIONS]
             [ACTIONS]
                [OFPAT_POP_VLAN] (if has vlan)
                [OFPAT_OUTPUT] (sent to controller)
                    port = ctrl
                    mlen = 65535

```
