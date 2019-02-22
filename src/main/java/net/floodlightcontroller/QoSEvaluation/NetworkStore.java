package net.floodlightcontroller.QoSEvaluation;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.stat.StatField;
import org.projectfloodlight.openflow.types.*;

import net.floodlightcontroller.core.IOFSwitchBackend;
import net.floodlightcontroller.core.internal.OFSwitch;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.linkdiscovery.internal.LinkInfo;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;

public class NetworkStore {
    protected static NetworkStore ns;
    //现在----历史
    /*protected  List<LinkDataInfo> currentLinkStatus;
    protected List<LinkDataInfo> historyLinkStatus;
    protected List<LinkTimeInfo> linkTimeStatus;*/
    //protected static List<Map<String, Map<String, Number>>> allFlowAllTimeOfSwitch;
    protected  List<Map<String, Map<String, Object>>> allFlowAllTimeOfSwitch;
    protected  static final int MAX_LENGTH_OF_ALL_FLOW_ALL_TIME_OF_SWITCH =100;

    protected Map<String, Double> QosOfLinks; //瞬时的
    protected Map<String, Double> SecurityOfNodes; //瞬时的节点安全性

    //时延
    protected Map<DatapathId, Long> echoReplyDelay;
    protected Map<String, Double> delayOfLinks;   //key是srcSwitchAndPortAndDstSwitchAndPort

    //丢包
    protected Map<String, Double> droppedPacketsOfLinks; //key是srcSwitchAndPort

    //通量
    protected Map<String, Long> throughOfLinks; //key是dstSwitchAndPort
    protected Map<String, Long> historyBytesOfLinks; //key是dstSwitchAndPort


    //安全值for所有节点
    protected double security = 0.8;


    public NetworkStore(){
        /*currentLinkStatus = new ArrayList<LinkDataInfo>();
        historyLinkStatus = new ArrayList<LinkDataInfo>();
        linkTimeStatus = new ArrayList<LinkTimeInfo>();*/

        allFlowAllTimeOfSwitch = new ArrayList<>();
        QosOfLinks = new HashMap<>();
        SecurityOfNodes = new HashMap<>();
        echoReplyDelay = new HashMap<>();
        delayOfLinks = new HashMap<>();
        droppedPacketsOfLinks = new HashMap<>();
        throughOfLinks = new HashMap<>();
        historyBytesOfLinks = new HashMap<>();
    }

    /**
     * 单例模式
     *
     * @return
     */
    public static synchronized NetworkStore getInstance() {

        if (ns == null) {
            ns = new NetworkStore();
        }
        return ns;

    }

    //packetin和echo消息结合测时延

    public void handlePacketIn(IPacket iPacket, ILinkDiscoveryService linkService) {

        IPv4 ip = (IPv4) iPacket;
        Data data = (Data) ip.getPayload();
        //封装时用“<>”作了分隔。此时可以通过这种分隔得到对应个数（此处为5个）的内容。
        String mess[] = new String(data.getData()).split("<>");
        if (mess.length != 5) {
            MyLog.error("handlePacketIn-NetworkStore：length is not 5!");
            return;
        }

        //计算时延
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
        Date currentTime = new Date();
        long allTime = 0;
        try {
            Date sendTime = df.parse(mess[0]);
            allTime = currentTime.getTime() - sendTime.getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }


        //System.out.println("allTime:=============="+allTime);
        //找出对应链路（有且只有单向的一条）
        Map<Link, LinkInfo> links = linkService.getLinks();
        for (Link l : links.keySet()) {

            DatapathId aimedSrc = DatapathId.of(mess[1]);
            int aimedSrcPort = Integer.parseInt(mess[2]);//也可以只使用链路的源交换机地址和端口号定位链路（mess[1]和mess[2]）
            DatapathId aimedDst = DatapathId.of(mess[3]);
            int aimedDstPort = Integer.parseInt(mess[4]);


            if (l.getSrc().equals(aimedSrc) &&
                    l.getSrcPort().getPortNumber() == aimedSrcPort &&
                    l.getDst().equals(aimedDst) &&
                    l.getDstPort().getPortNumber() == aimedDstPort) {

                String key = aimedSrc.getLong()+":"+aimedSrcPort+":"+aimedDst.getLong()+":"+aimedDstPort; //linkInfo

                long srcEchoReplyDelay = 0, dstEchoReplyDelay = 0;
                if(echoReplyDelay.containsKey(aimedSrc)) {
                    srcEchoReplyDelay = echoReplyDelay.get(aimedSrc);
                }
                if(echoReplyDelay.containsKey(aimedDst)) {
                    dstEchoReplyDelay = echoReplyDelay.get(aimedDst);
                }

                if(allTime-srcEchoReplyDelay-dstEchoReplyDelay>0) {
                    delayOfLinks.put(key, 0.0 + allTime-srcEchoReplyDelay-dstEchoReplyDelay);
                    //System.out.println("delay------:"+(0.0 + allTime-srcEchoReplyDelay-dstEchoReplyDelay));
                } else {
                    delayOfLinks.put(key, new Double(0));
                   /* MyLog.warn("handlePacketIn Error: 所测时延小于0， 为" + allTime+" "+srcEchoReplyDelay+" "+
                            dstEchoReplyDelay+" "+(allTime-srcEchoReplyDelay-dstEchoReplyDelay));*/
                }
                break;
            }

        }
    }

