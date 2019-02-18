package net.floodlightcontroller.sampling;
import net.floodlightcontroller.QoSEvaluation.NetworkStore;
import org.projectfloodlight.openflow.protocol.OFPacketIn;

import java.util.*;

public class PacketInSampling {
    //static List<Map<String, Number>> pLists = new ArrayList<>(); //结构：【{"p":0.01,"time":1234567890123},{"p":0.02,"time":9876543210321}】--最大长度为3
    static List<Double> pLists = new ArrayList<>();
    static int N = 6/*参考周期數*/, T = 34/*周期长度，单位为ms*/;
    static long lastTime;
    static double b=1.0, a=1.0;//系数
    static double initP = 0.01;
    private List<Map<Long, Map<String, Number>>> allFlowAllTimeOfSwitch;
    /*static int packetCounter = 1;
    public static HashMap<ItFlow, Integer> flowIntegerHashMap = new HashMap<>();//全局
    protected static List<Map<Integer, Object>> flowCouter = new ArrayList<>();
    protected static double packetSamplingPropobility;*/


    public void sampling() {
        adaptiveAdjustmentForP();//得到该周期包采样概率p
        NetworkStore ns = NetworkStore.getInstance();
        allFlowAllTimeOfSwitch = ns.getAllFlowAllTimeOfSwitch();



    }

    /*public void packetInSampling(OFPacketIn packetInMsg) {
        //判断新流旧流
        // if(packetInMsg.getTableId())//找到存储流表的表，看流表中是否有该流ID
        packetInMsg.getTableId();
        packetInMsg.getData();//Data消息里包含了

        //判断新流旧流
        // if(packetInMsg.getTableId())//找到存储流表的表，看流表中是否有该流ID
        packetInMsg.getTableId();
        packetInMsg.getData();//Data消息里包含了
        ItFlow flow1 = null;
        //节点相关
        if (flowIntegerHashMap.containsKey(flow1))//旧流
        {
            int flowCounter = flowIntegerHashMap.get(flow1) + 1;
            flowIntegerHashMap.put(flow1, flowCounter);//流计数count++;  --HashSet
            //采样
            *//*假设阈值为Threshold*//*
            int threshold = 0;
            if (flowCounter > threshold) {

                //TODO 流内采样流内采样

                flowCounter = 0;   //重置
            }

        } else {
            flowIntegerHashMap.put(flow1, 1);
            packetCounter++;
            *//*假设阈值为Threshold*//*
            int threshold = 0;
            if (packetCounter > threshold) {

                //TODO 新流采样（包级别采样）

                packetCounter = 0;

            }

        }


    }*/




    void adaptiveAdjustmentForP() { //pLists的最后一个的元素的p为当前的动态概率取值,pLists最大长度为N（需要记录的参考周期数）。
        long time = new Date().getTime();
        if(pLists.isEmpty()) {
            lastTime =time;
            pLists.add(initP);
        } else {
            if(time- lastTime>T) { //更新动态概率p
                if(pLists.size()==1) {
                    lastTime =time;
                    pLists.add(initP);
                    return;
                }
                double pRate=0;
                int half =pLists.size()/2;
                for(int i= 0; i<half;i++) {
                    pRate+=pLists.get(half+i)-pLists.get(i);
                }
                pRate*=4.0/(N*N); //变化率
                double lastP = pLists.get(pLists.size()-1);
                double finalP = lastP + (b*pRate)/(a*lastP*lastP+1);
                pLists.add(finalP);
                if(pLists.size()>N)
                    pLists.remove(0);
            } else { //在周期内，不需要更新p
                return;
            }
        }

    }
}



class ItFlow{
    String src,dst;
    int flowCounter=0;

}
