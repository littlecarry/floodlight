package net.floodlightcontroller.Constant;

public class BaseConstant {
    public static final Integer newPacketCounterThreshold = 10;


    /*节点选择*/
    //正常采样阈值k：S--k*Q(
    public static final double toAbnormalThreshold= 1.0;
    //中枢节点最少链路数
    public static final int leastLinksForCoreNode = 3;
}