    /**
     * echo响应消息处理--测量各个交换机与控制器的时延--时延测量
     * @param reply
     */
    public void handleEchoReply(OFEchoReply reply) {

        if (reply.getData().length <= 0)
            return;
        String[] data = new String(reply.getData()).split("<>");
        if (data.length != 2) { //当前时间和所属交换机
            MyLog.error("handleEchoReply-NetworkStore： length is not 2!");
            return;
        }
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
        Date currentTime = new Date();
        long time =0;
        try {
            Date sendTime = df.parse(data[0]);
            DatapathId switchId = DatapathId.of(data[1]);
            time = (currentTime.getTime() - sendTime.getTime())/2; // 该节点到控制器的时延
            if(time>0) {
                //System.out.println("echo------:"+time);
                echoReplyDelay.put(switchId, time);
            } else { //考虑异常（时延<0）：异常时使用上一次时延，若不存在上一次时延令时延为0.
                if(!echoReplyDelay.containsKey(switchId)) {
                    echoReplyDelay.put(switchId, new Long(0));
                    //System.out.println("echo------:"+0);
                } else {
                    //System.out.println("echo------:"+echoReplyDelay.get(switchId));
                }
            }

        } catch (ParseException e) {
            e.printStackTrace();
        }
        //更新记录
       /* for (LinkTimeInfo lti : linkTimeStatus) {
            if (lti.getL().getSrc().equals(DatapathId.of(data[1])))
                lti.setCtossw(Time / 2);
            if (lti.getL().getDst().equals(DatapathId.of(data[1])))
                lti.setCtodsw(Time / 2);
            if (lti.getCtodsw() != -1 && lti.getCtossw() != -1) {
                long delay = lti.getAllTime() - lti.getCtodsw() - lti.getCtossw();
                lti.setDelay(delay >= 0 ? delay : 0);
                MyLog.info("时延：" + lti.getDelay()); //delay为最终的时延
            }

        }*/

    }


    //端口消息测丢包率--得到的是交换机所有端口的消息
    public void handlePortStatsReply(OFPortStatsReply reply, IOFSwitchBackend aSwitch) {
        List<OFPortStatsEntry> portStatsEntries = reply.getEntries();//得到消息体
        for (OFPortStatsEntry entry : portStatsEntries) {
            /**
             * entry.getRXErrors();    //Rx:表示接收的数目 Tx：发送的数目
             * entry.getTxDropped();
             * entry.getTxErrors();
             * */
            OFPort srcPort = entry.getPortNo();//可以对应
            String key = aSwitch.getId().getLong() + ":" + srcPort.getPortNumber();
            double droppedPercent;
            if(entry.getTxPackets()!=null && entry.getTxDropped()!=null) {
                droppedPercent = 1.0 * entry.getTxDropped().getValue() / entry.getTxPackets().getValue();
            } else {
                MyLog.error("NetworkStore-handlePortStatsReply Error: 丢包率测量出错，丢包或收到的包对象为null");
                droppedPercent = 0;
            }
            //System.out.println("droppedPackets------:"+droppedPercent);
            droppedPacketsOfLinks.put(key, new Double(droppedPercent));

          /*  entry.getTxPackets().getValue();//发送的包数目
            entry.getTxDropped().getValue();//丢失的包数目 相除得到发送的丢包率
            entry.getRxPackets();
            entry.getRxDropped();//相除得到接收的丢包率*/
        }
    }


