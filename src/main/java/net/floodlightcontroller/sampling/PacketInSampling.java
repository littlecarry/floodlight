package net.floodlightcontroller.sampling;
import org.projectfloodlight.openflow.protocol.OFPacketIn;

import java.util.*;

public class PacketInSampling {
    static int packetCounter = 1;
    public static HashMap<ItFlow,Integer> flowIntegerHashMap =new HashMap<>();//全局
    protected static List<Map<Integer, Object>> flowCouter = new ArrayList<>();
    protected static double packetSamplingPropobility;
    public static void packetInSampling (OFPacketIn packetInMsg){
        //判断新流旧流
       // if(packetInMsg.getTableId())//找到存储流表的表，看流表中是否有该流ID
        packetInMsg.getTableId();
        packetInMsg.getData();//Data消息里包含了

        //判断新流旧流
       // if(packetInMsg.getTableId())//找到存储流表的表，看流表中是否有该流ID
        packetInMsg.getTableId();
        packetInMsg.getData();//Data消息里包含了
        ItFlow flow1=null;
        //节点相关
        if(flowIntegerHashMap.containsKey(flow1))//旧流
        {
            int flowCounter = flowIntegerHashMap.get(flow1)+1;
            flowIntegerHashMap.put(flow1, flowCounter);//流计数count++;  --HashSet
            //采样
            /*假设阈值为Threshold*/
            int threshold=0;
            if(flowCounter>threshold){

                //TODO 流内采样流内采样

                flowCounter=0;   //重置
            }

        }else {
            flowIntegerHashMap.put(flow1,1);
            packetCounter++;
            /*假设阈值为Threshold*/
            int threshold=0;
            if(packetCounter>threshold){

                //TODO 新流采样（包级别采样）

                packetCounter=0;

            }

        }





    }


}



class ItFlow{
    String src,dst;
    int flowCounter=0;

}
