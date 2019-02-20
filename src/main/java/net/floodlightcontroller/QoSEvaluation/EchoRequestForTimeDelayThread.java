package net.floodlightcontroller.QoSEvaluation;

import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import org.projectfloodlight.openflow.types.DatapathId;

public class EchoRequestForTimeDelayThread extends Thread {

    protected NetworkMeter networkMeter;
    MyLog myLog;

    public EchoRequestForTimeDelayThread(NetworkMeter networkMeter){
        this.networkMeter = networkMeter;
    };


    @Override
    public void run() {

        boolean isTheFirstTime = true;

        while(true) {

            if(!isTheFirstTime) {
                try {
                    sleep(200);
                } catch (Exception e) {
                    continue;
                }
            }

            try{
                NetworkStore networkStore = NetworkStore.getInstance();
                //得到网络交换机信息
                IOFSwitchService switches = networkMeter.getSwitchService();
                for(DatapathId switchId : switches.getAllSwitchDpids()) {
                    IOFSwitch aSwitch = switches.getSwitch(switchId);
                    if(aSwitch ==null){
                        MyLog.warn("交换机为空-sw is null");
                        continue;
                    }
                    networkMeter.getTimeDelayMeter().sendEchoRequest(aSwitch);
                }

            } catch (Exception e) {
                MyLog.error("EchoRequestForTimeDelayThread Error: 时延测量发送echo消息出错");
            }
            isTheFirstTime = false;
        }


    }
}