    /**
     * 处理FlowStataReply消息--获得通量测量的原始数据
     *
     * @param reply
     * @param sw
     */
    //按流为单位，一个流从源地址到目的地址，因此会有一系列的流表项，而不是一个流表项
    /**
     * 该方法最后给出了每条链路的出入交换机及对应端口号，以及流经比特数（求当前带宽），最大带宽
     */
    public void handleFlowStatsReply_combineWithSwitchPorts(OFFlowStatsReply reply, IOFSwitchBackend aSwitch) {

        IOFSwitch dstSwitch = aSwitch;
        OFPort inPort = null; //inPort：匹配域中的端口，outPort：（没有流表冲突时）表示某链路的源端口（该交换机为该链路的源节点）--因此以inPort计更准确
        long byteCount = 0;
        List<OFFlowStatsEntry> entries = reply.getEntries();
        if(entries == null || entries.size()==0) {
            MyLog.warn("handleFlowStatsReply_combineWithSwitchPorts error： 通量测量出错， 流状态响应流表为空");
        }
        for (OFFlowStatsEntry e : entries) {
            byteCount = e.getByteCount().getValue()/8; //除以8转化为字节（B）

            //Iterator<StatField<?>> kk = e.getStats().getStatFields().iterator();

            inPort = e.getMatch().get(MatchField.IN_PORT);

            if (inPort == null || inPort == OFPort.ALL) {//to controller
                //MyLog.info("inPort is null, WARN in handleFlowStatsReply module");
                continue;
            }

            if(byteCount<0)
                MyLog.warn("handleFlowStatsReply_combineWithSwitchPorts error： 通量测量出错， byteCount<0");
            String dstSwitchAndPort = aSwitch.getId().getLong()+":"+inPort.getPortNumber();
            if(historyBytesOfLinks.containsKey(dstSwitchAndPort)) {
                historyBytesOfLinks.put(dstSwitchAndPort, byteCount);
                long through= byteCount - throughOfLinks.get(dstSwitchAndPort);
                if(through<0)
                    through =0;
                //throughOfLinks.remove(dstSwitchAndPort);
                throughOfLinks.put(dstSwitchAndPort, through);
            } else {
                historyBytesOfLinks.put(dstSwitchAndPort, byteCount);
                throughOfLinks.put(dstSwitchAndPort, (long) 0);
            }

            //throughOfLinks.put(dstSwitchAndPort, byteCount);

        }
        /*for(String str :historyBytesOfLinks.keySet()) {
            System.out.println("through------:"+str+" bytecount="+throughOfLinks.get(str));
        }*/

    }


