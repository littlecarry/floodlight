package net.floodlightcontroller.QoSEvaluation;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import net.floodlightcontroller.MyLog;
import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFEchoReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortFeatures;
import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFPort;

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
    protected  List<LinkDataInfo> currentLinkStatus;
    protected List<LinkDataInfo> historyLinkStatus;
    protected List<LinkTimeInfo> linkTimeStatus;
    //protected static List<Map<String, Map<String, Number>>> allFlowAllTimeOfSwitch;
    protected  List<Map<Long, Map<String, Number>>> allFlowAllTimeOfSwitch;
    protected  static final int MAX_LENGTH_OF_ALL_FLOW_ALL_TIME_OF_SWITCH =100;

    protected List<Double> QoSLists;
    protected List<Double> securityLists;

    public NetworkStore(){
        currentLinkStatus = new ArrayList<LinkDataInfo>();
        historyLinkStatus = new ArrayList<LinkDataInfo>();
        linkTimeStatus = new ArrayList<LinkTimeInfo>();
        allFlowAllTimeOfSwitch = new ArrayList<>();
        QoSLists = new ArrayList<>();
        securityLists = new ArrayList<>();
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
            MyLog.error("length is not 5!");
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
            if (l.getSrc().equals(DatapathId.of(mess[1])) &&
                    l.getSrcPort().getPortNumber() == Integer.parseInt(mess[2]) &&
                    l.getDst().equals(DatapathId.of(mess[3])) &&
                    l.getDstPort().getPortNumber() == Integer.parseInt(mess[4])) {
                LinkTimeInfo ltf = new LinkTimeInfo();
                ltf.setL(l);
                ltf.setAllTime(allTime); //
                linkTimeStatus.add(ltf);
                break;
            }

        }
    }

    public void handleEchoReply(OFEchoReply reply) {

        if (reply.getData().length <= 0)
            return;
        String[] data = new String(reply.getData()).split("<>");
        if (data.length != 2) {
            MyLog.error("length is not 2!");
            return;
        }
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
        Date currentTime = new Date();
        long Time = 0;
        try {
            Date sendTime = df.parse(data[0]);
            Time = currentTime.getTime() - sendTime.getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        //更新记录
        for (LinkTimeInfo lti : linkTimeStatus) {
            if (lti.getL().getSrc().equals(DatapathId.of(data[1])))
                lti.setCtossw(Time / 2);
            if (lti.getL().getDst().equals(DatapathId.of(data[1])))
                lti.setCtodsw(Time / 2);
            if (lti.getCtodsw() != -1 && lti.getCtossw() != -1) {
                long delay = lti.getAllTime() - lti.getCtodsw() - lti.getCtossw();
                lti.setDelay(delay >= 0 ? delay : 0);
                MyLog.info("时延：" + lti.getDelay()); //delay为最终的时延
            }

        }

    }


    //TODO
    //端口消息测丢包率
    public void handlePortStatsReply(OFPortStatsReply reply, IOFSwitchBackend aSwitch) {

        List<OFPortStatsEntry> portStatsEntries = reply.getEntries();//得到消息体
        for (OFPortStatsEntry entry : portStatsEntries) {
            /**
             * entry.getRXErrors();    //Rx:表示接收的数目 Tx：发送的数目
             * entry.getTxDropped();
             * entry.getTxErrors();
             * */
            entry.getTxPackets();//发送的包数目
            entry.getTxDropped();//丢失的包数目 相除得到发送的丢包率
            entry.getRxPackets();
            entry.getRxDropped();//相除得到接收的丢包率
        }
    }


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
    public void handleFlowStatsReply_combineWithSwitchPorts(OFFlowStatsReply reply, IOFSwitchBackend sw) {

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

    }


    /**
     *
     * 作用：用于Sampling流量采样模块
     * 思路：将匹配域中同一源地址源端口，目的地址目的端口的流合并为一个流，并分配流id，采样时根据流id识别
     * @param reply
     * @param sw
     */
    public void handleFlowStatsReply_combineWithIPAndPorts(OFFlowStatsReply reply, IOFSwitchBackend sw) {

        long byteCount;
        //String srcAndDst = null;
        long srcAndDst = 0;
        IPv4Address dstAdd,srcAdd;
        OFPort inPort,outPort=null;

        List<OFFlowStatsEntry> entries = reply.getEntries();
        Map<Long, Map<String, Number>> flowsThisTerm = new HashMap<>();
        long time = new Date().getTime();

        for (OFFlowStatsEntry e :entries) {
            byteCount = e.getByteCount().getValue();//TODO 这里需要求的是包数目
            dstAdd =e.getMatch().get(MatchField.IPV4_DST);
            srcAdd = e.getMatch().get(MatchField.IPV4_SRC);
            inPort =e.getMatch().get(MatchField.IN_PORT);
            //srcAndDst = srcAdd.getInt()+":"+dstAdd.getInt(); //键（key）用字符串形式实现，与long效果上一样，类型不一样
            srcAndDst = (srcAdd.getInt()<<16)+dstAdd.getInt();

            if(inPort==null || inPort ==OFPort.ALL) {
                continue;
            }

            if(flowsThisTerm.containsKey(srcAndDst)) {
                Map<String, Number> enums = flowsThisTerm.get(srcAndDst);
                int count = (int) enums.get("count");
                enums.put("count", count+byteCount);//TODO 这里为包数目，而不应该加比特数
                flowsThisTerm.put(srcAndDst, enums);
            } else {
                Map<String, Number> enums = new HashMap<>();
                enums.put("srcIP", srcAdd.getInt());
                enums.put("dstIP", srcAdd.getInt());
                enums.put("count", 1);
                enums.put("time", time);
                flowsThisTerm.put(srcAndDst, enums);
            }

        }

        allFlowAllTimeOfSwitch.add(flowsThisTerm);

        if(allFlowAllTimeOfSwitch.size()>MAX_LENGTH_OF_ALL_FLOW_ALL_TIME_OF_SWITCH) { //将list限制在最大长度以内
            allFlowAllTimeOfSwitch.remove(0);
        }
        //TODO --计算流经该交换机的总包数

    }


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
    public void handleFlowStatsReply2(OFFlowStatsReply reply, IOFSwitchBackend sw) {

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

    }

    //存储
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
    }

    //计算链路的最大带宽---入口带宽与出口带宽的最小值
    public long calculateMaxBand(OFSwitch fromSw, OFSwitch toSw, OFPort inPort, OFPort outPort) {

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
    }

    //计算当前带宽，输出
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

    }


    public List<Map<Long, Map<String, Number>>> getAllFlowAllTimeOfSwitch() {
        return allFlowAllTimeOfSwitch;
    }
}

class LinkDataInfo {

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


}

class LinkTimeInfo {

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


}

class MulLinkDataInfo {

    private List<LinkDataInfo> linkDataInfos;

    MulLinkDataInfo() {
        this.linkDataInfos = new ArrayList<>();
    }

    public List<LinkDataInfo> getLinkDataInfos() {
        return linkDataInfos;
    }


}


