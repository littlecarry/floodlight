package net.floodlightcontroller.QoSEvaluation;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortStatsRequest;

import java.util.Collection;


public class PacketLossMeter {
    public PacketLossMeter(){

    }
    public void doPacketLossMeter(IOFSwitch aSwitch){
        Collection<OFPortDesc> ports = aSwitch.getEnabledPorts();
        for(OFPortDesc port : ports){
            OFPortStatsRequest.Builder portStatsRequest = aSwitch.getOFFactory().buildPortStatsRequest();
            portStatsRequest.setPortNo(port.getPortNo());
            aSwitch.write(portStatsRequest.build());
        }
    }

}