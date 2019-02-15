package net.floodlightcontroller.QoSEvaluation;

import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.util.AppCookie;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest.Builder;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;

/**
 * 下发请求报文的两种途径，
 * 1.OFFlowStatusRequest：针对流
 *
 * 2.OFAggregatetatusRequest：针对交换机--交换机统计之后上报所有消息
 *    tips：这个更适合我的工程实现
 *      --zigzag
 */


public class BandMeter {


    /**
     *
     * 带宽测量
     * @param aSwitch
     * 测量过程: 1.新建请求（status request）消息，
     */
    void doBandMeter(IOFSwitch aSwitch){
        Builder request = (Builder) aSwitch.getOFFactory().buildFlowStatsRequest();
        //TODO 相关配置可能需要更改 --zigzag
        //流表ID
        request.setTableId(TableId.ALL);
        //请求的出端口
        request.setOutPort(OFPort.ANY);
        //ANY表示是任意的Group，不关心
        request.setOutGroup(OFGroup.ANY);
        request.setCookie(AppCookie.makeCookie(2, 0));
        //MyLog.info("带宽测量发起请求-发送？请求");
        aSwitch.write(request.build());

    }
}
