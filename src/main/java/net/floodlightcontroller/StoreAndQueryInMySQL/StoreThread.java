package net.floodlightcontroller.StoreAndQueryInMySQL;

import net.floodlightcontroller.NodeSelection.NodeSelection;
import net.floodlightcontroller.NodeSelection.NodeSelectionThread;

public class StoreThread extends Thread {


    private NodeSelection nodeSelection;

    public StoreThread(NodeSelection nodeSelection) {
        this.nodeSelection = nodeSelection;
    }

    @Override
    public void run() {
        try {
            DBUtil.connectDB();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
