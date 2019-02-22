package net.floodlightcontroller.NodeSelection;

import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.QoSEvaluation.NetworkMeter;
import net.floodlightcontroller.QoSEvaluation.QosEvaluation;
import net.floodlightcontroller.core.IOFSwitch;

import java.util.Set;

public class NodeSelectionThread extends Thread {
    NodeSelection nodeSelection;
    NodeSelectionThread(NodeSelection nodeSelection) {
        this.nodeSelection = nodeSelection;
    }

    @Override
    public void run() {
        int i=0;
        while (true) {
            try {
                sleep(6);

            } catch (Exception e) {
                continue;
            }

            try {
                //节点选择
                Set<IOFSwitch> switches = nodeSelection.nodeSelection();
                //System.out.println("----NodeSelection----:   12345");

                /*for(IOFSwitch iofSwitch : switches) {
                    System.out.println("----NodeSelection----:"+iofSwitch.getId().getLong()+"   in the turn "+(i++));
                }*/
            } catch (Exception e) {
                e.printStackTrace();
                MyLog.warn("qosEvaluation-NormalActionThread error");
            }

        }

    }
}