    /**
     *
     * 作用：用于Sampling流量采样模块
     * 思路：将匹配域中同一源地址源端口，目的地址目的端口的流合并为一个流，并分配流id，采样时根据流id识别
     * @param reply
     * @param sw
     */
    public void handleFlowStatsReply_combineWithIPAndPorts(OFFlowStatsReply reply, IOFSwitchBackend sw) { //对非网络包无效，因为统计的是IP地址

        long packetCount;
        long byteCount;
        //String srcAndDst = null;
        String srcAndDst;
        IPv4Address dstAdd,srcAdd;
        PacketType type;
        MacAddress srcMac, dstMac;
        OFPort inPort,outPort=null;
        String typeName;

        Map<String, Map<String, Object>> flowsThisTerm = new HashMap<>();
        long time = new Date().getTime();

        int lastCount = allFlowAllTimeOfSwitch.size();

        try {
            List<OFFlowStatsEntry> entries = reply.getEntries();

            System.out.println("-----flowsThisTerm---- before loop");
            for (OFFlowStatsEntry e :entries) {
                if(e == null)
                    continue;


                //Iterable<MatchField<?>> matchFields = ((OFMatchV3) e.getMatch()).getMatchFields(MatchField.BSN_INGRESS_PORT_GROUP_ID);
                //e.getMatch().get();
                //srcAndDst = srcAdd.getInt()+":"+dstAdd.getInt(); //键（key）用字符串形式实现，与long效果上一样，类型不一样
                inPort =e.getMatch().get(MatchField.IN_PORT);


                if(inPort==null || inPort ==OFPort.ALL) {
                    continue;
                }

                //得到outPort
                List<OFInstruction> instruction = e.getInstructions();
                for (OFInstruction i : instruction) {
                    if (i instanceof OFInstructionApplyActions) {
                        List<OFAction> action = ((OFInstructionApplyActions) i).getActions();
                        for (OFAction a : action) {
                            if (a.getType() == OFActionType.OUTPUT) {
                                outPort = ((OFActionOutput) a).getPort();
                                break;
                            }
                        }
                    } else
                        continue;
                }

                if(outPort.getPortNumber()<1)
                    continue;


                packetCount = e.getPacketCount().getValue();
                byteCount = e.getByteCount().getValue();
                dstAdd =e.getMatch().get(MatchField.IPV4_DST);
                srcAdd = e.getMatch().get(MatchField.IPV4_SRC);
                type = e.getMatch().get(MatchField.PACKET_TYPE);
                srcMac = e.getMatch().get(MatchField.ETH_SRC);
                dstMac = e.getMatch().get(MatchField.ETH_DST);

                srcAndDst = srcAdd.getInt()+":"+dstAdd.getInt();

               /* System.out.println("-----flowsThisTerm---- packetCount="+packetCount+" dstAdd="+dstAdd
                        +" srcAdd="+srcAdd+" inPort="+inPort
                        +" srcMac="+srcMac+" dstMac="+dstMac
                        +" type="+type);*/

                typeName = "";
                if(type!=null) {
                    int namespace = type.getNamespace();
                    int nsType = type.getNsType();
                    //System.out.println("-----flowsThisTerm---- namespace="+namespace+" nsType="+nsType);
                    switch(namespace) {
                        case 0:
                            switch (nsType) {
                                case 0:
                                    typeName = "ETHERNET";
                                    break;
                                case 1:
                                    typeName =  "NO_PACKET";
                                    break;
                                case 0xFFFF:
                                    typeName = "EXPERIMENTER";
                            }
                            break;
                        case 1:
                            switch (nsType) {
                                case 0x800:
                                    typeName = "IPV4";
                                    break;
                                case 0x86dd:
                                    typeName = "IPV6";
                            }
                            break;
                    }

                } else {
                    typeName = "LOW_LAYER_PACKET";
                    //MyLog.warn("handleFlowStatsReply_combineWithIPAndPorts error： Sampling 统计信息收集出错， 流表中流的协议为空");
                }

                if(flowsThisTerm.containsKey(srcAndDst)) {
                    System.out.println("-----flowsThisTerm---- 00");
                    Map<String, Object> enums = flowsThisTerm.get(srcAndDst);
                    int count = (int) enums.get("count");
                    enums.put("count", count + packetCount);
                    flowsThisTerm.put(srcAndDst, enums);
                    System.out.println("-----flowsThisTerm---- 01");
                } else {
                    Map<String, Object> enums = new HashMap<>();
                    enums.put("srcIP", srcAdd.getInt());
                    enums.put("dstIP", dstAdd.getInt());
                    enums.put("srcMac", srcMac.getLong());
                    enums.put("dstMac", dstMac.getLong());
                    enums.put("packetType", typeName);
                    enums.put("count", 1); //packetCount(流中包含的包数目)
                    enums.put("byteCount", byteCount);
                    enums.put("time", time);
                    flowsThisTerm.put(srcAndDst, enums);
                }
               // System.out.println("-----flowsThisTerm---- keys="+flowsThisTerm.keySet()+" values="+flowsThisTerm.values());

            }


            if(allFlowAllTimeOfSwitch==null)
                MyLog.error("-----flowsThisTerm---- allFlowAllTimeOfSwitch is null");
            if(flowsThisTerm==null)
                MyLog.error("-----flowsThisTerm---- flowsThisTerm is null");
            allFlowAllTimeOfSwitch.add(flowsThisTerm);
            System.out.println("-----flowsThisTerm---- 9");

            //TODO --计算流经该交换机的总包数
        } catch (Exception e) {
            MyLog.error("handleFlowStatsReply_combineWithIPAndPorts error： Sampling 统计信息收集出错，抛出异常");

            if(lastCount+1 == allFlowAllTimeOfSwitch.size()) {
                allFlowAllTimeOfSwitch.remove(allFlowAllTimeOfSwitch.size()-1);
            } else if(lastCount!=allFlowAllTimeOfSwitch.size()){
                MyLog.error("handleFlowStatsReply_combineWithIPAndPorts error: 异常时出现另一个错误，时间周期数过长");
                if(lastCount>0)
                    allFlowAllTimeOfSwitch = allFlowAllTimeOfSwitch.subList(0, lastCount-1);

            }
            lastCount = allFlowAllTimeOfSwitch.size();
            e.printStackTrace();
        }

        if(allFlowAllTimeOfSwitch.size()>MAX_LENGTH_OF_ALL_FLOW_ALL_TIME_OF_SWITCH) { //将list限制在最大长度以内(长度n为时间周期数)
            allFlowAllTimeOfSwitch.remove(0);
        }

    }





