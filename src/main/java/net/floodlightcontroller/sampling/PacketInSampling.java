package net.floodlightcontroller.sampling;
//import com.kenai.jaffl.annotations.Synchronized;
import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.QoSEvaluation.NetworkStore;
import java.lang.*;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.python.antlr.ast.Str;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.*;

public class PacketInSampling {
    //static List<Map<String, Number>> pLists = new ArrayList<>(); //结构：【{"p":0.01,"time":1234567890123},{"p":0.02,"time":9876543210321}】--最大长度为3
    static  ConcurrentHashMap<Long, List<Double>> pListsForSwitches = new ConcurrentHashMap<>();
    //static List<Double> pLists = new ArrayList<>();
    static final int N = 6/*参考周期數*/, T = 34/*周期长度，单位为ms*/;  //T的确定与带宽时延丢包有关
    static long lastTime;
    static final double B=1.0, A=1.0;//系数
    static final double initP = 0.01;
    static Map<Long, Integer> packetCountList = new HashMap<>();
    public static ConcurrentLinkedQueue<Map<String, Object>> sampledPackets = new ConcurrentLinkedQueue<>();
    static ConcurrentHashMap<String,ConcurrentHashMap<String, Object>> historyFlows = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Long, Deque<Map<String, Map<String, Object>>>> allFlowAllTimeOfAllSwitch;

    //inFlowSampling params
    static final double INCREASE_FACTOR = 1.2;
    /*static int packetCounter = 1;
    public static HashMap<ItFlow, Integer> flowIntegerHashMap = new HashMap<>();//全局
    protected static List<Map<Integer, Object>> flowCouter = new ArrayList<>();
    protected static double packetSamplingPropobility;*/

    //public static void f(){}

