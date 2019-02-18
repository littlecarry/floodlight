package net.floodlightcontroller.QoSEvaluation;

import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.packet.Ethernet;
import org.projectfloodlight.openflow.protocol.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

public class NetworkMeter implements IOFMessageListener, IFloodlightModule {


    protected IFloodlightProviderService floodlightProvider;
    protected static Logger logger;

    //
    protected NetworkMeterThread networkMeterThread;
    //交换机实例对象
    protected IOFSwitchService switchService;
    protected ILinkDiscoveryService linkService;
    //具体实现单独测量的类
    BandMeter bandMeter;
    PacketLossMeter packetLossMeter;
    TimeDelayMeter timeDelayMeter;

    public NetworkMeterThread getNmt() {
        return networkMeterThread;
    }

    public BandMeter getBandMeter() {
        return bandMeter;
    }

    public PacketLossMeter getPacketLossMeter() {
        return packetLossMeter;
    }

    public TimeDelayMeter getTimeDelayMeter() {
        return timeDelayMeter;
    }


    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        //System.out.println("模块可以监听到消息- In Recieve");
        switch(msg.getType()){
            case PACKET_IN:
                //bcStore:相当于队列
                //所有收到的消息都会在controller.java中被打成以太帧存入bctore中，因此可以从bctore中取出二层帧
                Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
                if(timeDelayMeter.isDoingTimeDelay(eth)){//筛选packetin消息，仅选择用于时延测量的packetIn
                    NetworkStore networkStore = NetworkStore.getInstance();
                    //计算时延
                    networkStore.handlePacketIn(eth.getPayload(), linkService);
                }
                break;
        }
        return net.floodlightcontroller.core.IListener.Command.CONTINUE;
    }

    @Override
    public String getName() {
        //--zigzag 得到模块名称
        return this.getClass().getSimpleName();
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        // TODO --声明前置模块
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        // --声明后置名模快
        //对packetin消息的处理排在linkdiscovery前(所以是第一个处理packetin消息的模块)
        return type.equals(OFType.PACKET_IN)&&(name.equals("linkdiscovery"));
        //return false;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return null;
    }

    //声明依赖关系，将该模块与原模块相关联，在模块加载时通过该函数告知模块加载器（module loader）加载该模块
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        return l;
    }

    //以下为最重要的两个方法
    /**
     *初始化方法
     * @param context
     * @throws FloodlightModuleException
     */
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        //拿到实例化的对象
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);

        switchService = context.getServiceImpl(IOFSwitchService.class);
        linkService = context.getServiceImpl(ILinkDiscoveryService.class);
        //初始化测量所用的类对象
        bandMeter = new BandMeter();
        packetLossMeter =new PacketLossMeter();
        timeDelayMeter = new TimeDelayMeter();
        //初始化线程
        networkMeterThread = new NetworkMeterThread(this);
    }

    /**
     *主要功能执行方法
     * @param context
     * @throws FloodlightModuleException
     */
    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        //添加监听器，监听packet_in消息
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

        //启动线程
        networkMeterThread.start();
    }


    //得到所有交换机
    public IOFSwitchService getSwitchService(){ return switchService;}

    /**flowstatsreply的一个中继
     *
     * @param reply
     */
    public static void handleFlowStatsReply(OFFlowStatsReply reply, IOFSwitchBackend sw){
        NetworkStore networkStore = NetworkStore.getInstance();
        networkStore.handleFlowStatsReply_combineWithSwitchPorts(reply, sw);
    }


    /**flowstatsreply的一个中继
     *
     * @param reply
     */
    public static void handleFlowStatsReplyForSampling(OFFlowStatsReply reply, IOFSwitchBackend sw){
        NetworkStore networkStore = NetworkStore.getInstance();
        networkStore.handleFlowStatsReply_combineWithIPAndPorts(reply, sw);
    }

    /**portstatsreply的一个中继
     *
     * @param reply
     */
    public static void handlePortStatsReply(OFPortStatsReply reply, IOFSwitchBackend sw){
        NetworkStore networkStore = NetworkStore.getInstance();
        networkStore.handlePortStatsReply(reply, sw);
    }

    /**echoreply的一个中继
     *
     * @param reply
     */
    public static void handleEchoReply(OFEchoReply reply){
        NetworkStore networkStore = NetworkStore.getInstance();
        networkStore.handleEchoReply(reply);
    }

    public ILinkDiscoveryService getLinkService() {
        return linkService;
    }
}
