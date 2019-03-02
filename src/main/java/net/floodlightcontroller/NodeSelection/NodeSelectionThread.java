package net.floodlightcontroller.NodeSelection;

import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.QoSEvaluation.NetworkMeter;
import net.floodlightcontroller.QoSEvaluation.QosEvaluation;
import net.floodlightcontroller.StoreAndQueryInMySQL.DBUtil;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.sampling.PacketInSampling;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NodeSelectionThread extends Thread {
    NodeSelection nodeSelection;
    ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    //public Set<Long> switchIds = null;
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
                //cachedThreadPool.shutdown();
                try {   //流出等待时间，让线程池中的线程全部停止
                    sleep(6);

                } catch (Exception e) {
                    MyLog.info("qosEvaluation-NormalActionThread info: sleep too short, may cause history cachedThreadPool still running");
                }
                if(switches==null || switches.isEmpty()) {
                    MyLog.warn("qosEvaluation-NormalActionThread warn: cannot found sampled node from NodeSelection module");
                } else {
                    for(IOFSwitch iofSwitch : switches) {
                        //switchIds.add(iofSwitch.getId().getLong());
                        //System.out.println("----NodeSelection----:"+iofSwitch.getId().getLong()+"   in the turn "+(i++));
                        long switchId = iofSwitch.getId().getLong();
                        cachedThreadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                PacketInSampling packetInSampling = new PacketInSampling();
                                long time = new Date().getTime();
                                boolean flag = true;
                                long curTime;
                                while(flag) {
                                    packetInSampling.sampling(switchId);
                                    curTime = new Date().getTime();
                                    try {   //流出等待时间，让线程池中的线程全部停止
                                        sleep(6);

                                    } catch (Exception e) {
                                    }
                                    if(curTime-time>200) {
                                        flag = false;
                                    }
                                }
                            }
                        });

                    }
                }


                //线程池


            } catch (Exception e) {
                e.printStackTrace();
                MyLog.warn("qosEvaluation-NormalActionThread error");
            }

        } //end while

    } //end run

    /*public Set<Long> getSwitchIds() {
        return switchIds;
    }*/
}