    public void sampling(long switchId) { //该函数存在并发--

        try {
            NetworkStore ns = NetworkStore.getInstance();
            allFlowAllTimeOfAllSwitch = ns.getAllFlowAllTimeOfAllSwitch();
            Deque<Map<String, Map<String, Object>>> allFlowAllTimeOfSwitch = new LinkedBlockingDeque<>(2*NetworkStore.MAX_LENGTH_OF_ALL_FLOW_ALL_TIME_OF_SWITCH);
            allFlowAllTimeOfSwitch.addAll(allFlowAllTimeOfAllSwitch.get(switchId));  //存在并发修改但是不影响
            //MyLog.info("sampling:在该路由器采集:"+switchId+" allFlowAllTimeOfSwitch.size="+allFlowAllTimeOfSwitch.size());
            synchronized(allFlowAllTimeOfAllSwitch) {
                //System.out.println("allFlowAllTimeOfAllSwitch--------"+allFlowAllTimeOfSwitch.size());
                adaptiveAdjustmentForP(switchId);//得到该周期包采样概率p
                int lengthOfTimes = allFlowAllTimeOfSwitch.size();
                //MyLog.info("sampling:在该路由器采集:"+switchId+" allFlowAllTimeOfSwitch.size="+allFlowAllTimeOfSwitch.size()+" after function adaptiveAdjustmentForP");
                Map<String, Map<String, Object>> curFlows = allFlowAllTimeOfSwitch.peekLast();
                Map<String, Map<String, Object>> lastFlows;
                if(allFlowAllTimeOfSwitch.size()>=2) {
                    allFlowAllTimeOfSwitch.removeLast();  //若不与前面的方法同步，会被插队，导致队尾加入新元素
                    lastFlows = allFlowAllTimeOfSwitch.peekLast();
                    if(lastFlows == null)
                        lastFlows = new HashMap<>();
                    allFlowAllTimeOfSwitch.push(curFlows);
                } else {
                    lastFlows = new HashMap<>();
                }
                //MyLog.info("sampling----curFlows.size="+curFlows.size());

                HashMap<String, Map<String, Object>>  newFlows = new HashMap<>(); //新流，首次进入的流
                HashMap<String, Map<String, Object>>  disFlows = new HashMap<>(); //旧流，取流差值

                if(lastFlows == null || lastFlows.isEmpty()) {
                    newFlows.putAll(curFlows);
                } else { //必有非null的lastFlows
                    for(String srcAndDst : curFlows.keySet()) {
                        HashMap<String, Object> flow = new HashMap<>();
                        flow.putAll(curFlows.get(srcAndDst)); //HashMap的声明，深拷贝
                        String combineId = switchId + ":" + srcAndDst;
                        ConcurrentHashMap tempFlow;
                        if(!historyFlows.containsKey(combineId)) {
                            tempFlow = historyFlows.put(combineId, new ConcurrentHashMap<>());
                        } else {
                            tempFlow = historyFlows.get(combineId);
                        }

                        if(!lastFlows.containsKey(srcAndDst)) {
                            if(!(tempFlow.containsKey("isSampled")&&(int) tempFlow.get("isSampled")==1)) {
                                newFlows.put(srcAndDst, flow);
                            } else {
                                disFlows.put(srcAndDst, flow);
                            }

                        } else {
                            //MyLog.info("PacketSampling: isSampled="+flow.get("isSampled"));
                            if(tempFlow.containsKey("isSampled")&&(int) tempFlow.get("isSampled")==1) { //该流之前必经过newFlowSampling模块，否则isSampled！=1
                                long lastCount;
                                if(tempFlow.containsKey("count")&&tempFlow.get("count")!=null) {
                                    lastCount = (long) tempFlow.get("count");
                                } else {
                                    lastCount = 0;
                                    MyLog.error("PacketSampling: 该流之前未经过newFlowSampling模块，该流为"+srcAndDst);
                                }
                                //long lastCount = (long) lastFlows.get(srcAndDst).get("count");
                                long curCount =(long) curFlows.get(srcAndDst).get("count");
                                if(curCount>lastCount) {//TODO >=还是>
                                    long count = curCount-lastCount;
                                    flow.put("count", count); //添加差值
                                    disFlows.put(srcAndDst, flow);
                                } else {

                                    MyLog.error("PacketSampling:該流历史包数目大于流的当前包数目，该流为"+srcAndDst+" curCount="+curCount+" lastCount="+lastCount);
                                }

                            } else {
                                newFlows.put(srcAndDst, flow);
                            }

                            long lastCount;
                            if(tempFlow.containsKey("count")&&tempFlow.get("count")!=null) {
                                lastCount = (long) tempFlow.get("count");
                            } else {
                                lastCount = 0;
                            }
                            long curCount = (long) flow.get("count");
                            if(curCount>lastCount)
                                tempFlow.put("count", curCount);
                            else
                                tempFlow.put("count", lastCount);
                            historyFlows.put(combineId, tempFlow);
                        }

                    }

                    //MyLog.info("PacketSampling:非null的lastFlows，填充新流和旧流");
                    //MyLog.info("sampling----lastFlows == null   and     newFlows.size="+newFlows.size()+" disFlows.size="+disFlows.size()+" lastFlows.size="+lastFlows.size());
                }

                newFlowSampling(newFlows, curFlows, switchId);
                flowCompression(disFlows, curFlows, switchId);//curFlows用于接收字段修改（并全局化）
                /*newFlows.clear();
                disFlows.clear();*/

                //MyLog.info("sampledPackets.size="+sampledPackets.size());
            } //end synchronized


            } catch (Exception e) {
            e.printStackTrace();
            MyLog.info("sampling--PacketSamping error: "+e);
        }
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
        try {
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
        } catch (Exception e) {
            MyLog.warn("adaptiveAdjustmentForP--PacketSampling error: "+e);
            e.printStackTrace();
        }


    }

