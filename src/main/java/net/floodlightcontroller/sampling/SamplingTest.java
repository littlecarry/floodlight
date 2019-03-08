package net.floodlightcontroller.sampling;
//import com.kenai.jaffl.annotations.Synchronized;
import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.NodeSelection.NodeSelection;
import net.floodlightcontroller.QoSEvaluation.NetworkStore;
import java.lang.*;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.python.antlr.ast.Str;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;


public class SamplingTest extends Thread {
    //static List<Map<String, Number>> pLists = new ArrayList<>(); //结构：【{"p":0.01,"time":1234567890123},{"p":0.02,"time":9876543210321}】--最大长度为3
    static  ConcurrentHashMap<Long, List<Double>> pListsForSwitches = new ConcurrentHashMap<>();
    //static List<Double> pLists = new ArrayList<>();
    static Map<Long, Integer> packetCountList = new HashMap<>();
    public static ConcurrentLinkedQueue<Map<String, Object>> sampledPackets = new ConcurrentLinkedQueue<>();
    static ConcurrentHashMap<String,ConcurrentHashMap<String, Object>> historyFlows = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Long, Deque<Map<String, Map<String, Object>>>> allFlowAllTimeOfAllSwitch;
    static long count =0;
    static double P = 0.1;
    NodeSelection nodeSelection;
    static long switchId;
    static boolean isFlowSampling;//是否是流采样

    public SamplingTest(NodeSelection nodeSelection, long switchId/*, boolean isFlowSampling*/) {
        this.nodeSelection = nodeSelection;
        this.switchId =switchId;
        //this.isFlowSampling = isFlowSampling;

    }

    @Override
    public void run(){
        //MyLog.info("sampling--here-------------------12345");
        while(true) {
        try {

            //MyLog.info("sampling--here-------------------");
            //MyLog.info("sampling--here-------------------12345678");
            try {
                sleep(15);//s适当设定较大值，否则该线程一直占用锁，flow为空

            } catch (Exception e) {
                continue;
            }
            NetworkStore ns = NetworkStore.getInstance();
            allFlowAllTimeOfAllSwitch = ns.getAllFlowAllTimeOfAllSwitch();
            synchronized(allFlowAllTimeOfAllSwitch) {

                    Deque<Map<String, Map<String, Object>>> allFlowAllTimeOfSwitch = new LinkedBlockingDeque<>(2*NetworkStore.MAX_LENGTH_OF_ALL_FLOW_ALL_TIME_OF_SWITCH);
                    allFlowAllTimeOfSwitch.clear();
                    allFlowAllTimeOfSwitch.addAll(allFlowAllTimeOfAllSwitch.get(switchId));  //存在并发修改但是不影响
                    Map<String, Map<String, Object>> curFlows = allFlowAllTimeOfSwitch.peekLast();
                    simplePacketSampling(curFlows);





                }




        } catch (Exception e) {
            e.printStackTrace();
            MyLog.info("sampling--PacketSamping error: "+e);
        }
        }

    }


    synchronized void simpleFlowSampling(Map<String, Map<String, Object>> flows) {
        long threshold = (long) (1/P);
        if(flows==null||flows.size()==0) {
            MyLog.warn("simpleFlowSampling warn: flows is null");
            return;
        }

        for(String srcAndDst: flows.keySet()) {
            String combineId = switchId + ":" + srcAndDst;
            ConcurrentHashMap<String, Object> flow = new ConcurrentHashMap<>();
            long hisCount = getHisCount(combineId);
            long curCount = (long) flows.get(srcAndDst).get("count");
            if(historyFlows.containsKey(srcAndDst)) {
                if(hisCount < curCount) {
                    long num = curCount -hisCount;
                    flow.putAll(flows.get(srcAndDst));
                    for(int i =0; i<num; i++) {
                        sampledPackets.add(flow);
                        MyLog.info("simpleFlowSampling - SamplingTesr info: 采集到包: keySet: "+flow.keySet()+ "  and values:" + flow.values());
                    }
                    updateHisFlow(combineId, flow, "count", flows.get(srcAndDst).get("count"));
                }

            } else {
                count = count +(long)(flows.get(srcAndDst).get("count"));
                if(count>=threshold) { //以概率p采样，在第1/p点采样
                    long sampledNum = count/threshold;
                    count -= sampledNum*threshold;
                    for(int i=0;i<sampledNum;i++) {
                        flow.clear();
                        flow.putAll(flows.get(srcAndDst));
                        sampledPackets.add(flow);
                        updateHisFlow(combineId, flow);
                        MyLog.info("simpleFlowSampling - SamplingTesr info: 采集到包: keySet: "+flow.keySet()+ "  and values:" + flow.values());
                    }
                }
            }
        }

    }



    void geometricFlowSampling(Map<String, Map<String, Object>> flows) {
        if(flows==null||flows.size()==0) {
            MyLog.warn("simpleFlowSampling warn: flows is null");
            return;
        }
        for(String srcAndDst: flows.keySet()) {
            String combineId = switchId + ":" + srcAndDst;
            long hisCount = (long) historyFlows.get(switchId+":"+srcAndDst).get("count");
            long curCount = (long) flows.get(srcAndDst).get("count");
            ConcurrentHashMap<String, Object> flow = new ConcurrentHashMap<>();
            if(historyFlows.containsKey(srcAndDst)) {
                if(hisCount < curCount) {
                    long num = curCount -hisCount;
                    flow.putAll(flows.get(srcAndDst));
                    for(int i =0; i<num; i++) {
                        sampledPackets.add(flow);
                        MyLog.info("simpleFlowSampling - SamplingTesr info: 采集到包: keySet: "+flow.keySet()+ "  and values:" + flow.values());
                    }
                    updateHisFlow(combineId, flow, "count", flows.get(srcAndDst).get("count"));
                }
            } else {
                //以概率p采样
                long packetNum = (long) (flows.get(srcAndDst).get("count"));
                Random random = new Random();
                for(int i=0; i<packetNum;i++) {
                    if(random.nextDouble()<P) {
                        flow.clear();
                        flow.putAll(flows.get(srcAndDst));
                        sampledPackets.add(flow);
                        updateHisFlow(combineId, flow);
                        MyLog.info("geometricFlowSampling - SamplingTesr info: 采集到包: keySet: "+flow.keySet()+ "  and values:" + flow.values());
                    }
                }

            }
        }

    }


