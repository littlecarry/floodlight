package net.floodlightcontroller.NodeSelection;


import net.floodlightcontroller.Constant.BaseConstant;
import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.MyUtils.MyUtils;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.linkdiscovery.internal.LinkInfo;
import org.apache.commons.lang.StringUtils;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.*;
import java.util.logging.Logger;
import net.floodlightcontroller.AdaptiveParamers;

//TODO 在QOS和攻击评估（估计可以先设定一个值）
//返回需要采集数据的节点
public class NodeSelection implements IOFMessageListener, IFloodlightModule {
    protected IFloodlightProviderService floodlightProvider;
    protected static Logger logger;

    //交换机实例对象
    protected IOFSwitchService switchService;
    protected ILinkDiscoveryService linkService;


    //工具实例
    protected MyUtils utils;

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        //TODO
        System.out.println("---------------packetIn------"+this.getClass().getSimpleName());
        return net.floodlightcontroller.core.IListener.Command.CONTINUE;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        //TODO
        //return name.equals("devicemanager");
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        //TODO
        return false;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        //拿到实例化的对象
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        //得到链路信息
        switchService = context.getServiceImpl(IOFSwitchService.class);
        linkService = context.getServiceImpl(ILinkDiscoveryService.class);
        utils = new MyUtils();
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        //添加监听器，监听packet_in消息
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

        //节点选择
        Set<IOFSwitch> switches = this.nodeSelection();

    }


    public Set<IOFSwitch> nodeSelection() {

        ILinkDiscoveryService linkService = this.getLinkService();
        Map<Link, LinkInfo> links = linkService.getLinks(); //该links是有向的，同一的两个方向记为两条链路

        boolean isAbnormal = false;
        if (links != null && !links.isEmpty()) {
            List<Link> blockedLinks = new ArrayList<>();
            List<IOFSwitch> existingSwitches = new ArrayList<>();

            Map<IOFSwitch, SwitchOfDegree> switchMap = new HashMap<>();
            Map<IOFSwitch, List<IOFSwitch>> inToOutSwitchMapping = new HashMap<>();


            Set<SwitchOfDegree> curSwitches = new HashSet<>();
            Set<IOFSwitch> curSw = new HashSet<>();
            //List<SwitchOfDegree> curToSwitches =new ArrayList<>();
            Map<IOFSwitch, Integer> switchCounterMap = new HashMap<>();
            for (Link l : links.keySet()) {
                IOFSwitch fromSw = this.getSwitchService().getSwitch(l.getSrc());
                IOFSwitch toSw = this.getSwitchService().getSwitch(l.getDst());

                //
                double s1 = AdaptiveParamers.SecurityOfNodes.get(fromSw);
                double s2 = AdaptiveParamers.SecurityOfNodes.get(toSw);
                double q = AdaptiveParamers.QoSOfLinks.get(l);//注意，该QoStoAbnormalThreshold是双向的，同一条链路的两向QoS不同
                utils.mapCounter(switchCounterMap, fromSw, 0);
                utils.mapCounter(switchCounterMap, toSw, 0);
                if (Math.sqrt(s1 * s2) * BaseConstant.toAbnormalThreshold < q) {//此处为Abnormal状态；正常模式什么也不做
                    isAbnormal = true;
                    blockedLinks.add(l);
                    //出入节点对应关系
                    if (inToOutSwitchMapping.isEmpty() || !inToOutSwitchMapping.containsKey(fromSw)) {
                        List<IOFSwitch> list = new ArrayList<>();
                        list.add(toSw);
                        inToOutSwitchMapping.put(fromSw, list);
                    } else {
                        List<IOFSwitch> list = inToOutSwitchMapping.get(fromSw);
                        list.add(toSw);//允许重复，重复n次为n度
                        inToOutSwitchMapping.put(fromSw, list);
                    }
                    //节点出入度表
                    if (switchMap.isEmpty()) {
                        switchMap.put(fromSw, new SwitchOfDegree(fromSw, 0, 1));
                        switchMap.put(toSw, new SwitchOfDegree(toSw, 1, 0));
                    } else {

                        if (switchMap.containsKey(fromSw)) {
                            SwitchOfDegree sw = switchMap.get(fromSw);
                            sw.outDegree++;
                            switchMap.put(fromSw, sw);
                        } else {
                            switchMap.put(fromSw, new SwitchOfDegree(fromSw, 0, 1));
                        }
                        if (switchMap.containsKey(toSw)) {
                            SwitchOfDegree sw = switchMap.get(toSw);
                            sw.inDegree++;
                            switchMap.put(toSw, sw);
                        } else {
                            switchMap.put(toSw, new SwitchOfDegree(toSw, 1, 0));
                        }
                    }

                }

            }


            if (isAbnormal) {
                List<SwitchOfDegree> switchList = new ArrayList<>(switchMap.values());
                Set<IOFSwitch> resultSet = new HashSet<>();
                if (switchList.isEmpty()) {
                    MyLog.error("Abnormal Pattern in Node Selection, but switchList is null");
                    return AdaptiveParamers.coreSwitches;
                }
                Collections.sort(switchList, new switchComparator());
                SwitchOfDegree firstSwitch = switchList.get(0); //得到入度最小的节点
                Boolean isSourceBlockedNode = true;

                while (!switchList.isEmpty()) {
                    if (firstSwitch.inDegree == 0) {
                        List<SwitchOfDegree> noIndegreeSwitches = new ArrayList<>();
                        for (SwitchOfDegree sw : switchList) {
                            if (sw.inDegree == 0)
                                noIndegreeSwitches.add(sw);
                            else
                                break;
                        }
                        for (int i = 0; i < noIndegreeSwitches.size(); i++) {
                            switchList.remove(0);//始终移除第一个节点
                            IOFSwitch sw = noIndegreeSwitches.get(i).iofSwitch;
                            //只添加一次：添加源节点
                            if (isSourceBlockedNode) {
                                resultSet.add(sw);
                            }

                            if (inToOutSwitchMapping.containsKey(sw)) {
                                List<IOFSwitch> list = inToOutSwitchMapping.get(sw);
                                for (IOFSwitch iofSwitch : list) {
                                    if (switchMap.containsKey(iofSwitch) && switchMap.get(iofSwitch).inDegree > 0) {//保证操作是正确的，非源节点（汇聚节点）可能导致操作异常
                                        SwitchOfDegree switchOfDegree = switchMap.get(iofSwitch);
                                        switchOfDegree.inDegree--;
                                        switchMap.put(iofSwitch, switchOfDegree);
                                    }
                                }
                            }
                            switchMap.remove(sw);
                        }

                        if (!resultSet.isEmpty()) {
                            isSourceBlockedNode = false;
                        }
                        Collections.sort(switchList, new switchComparator());
                        if (switchList.isEmpty()) {
                            //MyLog.warn("Abnormal Pattern in Node Selection, but switchList is null After several loops");
                            AdaptiveParamers.coreSwitches.clear();
                            AdaptiveParamers.coreSwitches.addAll(resultSet);
                            return AdaptiveParamers.coreSwitches;
                        }
                        firstSwitch = switchList.get(0); //得到入度最小的节点
                    } else { ////非空存在环
                        int maxVal = -1;
                        IOFSwitch maxSw = null;
                        for (IOFSwitch sw : switchCounterMap.keySet()) {
                            if (switchCounterMap.get(sw) > maxVal) {
                                maxSw = sw;
                                maxVal = switchCounterMap.get(sw);
                            }
                        }
                        if (maxSw != null) {
                            switchCounterMap.remove(maxSw);
                            resultSet.add(maxSw);
                            List<IOFSwitch> list = inToOutSwitchMapping.get(maxSw);
                            for (IOFSwitch iofSwitch : list) {
                                if (switchMap.containsKey(iofSwitch) && switchMap.get(iofSwitch).inDegree > 0) {//保证操作是正确的，非源节点（汇聚节点）可能导致操作异常
                                    SwitchOfDegree switchOfDegree = switchMap.get(iofSwitch);
                                    switchOfDegree.inDegree--;
                                    switchMap.put(iofSwitch, switchOfDegree);
                                }
                            }
                            switchMap.remove(maxSw);
                        } else {
                            MyLog.warn("error to find 汇聚节点");
                        }
                    }
                }

                if (switchMap.isEmpty()) {
                    AdaptiveParamers.coreSwitches.clear();
                    AdaptiveParamers.coreSwitches.addAll(resultSet);
                    return AdaptiveParamers.coreSwitches;
                } else {
                    MyLog.error("There are some mistakes in Node Selection sothat cannot find the result");
                }

            } else {
                //找到中枢节点
                AdaptiveParamers.coreSwitches.clear();
                for (IOFSwitch sw : switchCounterMap.keySet()) {
                    if (switchCounterMap.get(sw) >= 6) {//每条边计数两次，其实为3
                        AdaptiveParamers.coreSwitches.add(sw);
                    }
                }
                return AdaptiveParamers.coreSwitches;
            }

        } else {
            MyLog.error("nodeSelection error : links is null");
        }


        return AdaptiveParamers.coreSwitches;
    }



    //get方法
    public IFloodlightProviderService getFloodlightProvider() {
        return floodlightProvider;
    }

    public IOFSwitchService getSwitchService() {
        return switchService;
    }

    public ILinkDiscoveryService getLinkService() {
        return linkService;
    }
}