    public List<Map<String, Map<String, Object>>> getAllFlowAllTimeOfSwitch() {
        return allFlowAllTimeOfSwitch;
    }

    public Map<String, Double> getQosOfLinks() {
        return QosOfLinks;
    }

    public Map<String, Double> getSecurityOfNodes() {
        return SecurityOfNodes;
    }

    public Map<DatapathId, Long> getEchoReplyDelay() {
        return echoReplyDelay;
    }

    public Map<String, Double> getDelayOfLinks() {
        return delayOfLinks;
    }

    public Map<String, Double> getDroppedPacketsOfLinks() {
        return droppedPacketsOfLinks;
    }

    public Map<String, Long> getThroughOfLinks() {
        return throughOfLinks;
    }

    public double getSecurity() {
        return security;
    }





//以下为教程代码，本实验不使用


    /**
     * 处理FlowStataReply消息--获得带宽（band）测量的原始数据
     *
     * @param reply
     * @param sw
     */
    //按流为单位，一个流从源地址到目的地址，因此会有一系列的流表项，而不是一个流表项
    /**
     * 该方法最后给出了每条链路的出入交换机及对应端口号，以及流经比特数（求当前带宽），最大带宽
     */
    /*public void handleFlowStatsReply2(OFFlowStatsReply reply, IOFSwitchBackend sw) {

        OFSwitch fromSw, toSw = null;
        OFPort inPort, outPort = null;
        long byteCount, maxBand, currentBand = 0;
        fromSw = toSw = (OFSwitch) sw;
        List<OFFlowStatsEntry> entries = reply.getEntries();
        for (OFFlowStatsEntry e : entries) {
            byteCount = e.getByteCount().getValue();
            inPort = e.getMatch().get(MatchField.IN_PORT);

            if (inPort == null) {//to controller
                //MyLog.info("inPort is null, WARN in handleFlowStatsReply module");
                inPort = OFPort.ALL;
            }
            //得到outPort
            List<OFInstruction> instruction = e.getInstructions();
            for (OFInstruction i : instruction) {
                if (i instanceof OFInstructionApplyActions) {
                    List<OFAction> action = ((OFInstructionApplyActions) i).getActions();
                    for (OFAction a : action) {
                        if (a.getType() == OFActionType.OUTPUT) {
                            outPort = ((OFActionOutput) a).getPort();
                            break;
                        }
                    }
                } else
                    continue;
            }
            //默认的流表项不需要存储
            if (inPort == OFPort.ALL || outPort.getPortNumber() < 1) {
                continue;
            }
            //构造链路信息对象
            maxBand = calculateMaxBand(fromSw, toSw, inPort, outPort);
            LinkDataInfo ldi = new LinkDataInfo();
            ldi.setFromSw(fromSw);
            ldi.setToSw(toSw);
            ldi.setInPort(inPort);
            ldi.setOutPort(outPort);
            ldi.setMaxBand(maxBand);
            ldi.setByteCount(byteCount);
            //存储
            storeLinkStatus(ldi);
        }

    }*/

   /* //存储
    public void storeLinkStatus(LinkDataInfo ldi) {

        if (currentLinkStatus == null) {
            System.out.println("currentLinkStatus为null");
            return;
        }

        //System.out.println(currentLinkStatus+"===============");
        System.out.println("currentLinkInfo is null?" + (ldi == null));
        if (currentLinkStatus.size() == 0)//first time
        {
            currentLinkStatus.add(ldi);
            //System.out.println(currentLinkStatus+"===============After");
        } else {
            for (LinkDataInfo l : currentLinkStatus) {
                if (l.getFromSw().getId() == ldi.getFromSw().getId() &&
                        l.getToSw().getId() == ldi.getToSw().getId() &&
                        l.getInPort().getPortNumber() == ldi.getInPort().getPortNumber() &&
                        l.getOutPort().getPortNumber() == ldi.getOutPort().getPortNumber()) {
                    l.setByteCount(l.getByteCount() + ldi.getByteCount());
                }
            }
            //new
            currentLinkStatus.add(ldi);
        }
    }

    public void nextMeterBegin() {

        historyLinkStatus.clear();
        for (LinkDataInfo l : currentLinkStatus) {
            historyLinkStatus.add(l);
        }
        currentLinkStatus.clear();
        linkTimeStatus.clear();
    }*/