    synchronized void simplePacketSampling(Map<String, Map<String, Object>> flows) {
        if(flows==null||flows.size()==0) {
            MyLog.warn("simpleFlowSampling warn: flows is null");
            return;
        }
        P=0.01;
        long threshold = (long) (1.0/P);
        for(String srcAndDst: flows.keySet()) {
            HashMap<String, Object> flow = new HashMap<>();
            //以概率p采样 1/p毫秒采集某1ms
            //System.out.println(historyFlows.values());
            String combineId = switchId + ":" + srcAndDst;
            long hisCount = getHisCount(combineId);
            long curCount = (long) flows.get(srcAndDst).get("count");
            long num = curCount - hisCount;
            resigiterFlowAndUpdateCount(combineId, curCount, hisCount);
            //MyLog.info("simplePacketSampling - SamplingTesr info: ---------- hisCount: "+((long)historyFlows.get(combineId).get("count"))+" curCount:"+curCount+ "   num:"+num);
            if(num<=0)
                continue;
            //MyLog.info("simplePacketSampling - SamplingTesr info: count>0");

            count += num;
            if(count>=threshold) { //以概率p采样，在第1/p点采样

                long sampledNum = count/threshold;
                //MyLog.info("simplePacketSampling - SamplingTesr info: ---------- count: "+count+ "  and sampledNum:" + sampledNum +" and threshold="+threshold);
                count = count-sampledNum*threshold;
                //MyLog.info("simplePacketSampling - SamplingTesr info: ---------- count: "+count);
                for(int i=0;i<sampledNum;i++) {
                    flow.clear();
                    flow.putAll(flows.get(srcAndDst));
                    sampledPackets.add(flow);
                    MyLog.info("simplePacketSampling - SamplingTesr info: 采集到包: keySet: "+flow.keySet()+ "  and values:" + flow.values());
                }
            }


        }

    }

    void randomPacketSampling(Map<String, Map<String, Object>> flows) {
        if(flows==null||flows.size()==0) {
            MyLog.warn("simpleFlowSampling warn: flows is null");
            return;
        }
        for(String srcAndDst: flows.keySet()) {
            //以概率p采样 1/p毫秒采集最后1ms
            String combineId = switchId + ":" + srcAndDst;
            long hisCount =  getHisCount(combineId);
            long curCount = (long) flows.get(srcAndDst).get("count");
            long num = curCount -hisCount;
            if(num<=0)
                continue;
            resigiterFlowAndUpdateCount(combineId, curCount, hisCount);
            HashMap<String, Object> flow = new HashMap<>();
            Random random = new Random();
            for(int i=0; i<num;i++) {
                if(random.nextDouble()<P) {
                    flow.clear();
                    flow.putAll(flows.get(srcAndDst));
                    sampledPackets.add(flow);
                    MyLog.info("randomPacketSampling - SamplingTesr info: 采集到包: keySet: "+flow.keySet()+ "  and values:" + flow.values());
                }
            }



        }

    }


    void poissonPacketSampling(Map<String, Map<String, Object>> flows) {
        if(flows==null||flows.size()==0) {
            MyLog.warn("simpleFlowSampling warn: flows is null");
            return;
        }
        for(String srcAndDst: flows.keySet()) {
            //以概率p采样

        }

    }




    long getHisCount(String combineId) {
        if(historyFlows.containsKey(combineId)&&historyFlows.get(combineId).containsKey("count"))
            return (long) historyFlows.get(combineId).get("count");
        return 0;
    }



    ConcurrentHashMap<String, Object> getHisFlow(String combineId) {
        if(historyFlows.containsKey(combineId))
            return historyFlows.get(combineId);
        return new ConcurrentHashMap<String, Object>();

    }

    void resigiterFlowAndUpdateCount(String combineId, long curCount, long hisCount){
        ConcurrentHashMap<String, Object> thisFlow = getHisFlow(combineId);
        //MyLog.info("simplePacketSampling - SamplingTesr info: ---------- hisCount: "+((long)getHisCount(combineId))+"hisCount: ");
        if(curCount>hisCount)
            thisFlow.put("count", curCount);
        else
            thisFlow.put("count", hisCount);
        historyFlows.put(combineId,thisFlow);
    }

    void updateHisFlow(String combineId, ConcurrentHashMap<String, Object> flow) {
        ConcurrentHashMap<String, Object> thisFlow = new ConcurrentHashMap<>();
        thisFlow.putAll(flow);
        historyFlows.put(combineId,thisFlow);
    }

    void updateHisFlow(String combineId, ConcurrentHashMap<String, Object> flow, String key, Object val){

        ConcurrentHashMap<String, Object> thisFlow = historyFlows.get(combineId);
        thisFlow.put("count", val);
        historyFlows.put(combineId,thisFlow);
    }




}