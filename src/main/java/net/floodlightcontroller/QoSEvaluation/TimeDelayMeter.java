package net.floodlightcontroller.QoSEvaluation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.QoSEvaluation.NetworkMeter;
import org.projectfloodlight.openflow.protocol.OFPacketOut.Builder;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.linkdiscovery.internal.LinkInfo;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

public class TimeDelayMeter {
    //des和sourceMac设定为真实网络环境下不会出现的MAC，避免冲突，取值不是固定的
    public static byte[] destinationMACAddress={0x00,0x00,0x00,0x00,0x00,0x00};
    public static byte[] sourceMACAddress={0x00,0x00,0x00,0x00,0x00,0x01};
    public TimeDelayMeter(){

    }

    public boolean isDoingTimeDelay(Ethernet eth){
        byte[] descmac = eth.getDestinationMACAddress().getBytes();
        byte[] srcmac = eth.getSourceMACAddress().getBytes();
        if(descmac.length!=6||srcmac.length!=6)
            return false;
        for(int i=0;i<6;i++){
            if(descmac[i] !=destinationMACAddress[i] ||
                    srcmac[i] !=sourceMACAddress[i])
                return false;
        }
        return true;
    }

    public void doTimeDelayMeter(NetworkMeter networkMeter){
        ILinkDiscoveryService linkService = networkMeter.getLinkService();
        //得到所有链路
        Map<Link, LinkInfo> links = linkService.getLinks(); //该links是有向的，同一的两个方向记为两条链路
        for(Link l :links.keySet()){
            //得到每条链路两端的地址和端口号
            IOFSwitch fromSw = networkMeter.getSwitchService().getSwitch(l.getSrc());
            IOFSwitch toSw = networkMeter.getSwitchService().getSwitch(l.getDst());
            OFPort inPort = l.getSrcPort();
            OFPort outPort = l.getDstPort();
            //时延 = （控制器到源交换机时间+控制器到目的交换机时间+交换机间时延）-控制器到源交换机时间-控制器到目的交换机时间
            //packetOut消息到echo的时间间隔为（控制器到源交换机时间+控制器到目的交换机时间+交换机间时延）。
            sendPacketOut(fromSw,inPort,toSw,outPort);
            sendEchoRequest(fromSw);
            sendEchoRequest(toSw);
        }
    }

    public void  sendPacketOut(IOFSwitch fromSw, OFPort inPort, IOFSwitch toSw, OFPort outPort){
        Builder packetOutBuilder = fromSw.getOFFactory().buildPacketOut();

        List<OFAction> actions  = new ArrayList<OFAction>();
        actions.add(fromSw.getOFFactory().actions().output(inPort, Integer.MAX_VALUE));//packetout消息由控制器发给源交换机，
                                                                        // Controller-fromSwitch-toSwitch-Controller闭环
        packetOutBuilder.setActions(actions);
        //Ethernet--以太帧头部组成：目的地址+源地址+负载类型（payload）
        Ethernet eth = new Ethernet();
        eth.setDestinationMACAddress(destinationMACAddress);
        eth.setSourceMACAddress(sourceMACAddress);
        eth.setEtherType(EthType.IPv4);
        //IP
        IPv4 ip = new IPv4();
        ip.setSourceAddress(0);
        ip.setDestinationAddress(0);
        ip.setProtocol(IpProtocol.NONE);
        //timestamp
        StringBuilder sb = new StringBuilder(getCurrentTime());
        sb.append("<>").append(fromSw.getId())
                .append("<>").append(inPort.getPortNumber())
                .append("<>").append(toSw.getId())
                .append("<>").append(outPort.getPortNumber());
        String mess = new String(sb);
        Data data = new Data();
        data.setData(mess.getBytes());
        //填充IP和eth的payload
        ip.setPayload(data);
        eth.setPayload(ip);
        //eth序列化并写入到packetout消息，并发送
        packetOutBuilder.setData(eth.serialize());
        fromSw.write(packetOutBuilder.build());

    }

    public void sendEchoRequest(IOFSwitch aSwitch){
        org.projectfloodlight.openflow.protocol.OFEchoRequest.Builder request
                = aSwitch.getOFFactory().buildEchoRequest();
        //只需要填充时间戳，用于计算
        StringBuilder sb = new StringBuilder(getCurrentTime());
        sb.append("<>").append(aSwitch.getId());
        String mess = new String(sb);
        Data data = new Data();
        data.setData(mess.getBytes());

        request.setData(data.serialize());
        aSwitch.write(request.build());
    }

    public String getCurrentTime(){
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
        return df.format(new Date());
    }
}