    //计算链路的最大带宽---入口带宽与出口带宽的最小值
    /*public long calculateMaxBand(OFSwitch fromSw, OFSwitch toSw, OFPort inPort, OFPort outPort) {

        long fromBand = 0, toBand = 0;
        //inport
        OFPortDesc inPortDesc = fromSw.getPort(inPort);
        Set<OFPortFeatures> infeatures = inPortDesc.getAdvertised(); //features为交换机属性
        //?
        for (OFPortFeatures f : infeatures) {
            fromBand = f.getPortSpeed().getSpeedBps();//bps
            if (fromBand > 0)
                break;
        }
        //outPort
        OFPortDesc outPortDesc = toSw.getPort(outPort);
        Set<OFPortFeatures> outfeatures = outPortDesc.getAdvertised();
        //?
        for (OFPortFeatures f : outfeatures) {
            toBand = f.getPortSpeed().getSpeedBps();//bps
            if (toBand > 0)
                break;
        }
        return (fromBand >= toBand ? toBand : fromBand);
    }*/

    /*//计算当前带宽，输出
    public void calCurrentBand() {
        for(LinkDataInfo h:historyLinkStatus)
            for (LinkDataInfo c : currentLinkStatus) {
                if (h.getFromSw().getId() == c.getFromSw().getId() &&
                        h.getToSw().getId() == c.getToSw().getId() &&
                        h.getInPort().getPortNumber() == c.getInPort().getPortNumber() &&
                        h.getOutPort().getPortNumber() == c.getOutPort().getPortNumber()) {
                    long speed = (c.getByteCount() - h.getByteCount()) / 1;
                    float band = (float) (speed * 1.0 / c.maxBand);
                    System.out.println("currentspeed:" + speed + "Bps" + "    currentBand:" + band * 100 + "%");
                }
            }

    }*/
}

/*class LinkDataInfo {

    protected OFSwitch fromSw;
    protected OFSwitch toSw;
    protected OFPort inPort;
    protected OFPort outPort;
    protected long byteCount;
    protected long maxBand;
    protected long currentBand;

    public OFSwitch getFromSw() {
        return fromSw;
    }

    public void setFromSw(OFSwitch fromSw) {
        this.fromSw = fromSw;
    }

    public OFSwitch getToSw() {
        return toSw;
    }

    public void setToSw(OFSwitch toSw) {
        this.toSw = toSw;
    }

    public OFPort getInPort() {
        return inPort;
    }

    public void setInPort(OFPort inPort) {
        this.inPort = inPort;
    }

    public OFPort getOutPort() {
        return outPort;
    }

    public void setOutPort(OFPort outPort) {
        this.outPort = outPort;
    }

    public long getByteCount() {
        return byteCount;
    }

    public void setByteCount(long byteCount) {
        this.byteCount = byteCount;
    }

    public long getMaxBand() {
        return maxBand;
    }

    public void setMaxBand(long maxBand) {
        this.maxBand = maxBand;
    }

    public long getCurrentBand() {
        return currentBand;
    }

    public void setCurrentBand(long currentBand) {
        this.currentBand = currentBand;
    }


}*/

/*class LinkTimeInfo {

    protected Link l;
    protected long allTime = -1;
    protected long ctossw = -1;
    protected long ctodsw = -1;
    protected long delay = -1;

    public Link getL() {
        return l;
    }

    public void setL(Link l) {
        this.l = l;
    }

    public long getAllTime() {
        return allTime;
    }

    public void setAllTime(long allTime) {
        this.allTime = allTime;
    }

    public long getCtossw() {
        return ctossw;
    }

    public void setCtossw(long time) {
        this.ctossw = time;
    }

    public long getCtodsw() {
        return ctodsw;
    }

    public void setCtodsw(long ctodsw) {
        this.ctodsw = ctodsw;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long m) {
        this.delay = m;
    }


}*/

/*
class MulLinkDataInfo {

    private List<LinkDataInfo> linkDataInfos;

    MulLinkDataInfo() {
        this.linkDataInfos = new ArrayList<>();
    }

    public List<LinkDataInfo> getLinkDataInfos() {
        return linkDataInfos;
    }


}
*/


