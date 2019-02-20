package net.floodlightcontroller.QoSEvaluation;

import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.OFSwitch;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.linkdiscovery.internal.LinkInfo;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import sun.rmi.runtime.Log;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class NetworkMeterThread  extends Thread{

    //
    protected NetworkMeter networkMeter;
    MyLog myLog;

    public NetworkMeterThread(NetworkMeter networkMeter){
        this.networkMeter = networkMeter;
    };


    //该run方法发送所有的信息探测包
    public void run() {

        //一直采样 TODO 仅在一些时刻的一些节点采样
        while (true) {
            try {
                //测量间隔需要是动态的，不恒定是2s
                sleep(5); // TODO 需与采样周期（目前34ms）保持一定关系
            } catch (Exception e) {
                continue;
            }

            try {
                NetworkStore networkStore = NetworkStore.getInstance();

                //得到网络拓扑信息（交换机和链路）
                IOFSwitchService switches = networkMeter.getSwitchService();
                Map<Link, LinkInfo> links = networkMeter.getLinkService().getLinks(); //该links是有向的，同一的两个方向记为两条链路

                //发送流统计消息请求以交换机为单位，接收处理消息时按链路（链路源地址+源端口）为单位处理
                for(DatapathId switchId : switches.getAllSwitchDpids()) {//统计信息请求直接发向指定交换机而针对链路（避免多条链路来自同一源交换机造成流量统计重复）
                    IOFSwitch aSwitch = switches.getSwitch(switchId);
                    if(aSwitch ==null){
                        MyLog.warn("交换机为空-sw is null");
                        continue;
                    }
                    networkMeter.getBandMeter().doBandMeter(aSwitch);
                }

                if(links==null||links.isEmpty()) {
                    MyLog.warn("there are no links in NetworkMeterThread");
                }
                for (Link link : links.keySet()) {
                    IOFSwitch fromSw = switches.getSwitch(link.getSrc());
                    IOFSwitch toSw = switches.getSwitch(link.getDst());
                    OFPort inPort = link.getSrcPort();
                    OFPort outPort = link.getDstPort();

                    //链路时延探测包
                    networkMeter.getTimeDelayMeter().doTimeDelayMeterByLinks(fromSw, toSw ,inPort ,outPort);
                    //链路丢包率探测包（针对端口的port消息）
                    networkMeter.getPacketLossMeter().doPacketLossMeterForLink(fromSw, inPort);
                    //networkMeter.getBandMeter().doBandMeter();

                }


            } catch (Exception e) {
                e.printStackTrace();
                MyLog.error("NetworkMeterThread Error");
            }

        }
    }






    /*
    //该run方法测量的拓扑为一个控制器两个交换机出来的简单拓扑
    public void run(){
        //一直采样
        while(true){
            try{

                sleep(500);
            } catch (Exception e){
                e.printStackTrace();
            }
            //clear
            NetworkStore networkStore =NetworkStore.getInstance();
            networkStore.calCurrentBand();
            networkStore.nextMeterBegin();

            IOFSwitchService switches = networkMeter.getSwitchService();
            //得到并遍历所有交换机ID
            for(DatapathId switchId: switches.getAllSwitchDpids()){
                IOFSwitch aSwitch = switches.getSwitch(switchId);
                if(aSwitch ==null){
                    MyLog.warn("交换机为空-sw is null");
                    continue;
                }
                 //针对每个交换机计算带宽和丢包
                networkMeter.getBandMeter().doBandMeter(aSwitch);
                networkMeter.getPacketLossMeter().doPacketLossMeter(aSwitch);
            }
            //针对链路计算时延
            networkMeter.getTimeDelayMeter().doTimeDelayMeter(networkMeter);

        }
    }*/

}