    /**
     *
     * disFlows理论上需要先经过newFlowSampling后才能到达flowCompression
     * @param disFlows
     * @param originalFlows
     */
    void flowCompression(Map<String, Map<String, Object>> disFlows, Map<String, Map<String, Object>> originalFlows, long switchId) {
                                                                    //流内压缩/采样 disFlows中的流信息全部存在于历史的流内，
                                                                    // disFlows代表（当前与历史流中都存在的流）之间的差值
        try{
            //MyLog.info("flowCompression - PacketInSampling info: before disFlow judge-----");
            if(disFlows==null||disFlows.size()==0) {
            /*if(disFlows==null)
                MyLog.info("flowCompression - disFlow==null");
            else
                MyLog.info("flowCompression - disFlow.size=0");*/
                return;
            }
            //MyLog.info("flowCompression - PacketInSampling info: doing flowCompression--------------- disFlows.size= "+disFlows.size());
            for(String srcAndDst : disFlows.keySet()) {
                Map<String, Object> flow =disFlows.get(srcAndDst);

                int inFlowCount, lastN, N, isSilent;
                long count;
                String combineId = switchId + ":" + srcAndDst;
                if(historyFlows.get(combineId)==null) {
                    MyLog.error("flowCompression - PacketInSampling error: disFlows 未经过 newFlowSampling（新流采样）函数 -- FIRST CHECK");
                    continue;
                }
                ConcurrentHashMap<String, Object> tempFlow = historyFlows.get(combineId);

                if(tempFlow.containsKey("inFlowCount")
                        &&tempFlow.containsKey("lastN")
                        &&tempFlow.containsKey("N")
                        &&tempFlow.containsKey("isSilent")
                        &&tempFlow.containsKey("count")
                        &&tempFlow.get("inFlowCount")!=null
                        &&tempFlow.get("lastN")!=null
                        &&tempFlow.get("N")!=null
                        &&tempFlow.get("isSilent")!=null
                        &&tempFlow.get("count")!=null
                ) {
                    inFlowCount = (int) tempFlow.get("inFlowCount");
                    lastN = (int) tempFlow.get("lastN");
                    N = (int) tempFlow.get("N");
                    isSilent = (int) tempFlow.get("isSilent");
                    count = (long) tempFlow.get("count");
                } else {
                    MyLog.error("flowCompression - PacketInSampling error: disFlows 未经过 newFlowSampling（新流采样）函数  -- SECOND CHECK");
                    continue;
                }

               // MyLog.info("flowCompression - PacketInSampling info: inFlowCount "+inFlowCount + "  and count:" + count +" N="+ N);
                int loopTurn = 0;
                while(inFlowCount +count > N) {
                    //MyLog.info("flowCompression - PacketInSampling info: 循环内 inFlowCount "+inFlowCount + "  and count:" + count +" N="+ N);
                    if(isSilent==0) {
                        HashMap<String, Object> packet = new HashMap<>();
                        packet.putAll(flow);
                        //packet.remove("count"); //TODO 包中添加count和flowCount
                        sampledPackets.add(packet);             //TODO: 该步为包信息采样，需好好扩充
                        MyLog.info("flowCompression - PacketInSampling info: 采集到包: keySet: "+packet.keySet()+ "  and values:" + packet.values());
                    }

                    count-= N-inFlowCount;
                    inFlowCount = 0;
                    lastN = N;
                    N = (int) (Math.ceil(INCREASE_FACTOR*N));
                    isSilent =0; //不再silent
                    if(++loopTurn>100) {
                        MyLog.warn("flowCompression:循环次数超过100.");
                    }
                    //MyLog.info("flowCompression: N=" +N);
                }

                inFlowCount+= count;

                //Map<String, Object> thisFlow = originalFlows.get(srcAndDst);
                tempFlow.put("inFlowCount", inFlowCount);//流内采样计数
                tempFlow.put("lastN", lastN);
                tempFlow.put("N", N);
                tempFlow.put("isSilent", 0); //0代表非Silent，1代表Silent。 Silent：Silent=1，表示该N个包中，已有包被采样，后序包不再被采样。
                tempFlow.put("isSampled", 1); //1代表已被采样，其他（包括为0，空或无该字段）表示未被采样。
                /*tempFlow.put("combineId", combineId);
                tempFlow.put("switchId", switchId); //在哪个路由器采的数据包
                tempFlow.put("srcIP", flow.get("srcIP"));
                tempFlow.put("dstIP", flow.get("dstIP"));
                tempFlow.put("srcMac", flow.get("srcMac"));
                tempFlow.put("dstMac", flow.get("dstMac"));
                tempFlow.put("protocolType", flow.get("protocolType"));
                tempFlow.put("count", flow.get("packetCount")); //packetCount(流中包含的包数目)
                tempFlow.put("byteCount", flow.get("byteCount"));
                tempFlow.put("time", flow.get("time"));*/
                //随机采样函数
                double num = Math.random();

                if(num<1.0*inFlowCount/N){//采样
                    tempFlow.put("isSilent", 1);
                } else { //不采样
                    tempFlow.put("isSilent", 0);
                }

            }
        } catch (Exception e) {
            MyLog.warn("flowCompression--PacketSampling error: "+e);
            e.printStackTrace();
        }


    }

