package net.floodlightcontroller.StoreAndQueryInMySQL;


import java.util.Date;

/**refer to CSDN: https://blog.csdn.net/u011024652/article/details/51753481*/

public class PacketInfoVO {
    private int id;
    private long time;
    private int srcAdd;
    private int dstAdd;
    private int inPort;
    private int outPort;
    private long srcMac;
    private long dstMac;
    private int byteCount;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public int getSrcAdd() {
        return srcAdd;
    }

    public void setSrcAdd(int srcAdd) {
        this.srcAdd = srcAdd;
    }

    public int getDstAdd() {
        return dstAdd;
    }

    public void setDstAdd(int dstAdd) {
        this.dstAdd = dstAdd;
    }

    public int getInPort() {
        return inPort;
    }

    public void setInPort(int inPort) {
        this.inPort = inPort;
    }

    public int getOutPort() {
        return outPort;
    }

    public void setOutPort(int outPort) {
        this.outPort = outPort;
    }

    public long getSrcMac() {
        return srcMac;
    }

    public void setSrcMac(long srcMac) {
        this.srcMac = srcMac;
    }

    public long getDstMac() {
        return dstMac;
    }

    public void setDstMac(long dstMac) {
        this.dstMac = dstMac;
    }

    public int getByteCount() {
        return byteCount;
    }

    public void setByteCount(int byteCount) {
        this.byteCount = byteCount;
    }

    @Override
    public String toString() {
        return "PacketInfoVO [id=" + id + ", time=" + time + ", srcAdd=" + srcAdd
                + ", dstAdd=" + dstAdd + ", inPort=" + inPort + ", srcMac=" + srcMac+ ", dstMac=" + dstMac+ ", byteCount=" + byteCount+"]";
    }

}

