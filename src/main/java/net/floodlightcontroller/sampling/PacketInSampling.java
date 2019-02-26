package net.floodlightcontroller.sampling;
//import com.kenai.jaffl.annotations.Synchronized;
import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.QoSEvaluation.NetworkStore;
import java.lang.*;
import org.projectfloodlight.openflow.protocol.OFPacketIn;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketInSampling {
    //static List<Map<String, Number>> pLists = new ArrayList<>(); //结构：【{"p":0.01,"time":1234567890123},{"p":0.02,"time":9876543210321}】--最大长度为3
    static  ConcurrentHashMap<Long, List<Double>> pListsForSwitches = new ConcurrentHashMap<>();
    //static List<Double> pLists = new ArrayList<>();
    static final int N = 6/*参考周期數*/, T = 34/*周期长度，单位为ms*/;
    static long lastTime;
    static final double B=1.0, A=1.0;//系数
    static final double initP = 0.01;
    static int packetCounter = 0;
    public static ConcurrentLinkedQueue<Map<String, Object>> sampledPackets = new ConcurrentLinkedQueue<>();
    private ConcurrentHashMap<Long, Deque<Map<String, Map<String, Object>>>> allFlowAllTimeOfAllSwitch;

    //inFlowSampling params
    static final double INCREASE_FACTOR = 1.2;
    /*static int packetCounter = 1;
    public static HashMap<ItFlow, Integer> flowIntegerHashMap = new HashMap<>();//全局
    protected static List<Map<Integer, Object>> flowCouter = new ArrayList<>();
    protected static double packetSamplingPropobility;*/

    //public static void f(){}

    public void sampling(long switchId) { //该函数存在并发--
        NetworkStore ns = NetworkStore.getInstance();
        allFlowAllTimeOfAllSwitch = ns.getAllFlowAllTimeOfAllSwitch();
        Deque<Map<String, Map<String, Object>>> allFlowAllTimeOfSwitch = allFlowAllTimeOfAllSwitch.get(switchId);
        synchronized(allFlowAllTimeOfAllSwitch) {
        adaptiveAdjustmentForP(switchId);//得到该周期包采样概率p
        int lengthOfTimes = allFlowAllTimeOfSwitch.size();
        Map<String, Map<String, Object>> curFlows = allFlowAllTimeOfSwitch.peekLast();
        Map<String, Map<String, Object>> lastFlows = null;
        if(allFlowAllTimeOfSwitch.size()>=2) {
            allFlowAllTimeOfSwitch.removeLast();  //若不与前面的方法同步，会被插队，导致队尾加入新元素
            lastFlows = allFlowAllTimeOfSwitch.peekLast();
            allFlowAllTimeOfSwitch.push(curFlows);
        } else {
            lastFlows = new HashMap<>();
        }

        HashMap<String, Map<String, Object>>  newFlows = new HashMap<>(); //新流，首次进入的流
        HashMap<String, Map<String, Object>>  disFlows = new HashMap<>(); //旧流，取流差值

        if(lastFlows == null || lastFlows.isEmpty()) {
            newFlows.putAll(curFlows);
            MyLog.info("PacketSampling:curFlows填充新流");
        } else { //必有非null的lastFlows
            for(String srcAndDst : curFlows.keySet()) {
                HashMap<String, Object> flow = new HashMap<>();
                flow.putAll(lastFlows.get(srcAndDst)); //HashMap的声明，深拷贝
                if(!lastFlows.containsKey(srcAndDst)) {
                    newFlows.put(srcAndDst, flow);
                } else {
                    if(flow.containsKey("isSampled") && (int)flow.get("isSampled")==1) {
                        int lastCount =(int) lastFlows.get(srcAndDst).get("count");
                        int curCount =(int) curFlows.get(srcAndDst).get("count");
                        if(curCount>lastCount) {
                            int count = curCount-lastCount;
                            flow.put("count", count);
                            disFlows.put(srcAndDst, flow);
                        } else {
                            MyLog.error("PacketSampling:該流历史包数目大于流的当前包数目，该流为"+srcAndDst);
                        }
                    } else {
                        newFlows.put(srcAndDst, flow);
                    }

                }

            }
            MyLog.info("PacketSampling:非null的lastFlows，填充新流和旧流");
        }

        newFlowSampling(newFlows, curFlows, switchId);
        flowCompression(disFlows, curFlows);//curFlows用于接收字段修改（并全局化）
            MyLog.info("sampledPackets.size="+sampledPackets.size());
         } //end synchronized

    } //end function sampling

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




    void adaptiveAdjustmentForP(long switchId) { //pLists的最后一个的元素的p为当前的动态概率取值,pLists最大长度为N（需要记录的参考周期数）。
        long time = new Date().getTime();
        List<Double> pLists;
        if(!pListsForSwitches.containsKey(switchId) || pListsForSwitches.get(switchId)==null) {
            //MyLog.error("PacketInSampling-adaptiveAdjustmentForP error: switchId not found");
            lastTime = time;
            pLists = new ArrayList<>();
            pLists.add(initP);
            return;
        }
        pLists = pListsForSwitches.get(switchId);

        if(pLists.isEmpty()) {
            lastTime = time;
            pLists.add(initP);
        } else {
            if(time - lastTime>T) { //更新动态概率p
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
                double finalP = lastP + (B*pRate)/(A*lastP*lastP+1);
                pLists.add(finalP);
                if(pLists.size()>N)
                    pLists.remove(0);
            } else { //在周期内，不需要更新p
                return;
            }
        }

    }

    /**
     *
     * disFlows理论上需要先经过newFlowSampling后才能到达flowCompression
     * @param disFlows
     * @param originalFlows
     */
    void flowCompression(Map<String, Map<String, Object>> disFlows, Map<String, Map<String, Object>> originalFlows) {
                                                                    //流内压缩/采样 disFlows中的流信息全部存在于历史的流内，
                                                                    // disFlows代表（当前与历史流中都存在的流）之间的差值
        if(disFlows==null||disFlows.size()==0) {
            return;
        }
        for(String srcAndDst : disFlows.keySet()) {
            Map<String, Object> flow =disFlows.get(srcAndDst);

            int inFlowCount, lastN, N, isSilent, count;
            if(flow.containsKey("inFlowCount")
                    &&flow.containsKey("lastN")
                    &&flow.containsKey("N")
                    &&flow.containsKey("isSilent")
                    &&flow.containsKey("count")
                    &&flow.get("inFlowCount")!=null
                    &&flow.get("lastN")!=null
                    &&flow.get("N")!=null
                    &&flow.get("isSilent")!=null
                    &&flow.get("count")!=null
            ) {
                inFlowCount = (int) flow.get("inFlowCount");
                lastN = (int) flow.get("lastN");
                N = (int) flow.get("N");
                isSilent = (int) flow.get("isSilent");
                count = (int) flow.get("count");
            } else {
                MyLog.error("flowCompression - PacketInSampling error: disFlows 未经过 newFlowSampling（新流采样）函数");
                continue;
            }


            int loopTurn = 0;
            while(inFlowCount +count > N) {
                if(isSilent==0) {
                    HashMap<String, Object> packet = new HashMap<>();
                    packet.putAll(flow);
                    packet.remove("count");
                    sampledPackets.add(packet);             //TODO: 该步为包信息采样，需好好扩充
                }

                count-= N-inFlowCount;
                inFlowCount = 0;
                lastN = N;
                N = (int) (Math.ceil(INCREASE_FACTOR*N));
                isSilent =0; //不再silent
                if(++loopTurn>100) {
                    MyLog.warn("flowCompression:循环次数超过100.");
                }

            }

            inFlowCount+= count;

            Map<String, Object> thisFlow = originalFlows.get(srcAndDst);
            thisFlow.put("inFlowCount", inFlowCount);//流内采样计数
            thisFlow.put("lastN", lastN);
            thisFlow.put("N", N);
            thisFlow.put("isSilent", 0); //0代表非Silent，1代表Silent。 Silent：Silent=1，表示该N个包中，已有包被采样，后序包不再被采样。
            thisFlow.put("isSampled", 1); //1代表已被采样，其他（包括为0，空或无该字段）表示未被采样。

            //随机采样函数
            double num = Math.random();

            if(num<1.0*inFlowCount/N){//采样
                thisFlow.put("isSilent", 1);
            } else { //不采样
                thisFlow.put("isSilent", 0);
            }

        }


    }

    void newFlowSampling(Map<String, Map<String, Object>> newFlows, Map<String, Map<String, Object>> originalFlows, long switchId) { //新采样模式每个流最多只能采集一个包，并最多被计数一次 ---经过该函数的包最终未都被采样

        if(newFlows==null||newFlows.size()==0) {
            return;
        }

        if(!pListsForSwitches.containsKey(switchId) || pListsForSwitches.get(switchId)==null) {
            MyLog.error("PacketInSampling-newFlowSampling error: switchId not found");
            return;
        }
        List<Double> pLists = pListsForSwitches.get(switchId);

        int threshold = (int) (1/pLists.get(pLists.size()-1));
        for(String srcAndDst : newFlows.keySet()) {
            Map<String, Object> flow = newFlows.get(srcAndDst);
            int numberOfPackets = (int) flow.get("count");
            if(numberOfPackets-packetCounter>=threshold) {
                HashMap<String, Object> packet = new HashMap<>(); //此处必须声明为HashMap，否则无法使用HashMap的putAll深复制（Map的putAll为浅复制）
                packet.putAll(flow); //HashMap的putAll深复制，将packets与flow对象分离，不修改flow
                //修改字节数
                packet.remove("count"); //单个packet包与流不同，没有包数目的字段
                //packets.remove("time");//时间信息是否移除
                sampledPackets.add(packet);             //TODO: 该步为包信息采样，需好好扩充---存储（将samplePackets存储到VO中）
                packetCounter=0;
                Map<String, Object> thisFlow = originalFlows.get(srcAndDst);
                thisFlow.put("inFlowCount", 1);//流内采样计数
                thisFlow.put("lastN", 1);
                thisFlow.put("N", Math.ceil(INCREASE_FACTOR*1)); //向上取整
                thisFlow.put("isSilent", 0); //0代表非Silent，1代表Silent。 Silent：Silent=1，表示该N个包中，已有包被采样，后序包不再被采样。
                thisFlow.put("isSampled", 1); //1代表已被采样，其他（包括为0，空或无该字段）表示未被采样。
            } else {
                packetCounter++;
            }

        }

        /**注释掉的方案为可以在每个新流中采集多个包
         *
         if(newFlows==null||newFlows.size()==0) {
         return;
         }
         int threshold = (int) (1/pLists.get(pLists.size()-1));
         for(long srcAndDst : newFlows.keySet()) {
         Map<String, Number> flow = newFlows.get(srcAndDst);
         int numberOfPackets = (int) flow.get("count");
         if(numberOfPackets-packetCounter>=threshold) {
         int sampledCount = (numberOfPackets-packetCounter)/threshold;
         HashMap<String, Number> packets = new HashMap<>(); //此处必须声明为HashMap，否则无法使用HashMap的putAll深复制（Map的putAll为浅复制）
         packets.putAll(flow); //HashMap的putAll深复制，将packets与flow对象分离，不修改flow
         packets.put("count",sampledCount+1);//超过阈值至少有一个
         //packets.remove("time");//时间信息是否移除
         sampledPackets.add(packets);
         packetCounter = numberOfPackets - packetCounter - sampledCount*threshold;

         } else {
         packetCounter+=numberOfPackets;
         }
         flow.put("inFlowCount", numberOfPackets);//流内采样计数
         flow.put("N", 1);
         }*/
    }



}