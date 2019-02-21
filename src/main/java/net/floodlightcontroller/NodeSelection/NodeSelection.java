package net.floodlightcontroller.NodeSelection;


import net.floodlightcontroller.Constant.BaseConstant;
import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.MyUtils.MyUtils;
import net.floodlightcontroller.QoSEvaluation.NetworkStore;
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
    protected NetworkStore ns;

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
        ns = NetworkStore.getInstance();
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
            MyLog.info("nodeSelection info : doing node selection.");
            List<Link> blockedLinks = new ArrayList<>();
            List<IOFSwitch> existingSwitches = new ArrayList<>();

            Map<IOFSwitch, SwitchOfDegree> switchMap = new HashMap<>();
            Map<IOFSwitch, List<IOFSwitch>> inToOutSwitchMapping = new HashMap<>();


            Set<SwitchOfDegree> curSwitches = new HashSet<>();
            Set<IOFSwitch> curSw = new HashSet<>();
            //List<SwitchOfDegree> curToSwitches =new ArrayList<>();
            Map<IOFSwitch, Integer> switchCounterMap = new HashMap<>();
            for (Link l : links.keySet()) {
                IOFSwitch srcSwitch = this.getSwitchService().getSwitch(l.getSrc());
                IOFSwitch dstSwitch = this.getSwitchService().getSwitch(l.getDst());
                OFPort inPort = l.getSrcPort();
                OFPort outPort = l.getDstPort();
                String srcSwitchAndPortAndDstSwitchAndPort = srcSwitch.getId().getLong()+":"+inPort.getPortNumber()
                        +":"+dstSwitch.getId().getLong()+":"+outPort.getPortNumber();
                //
                /*double s1 = AdaptiveParamers.SecurityOfNodes.get(fromSw); //事实上需要每个节点一个安全值
                double s2 = AdaptiveParamers.SecurityOfNodes.get(toSw);*/
                double s1 = ns.getSecurity(); //目前安全性取值为固定值
                double s2 = ns.getSecurity();
                double q = ns.getQosOfLinks().get(srcSwitchAndPortAndDstSwitchAndPort);//注意，该QosToAbnormalThreshold是双向的，同一条链路的两向QoS不同
                utils.mapCounter(switchCounterMap, srcSwitch, 1); //+1 一个节点的入度和出度其实是相同的，因为对于同一条链路每个节点既是入度节点又是出度节点
                utils.mapCounter(switchCounterMap, dstSwitch, 1);
                if (Math.sqrt(s1 * s2) * BaseConstant.toAbnormalThreshold < q) {//此处为Abnormal状态；正常模式什么也不做
                    isAbnormal = true;
                    blockedLinks.add(l);
                    //出入节点对应关系-每个入节点对应的出节点的集合（映射）
                    if (inToOutSwitchMapping.isEmpty() || !inToOutSwitchMapping.containsKey(srcSwitch)) {
                        List<IOFSwitch> list = new ArrayList<>();
                        list.add(dstSwitch);
                        inToOutSwitchMapping.put(srcSwitch, list);
                    } else {
                        List<IOFSwitch> list = inToOutSwitchMapping.get(srcSwitch);
                        list.add(dstSwitch);//允许重复，重复n次为n度
                        inToOutSwitchMapping.put(srcSwitch, list);
                    }
                    //节点出入度表
                    if (switchMap.isEmpty()) {
                        switchMap.put(srcSwitch, new SwitchOfDegree(srcSwitch, 0, 1));
                        switchMap.put(dstSwitch, new SwitchOfDegree(dstSwitch, 1, 0));
                    } else {

                        if (switchMap.containsKey(srcSwitch)) {
                            SwitchOfDegree sw = switchMap.get(srcSwitch);
                            sw.outDegree++;
                            switchMap.put(srcSwitch, sw);
                        } else {
                            switchMap.put(srcSwitch, new SwitchOfDegree(srcSwitch, 0, 1));
                        }
                        if (switchMap.containsKey(dstSwitch)) {
                            SwitchOfDegree sw = switchMap.get(dstSwitch);
                            sw.inDegree++;
                            switchMap.put(dstSwitch, sw);
                        } else {
                            switchMap.put(dstSwitch, new SwitchOfDegree(dstSwitch, 1, 0));
                        }
                    }

                }

            }


            if (isAbnormal) {
                List<SwitchOfDegree> switchList = new ArrayList<>(switchMap.values()); //得到所有交换机的SwitchOfDegree对象
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
                                indegreeAdjust(sw, inToOutSwitchMapping, switchMap); //switchMap中一定包含sw。理论上inToOutSwitchMapping中也一定包含sw
                            } else {
                                MyLog.error("Abnormal Pattern in Node Selection: sw is not contained in inToOutSwitchMapping.");
                            }
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
                            indegreeAdjust(maxSw, inToOutSwitchMapping, switchMap);
                        } else {
                            MyLog.warn("error to find 汇聚节点");
                        }

                        if (!resultSet.isEmpty()) { //可能存在源节点即是环的拓扑
                            isSourceBlockedNode = false;
                        }
                        Collections.sort(switchList, new switchComparator());
                        firstSwitch = switchList.get(0); //得到入度最小的节点
                    }
                }

                if (switchMap.isEmpty()) {
                    AdaptiveParamers.coreSwitches.clear();
                    AdaptiveParamers.coreSwitches.addAll(resultSet);
                    return AdaptiveParamers.coreSwitches;
                } else {
                    MyLog.error("There are some mistakes in Node Selection sothat cannot find the result");
                }

            } else { //normal模式
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

    /**
     *
     * 删除节点并修改相关节点的入度（其实对于中枢节点，其相关节点的出度也需要修改，但是本实验只关心入度）
     * @param sw
     * @param inToOutSwitchMapping
     * @param switchMap
     */
    void indegreeAdjust(IOFSwitch sw, Map<IOFSwitch, List<IOFSwitch>> inToOutSwitchMapping,  Map<IOFSwitch, SwitchOfDegree> switchMap) {
        List<IOFSwitch> list = inToOutSwitchMapping.get(sw);
        for (IOFSwitch iofSwitch : list) {
            if (switchMap.containsKey(iofSwitch) && switchMap.get(iofSwitch).inDegree > 0) {//保证操作是正确的，非源节点（汇聚节点）可能导致操作异常
                SwitchOfDegree switchOfDegree = switchMap.get(iofSwitch);
                switchOfDegree.inDegree--;
                switchMap.put(iofSwitch, switchOfDegree);
            }
        }
        switchMap.remove(sw);
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