class SwitchOfDegree {
    IOFSwitch iofSwitch;
    int inDegree;
    int outDegree;
    SwitchOfDegree(){
        inDegree=0;
        outDegree=0;
    }
    SwitchOfDegree(IOFSwitch iofSwitch){
        this.iofSwitch = iofSwitch;
        inDegree=0;
        outDegree=0;
    }
    SwitchOfDegree(IOFSwitch iofSwitch, int inDegree, int outDegree){
        this.iofSwitch = iofSwitch;
        this.inDegree= inDegree;
        this.outDegree= outDegree;
    }

    @Override
    public boolean equals(Object otherSwitchOfDegree){
        if(otherSwitchOfDegree instanceof SwitchOfDegree)
            return false;
        if(otherSwitchOfDegree==this)
            return true;
        if(this.iofSwitch.equals(((SwitchOfDegree)otherSwitchOfDegree).iofSwitch))
            return true;
        return false;
    }

    boolean has(IOFSwitch iofSwitch){
        if(this.iofSwitch==iofSwitch)
            return true;
        return false;
    }
}



class switchComparator implements Comparator<SwitchOfDegree> {

    @Override
    public int compare(SwitchOfDegree o1, SwitchOfDegree o2) {//按入度排序
        if(o1.inDegree>o2.inDegree)
            return 1;
        if(o1.inDegree<o2.inDegree)
            return -1;
        return 0;
    }

}
