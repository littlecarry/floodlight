package net.floodlightcontroller.QoSEvaluation;

import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import org.projectfloodlight.openflow.types.DatapathId;
import sun.rmi.runtime.Log;

import java.util.logging.Logger;

public class NetworkMeterThread  extends Thread{

    //
    protected NetworkMeter networkMeter;
    MyLog myLog;

    public NetworkMeterThread(NetworkMeter networkMeter){
        this.networkMeter = networkMeter;
    };

    public void run(){
        //一直采样 TODO 仅在一些时刻的一些节点采样
        while(true){
            try{
                //TODO 测量间隔需要是动态的，不恒定是2s
                sleep(500);
            } catch (Exception e){
                e.printStackTrace();
            }
            //clear
            NetworkStore networkStore =NetworkStore.getInstance();
            networkStore.calCurrentBand();
            networkStore.nextMeterBegin();
            //TODO

            IOFSwitchService switches = networkMeter.getSwitchService();
            //得到并遍历所有交换机ID
            for(DatapathId switchId: switches.getAllSwitchDpids()){
                IOFSwitch aSwitch = switches.getSwitch(switchId);
                if(aSwitch ==null){
                    MyLog.warn("交换机为空-sw is null");
                    continue;
                }
                //TODO
                networkMeter.getBandMeter().doBandMeter(aSwitch);
                networkMeter.getPacketLossMeter().doPacketLossMeter(aSwitch);
            }
            networkMeter.getTimeDelayMeter().doTimeDelayMeter(networkMeter);

        }
    }

}
