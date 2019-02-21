package net.floodlightcontroller.QoSEvaluation;

import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.linkdiscovery.internal.LinkInfo;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.Map;

public class QosEvaluation {
    protected NetworkStore ns;
    protected NetworkMeter networkMeter;
    protected Map<String, Double> QosEvaluation;
    /*static final double FACTOR_OF_DELAY = 1.0/3, FACTOR_OF_THROUGH = 1.0/3, FACTOR_OF_DROPPED_PACKETS = 1.0/3;//此处应该是每个节点拥有不同的系数
    static final double LOW_THRESHOLD_OF_DELAY = 10, HIGH_THRESHOLD_OF_DELAY = 100;//时延下阈值为10ms，上阈值为100ms。
    static final double LOW_THRESHOLD_OF_THROUGH = 10, HIGH_THRESHOLD_OF_THROUGH = 100000.0/8;//通量下阈值为10字节，上阈值为100K字节。
    static final double LOW_THRESHOLD_OF_DROPPED_PACKETS = 0.01, HIGH_THRESHOLD_OF_DROPPED_PACKETS = 0.2;//丢包率下阈值为1%，上阈值为20%。
    static final double SIGMA2_OF_DELAY = 1.0, SIGMA2_OF_THROUGH = 1.0, SIGMA2_OF_DROPPED_PACKETS = 1.0;*/

    //顺序分别是delay,through,droppedPackets.
    static final double[] FACTORS ={1.0/3, 1.0/3, 1.0/3};
    static final double[] LOW_THRESHOLDS ={10, 10, 0.01}; //注释见被注释的常量
    static final double[] HIGH_THRESHOLD = {100, 100000.0/8, 0.2};
    static final double[] SIGMA2 = {1.0, 1.0, 1.0};

    //static final double UNIFORM_FACTOR_OF_DELAY = 1.0/3, UNIFORM_FACTOR_OF_THROUGH = 1.0/3, UNIFORM_FACTOR_OF_DROPPED_PACKETS = 1.0/3;//规整到[0,1]

    static final double s = 1;

    QosEvaluation() {
        ns = NetworkStore.getInstance();
        networkMeter = new NetworkMeter();
        QosEvaluation = ns.getQosOfLinks();

    }

    void calQosEvaluation() {

        Map<Link, LinkInfo> links = networkMeter.getLinkService().getLinks();
        if(links.isEmpty() || links.size()==0) {
            MyLog.warn("calQosEvaluation-QosEvaluation error: links not found.");
            return;
        }

        Map<String, Double> delayOfLinks = ns.getDelayOfLinks(); ////key是srcSwitchAndPortAndDstSwitchAndPort
        Map<String, Long> throughOfLinks = ns.getThroughOfLinks(); //key是dstSwitchAndPort
        Map<String, Double> droppedPacketsOfLinks = ns.getDroppedPacketsOfLinks(); //key是srcSwitchAndPort
        double delay, droppedPackets, through, qos;

        for(Link link : links.keySet()) {
            DatapathId srcSwitch = link.getSrc();
            OFPort inPort = link.getSrcPort();
            DatapathId dstSwitch = link.getDst();
            OFPort outPort = link.getDstPort();
            String srcSwitchAndPortAndDstSwitchAndPort = srcSwitch.getLong()+":"+inPort.getPortNumber()
                                +":"+dstSwitch.getLong()+":"+outPort.getPortNumber();
            String srcSwitchAndPort = srcSwitch.getLong()+":"+inPort.getPortNumber();
            String dstSwitchAndPort = dstSwitch.getLong()+":"+outPort.getPortNumber();

            delay =calSubEvaluation(delayOfLinks, srcSwitchAndPortAndDstSwitchAndPort, 0);
            through =calSubEvaluation(throughOfLinks, dstSwitchAndPort, 1);
            droppedPackets =calSubEvaluation(droppedPacketsOfLinks, srcSwitchAndPort, 2);

            qos = FACTORS[0]*delay+FACTORS[1]*through+FACTORS[2]*droppedPackets;
            QosEvaluation.put(srcSwitchAndPortAndDstSwitchAndPort, qos);
        }

    }

    /**
     * 计算子项的评价值（时延，通量，丢包）
     * @param map
     * @param key
     * @param i
     * @param <T>
     * @return
     */
    <T extends Number> double calSubEvaluation (Map<String, T> map, String key, int i)  { //i=0,1,2 分别代表delay,through,droppedPackets.
        double val ;
        if(map.containsKey(key)) {
            val = (double) map.get(key);
        } else {
            val = 0.0;
        }

        if(val<LOW_THRESHOLDS[i]) { //最佳
            return 1.0;
        } else if(val>HIGH_THRESHOLD[i]) {
            return 0.0;
        }
        //以下LOW<val<HIGH
        double result = Math.exp(-1.0*(val-LOW_THRESHOLDS[i])*(val-LOW_THRESHOLDS[i])/(2*SIGMA2[i]*HIGH_THRESHOLD[i]*HIGH_THRESHOLD[i]));

        return result;
    }

}
