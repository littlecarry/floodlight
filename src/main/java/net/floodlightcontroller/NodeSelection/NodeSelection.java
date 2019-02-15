package net.floodlightcontroller.NodeSelection;


import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;

import java.util.Collection;
import java.util.Map;

//TODO 在QOS和攻击评估（估计可以先设定一个值）
//返回需要采集数据的节点
public class NodeSelection implements IOFMessageListener, IFloodlightModule {
    //protected

    public static void nodeSelection(){
        //阈值-动态阈值（与QoS相关）

        //1.得到链路信息--节点有哪些

        //2.得到各个链路的阈值

        //3.
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        //TODO
        return null;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        //TODO
        return name.equals("devicemanager");
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        //TODO
        return false;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        //TODO
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        //TODO
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        //TODO
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        //TODO
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        //TODO
    }
}
