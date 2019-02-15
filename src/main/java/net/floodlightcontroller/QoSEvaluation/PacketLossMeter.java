package net.floodlightcontroller.QoSEvaluation;

import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortStatsRequest;

import java.util.Collection;


public class PacketLossMeter {
    public PacketLossMeter(){

    }
    //为什么丢包计算（针对端口计算丢包发端口状态请求
    public void doPacketLossMeter(IOFSwitch aSwitch){
        Collection<OFPortDesc> ports = aSwitch.getEnabledPorts();
        for(OFPortDesc port : ports){
            OFPortStatsRequest.Builder portStatsRequest = aSwitch.getOFFactory().buildPortStatsRequest();
            portStatsRequest.setPortNo(port.getPortNo());
            //MyLog.info("丢包测量发起请求-发送portStatsRequest消息");
            aSwitch.write(portStatsRequest.build());
        }
    }

}