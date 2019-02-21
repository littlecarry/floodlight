package net.floodlightcontroller.QoSEvaluation;

import net.floodlightcontroller.MyLog;

public class NormalActionThread extends Thread {
    NetworkMeter networkMeter;
    NormalActionThread(NetworkMeter networkMeter) {
        this.networkMeter = networkMeter;
    }

    @Override
    public void run() {

        while (true) {
            try {
                sleep(6);

            } catch (Exception e) {
                continue;
            }

            try {
                QosEvaluation qosEvaluation = new QosEvaluation(networkMeter);
                qosEvaluation.calQosEvaluation();
            } catch (Exception e) {
                e.printStackTrace();
                MyLog.warn("qosEvaluation-NormalActionThread error");
            }

        }

    }
}
