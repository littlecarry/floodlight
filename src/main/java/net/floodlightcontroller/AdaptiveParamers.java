package net.floodlightcontroller;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.linkdiscovery.Link;

import java.util.*;

public interface AdaptiveParamers {


    /*NodeSelection*/
    //核心交换机（中枢节点）集合
    public static Set<IOFSwitch> coreSwitches = new HashSet<>();
    //所有节点安全性，QoS
    Map<IOFSwitch, Double> SecurityOfNodes = new HashMap<>();
    Map<IOFSwitch, Double> QoSOfNodes =new HashMap<>();
    //所有链路安全性，QoS
    Map<Link, Double> SecurityOfLinks = new HashMap<>();
    Map<Link, Double> QoSOfLinks =new HashMap<>();





}