    void newFlowSampling(Map<String, Map<String, Object>> newFlows, Map<String, Map<String, Object>> originalFlows, long switchId) { //新采样模式每个流最多只能采集一个包，并最多被计数一次 ---经过该函数的包最终未都被采样
        try {
            //MyLog.info("newFlowSampling - PacketInSampling info: before newFlows judge-----");
            if(newFlows==null||newFlows.size()==0) {
                return;
            }

            int packetCounter;
            if(packetCountList.containsKey(switchId)) {
                packetCounter = packetCountList.get(switchId);
            } else {
                packetCounter = 0;
                packetCountList.put(switchId, packetCounter);
            }

            if(!pListsForSwitches.containsKey(switchId) || pListsForSwitches.get(switchId)==null) {
                List<Double> list = new ArrayList<>();
                list.add(initP);
                pListsForSwitches.put(switchId, list);
                MyLog.error("PacketInSampling-newFlowSampling error: switchId not found");  //TODO: bug switch not found
            }

            ArrayList<Double> pLists = new ArrayList<>();
            pLists.addAll(pListsForSwitches.get(switchId));

            //MyLog.info("newFlowSampling - PacketInSampling info: doing newFlowSampling---------------");
            int threshold;
            if(pLists.size()<1) {
                threshold = 1;
            } else  {
                threshold = (int) (1/pLists.get(pLists.size()-1));
            }
            //MyLog.info("newFlowSampling - ClassCastException After this---------------");
            for(String srcAndDst : newFlows.keySet()) {
                Map<String, Object> flow = newFlows.get(srcAndDst);
                String combineId = switchId+":"+srcAndDst/*+":"+flow.get("protocolType")*/;
                ConcurrentHashMap<String, Object> tempFlow;
                if(!historyFlows.containsKey(combineId)) {
                    tempFlow = new ConcurrentHashMap<>();
                    tempFlow.put("combineId", combineId);
                    historyFlows.put(combineId, tempFlow);
                } else {
                    tempFlow =historyFlows.get(combineId);
                }

                if(tempFlow.containsKey("isSampled")&&(int) tempFlow.get("isSampled")==1) { //已被采样，采样标记为1
                    continue; //
                }

                long numberOfPackets;
                if(tempFlow.containsKey("count")&&tempFlow.get("count")!=null) {
                    numberOfPackets = (long) tempFlow.get("count");
                } else {
                    numberOfPackets = (long) flow.get("count");
                }

                //MyLog.info("newFlowSampling - PacketInSampling info: numberOfPackets "+numberOfPackets + "  and packetCounter:" + packetCounter +" threshold="+ threshold);
                //boolean flag =true;

                if(numberOfPackets+packetCounter>=threshold) {
                //if(flag) {
                    HashMap<String, Object> packet = new HashMap<>(); //此处必须声明为HashMap，否则无法使用HashMap的putAll深复制（Map的putAll为浅复制）
                    packet.putAll(flow); //HashMap的putAll深复制，将packets与flow对象分离，不修改flow
                    //修改字节数
                    //packet.remove("count"); //单个packet包与流不同，没有包数目的字段
                    //packets.remove("time");//时间信息是否移除
                    sampledPackets.add(packet);
                    MyLog.info("PacketInSampling-newFlowSampling info: 采集到包: keySet: "+packet.keySet()+ "  and values:" + packet.values());
                    packetCounter= (int) ((packetCounter+numberOfPackets)-threshold); //只采第一个包
                    tempFlow.put("inFlowCount", 1);//流内采样计数
                    tempFlow.put("lastN", 1);
                    tempFlow.put("N",  (int) Math.ceil(INCREASE_FACTOR*1)); //向上取整
                    tempFlow.put("isSilent", 0);//0代表非Silent，1代表Silent。 Silent：Silent=1，表示该N个包中，已有包被采样，后序包不再被采样。
                    tempFlow.put("isSampled", 1);//1代表已被采样，其他（包括为0，空或无该字段）表示未被采样。
                    tempFlow.put("combineId", combineId);
                    tempFlow.put("switchId", switchId); //在哪个路由器采的数据包
                    tempFlow.put("srcIP", flow.get("srcIP"));
                    tempFlow.put("dstIP", flow.get("dstIP"));
                    tempFlow.put("srcMac", flow.get("srcMac"));
                    tempFlow.put("dstMac", flow.get("dstMac"));
                    tempFlow.put("protocolType", flow.get("protocolType"));
                    tempFlow.put("time", flow.get("time"));
                    tempFlow.put("count", flow.get("count")); //packetCount(流中包含的包数目)
                    tempFlow.put("byteCount", flow.get("byteCount"));

                    historyFlows.put(combineId, tempFlow);
                   // flag = false;
                } else {
                    packetCounter+=numberOfPackets; //属于系统而非某个流
                }

            }
            packetCountList.put(switchId, packetCounter);
        } catch (Exception e) {
            MyLog.warn("newFlowSampling--PacketSampling error: "+e);
            e.printStackTrace();
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