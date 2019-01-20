package net.floodlightcontroller.sampling;
import org.projectfloodlight.openflow.protocol.OFPacketIn;

import java.util.*;

public class PacketInSampling {
    static int packetCounter = 1;
    protected static List<Map<Integer, Object>> flowCouter = new ArrayList<>();
    protected static double packetSamplingPropobility;
    public static void packetInSampling (OFPacketIn packetInMsg){
        //判断新流旧流
       // if(packetInMsg.getTableId())//找到存储流表的表，看流表中是否有该流ID


    }


}
