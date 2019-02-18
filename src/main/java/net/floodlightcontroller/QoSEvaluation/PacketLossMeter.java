package net.floodlightcontroller.QoSEvaluation;

import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.OFSwitch;
import net.floodlightcontroller.linkdiscovery.Link;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortStatsRequest;
import org.projectfloodlight.openflow.types.OFPort;

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


    //另一种方法可以直接尝试link.getLatency
    /*该方法输入为switchService与相应的链路link，该方法也可行
    public void doPacketLossMeterForLink(IOFSwitchService switchService, Link link){
        IOFSwitch fromSw = switchService.getSwitch(link.getSrc());
        IOFSwitch toSw = switchService.getSwitch(link.getDst());
        OFPort inPort = link.getSrcPort();
        OFPort outPort = link.getDstPort();

        //每个链路发送测量请求（发向链路的源路由器）
        OFPortStatsRequest.Builder portStatsRequest = fromSw.getOFFactory().buildPortStatsRequest();
        portStatsRequest.setPortNo(inPort);
        //MyLog.info("丢包测量发起请求-发送portStatsRequest消息");
        fromSw.write(portStatsRequest.build());
    }*/


    /**
     * 每个链路发送测量请求（发向链路的源路由器）
     * @param fromSwitch
     * @param inPort
     */
    public void doPacketLossMeterForLink(IOFSwitch fromSwitch, OFPort inPort){

        //每个链路发送测量请求（发向链路的源路由器）
        OFPortStatsRequest.Builder portStatsRequest = fromSwitch.getOFFactory().buildPortStatsRequest();
        portStatsRequest.setPortNo(inPort);
        //MyLog.info("丢包测量发起请求-发送portStatsRequest消息");
        fromSwitch.write(portStatsRequest.build());
    }

}