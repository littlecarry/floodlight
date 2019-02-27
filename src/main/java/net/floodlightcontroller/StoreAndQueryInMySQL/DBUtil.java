package net.floodlightcontroller.StoreAndQueryInMySQL;


import net.floodlightcontroller.MyLog;
import net.floodlightcontroller.sampling.PacketInSampling;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.Thread.sleep;

public class DBUtil {

    private static final String URI = "jdbc:mysql://127.0.0.1:3306/DataCollection?"
            + "user=root&password=mysql&useUnicode=true&characterEncoding=UTF-8";

    private static final String DRIVER = "com.mysql.jdbc.Driver";

    private static final String sql = "INSERT INTO packet_info(combine_id, switch_id, src_IP, dst_IP, src_mac, dst_mac, byte_count, protocol_type) "
                                     + "  VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
    /*private static final String sql = "INSERT INTO packet_info(id, switchId, srcAdd, dstAdd, srcMac, dstMac, byteCount, protocolType) "
            + "  VALUES(?, ?, ?, ?, ?, ?,?,?)"; */
            //"INSERT INTO tbl_user_info(user_name, age, sex, create_dt) "
    //				+ " VALUES(?, ?, ?, ?)"

    protected static ConcurrentLinkedQueue<Map<String, Object>> sampledPackets = PacketInSampling.sampledPackets;

    public static void connectDB() throws Exception {

        //while (true) {
            //MyLog.info("connectDB--DBUtil----before packets storing: 12345");
            boolean isWake = false;
            while(!isWake) {
                try {
                    sleep(10);
                } catch (Exception e) {
                    continue;
                }
                if(sampledPackets.size()>=100) {
                    isWake = true;
                }
                MyLog.info("connectDB--DBUtil----before packets storing: "+sampledPackets.size());
            }

            List<Map<String, Object>> list = new ArrayList<>();
            int size = sampledPackets.size();
            while(size--!=0) {
                list.add(sampledPackets.poll());
            }
            //1、加载数据库驱动
            Class.forName(DRIVER);
            //2、获取数据库连接
            Connection conn = DriverManager.getConnection(URI);
            conn.setAutoCommit(false); //关闭数据库自动提交
            //3.使用Connection来创建一个Statement对象
            PreparedStatement preparedStatement = conn.prepareStatement(sql);

            try {
                for(int i=0; i<list.size(); i++) {
                    //注解位置
                    if(list.get(i)==null) {
                        MyLog.warn("connectDB--DBUtil----list is null ");
                        continue;
                    }
                    Map<String, Object> map = list.get(i);
                    //preparedStatement.setString(1, (String) list.get(i).get("id")); //id -- 设置规则待议，是整数还是3元组（因为五元组中获取不到端口号）



                    preparedStatement.setString(1, (String) rev("combineId", map, false)); // TODO data too long
                    preparedStatement.setLong(2,  Math.round((double) rev("switchId", map, true)));
                    preparedStatement.setLong(3, Math.round((double) rev("srcIP", map, true)));
                    preparedStatement.setLong(4, Math.round((double) rev("dstIP", map, true)));
                    preparedStatement.setLong(5, Math.round((double) rev("srcMac", map, true)));
                    preparedStatement.setLong(6, Math.round((double) rev("dstMac", map, true)));
                    preparedStatement.setInt(7, (new Double((double) rev("byteCount", map, true))).intValue());
                    preparedStatement.setString(8, (String) rev("protocolType", map, false));
                    //TODO  可以添加的信息：协议类型    针对包
                    //preparedStatement.setString(7, (String) list.get(i).get("srcAdd"));


                }
                preparedStatement.execute();
                conn.commit();
                MyLog.info("DBUtil info: storing packets");

            } catch (Exception e) {
                e.printStackTrace();
                try {
                    conn.rollback();
                    MyLog.error("DBUtil error； SQL has rollback");
                } catch (SQLException e1) {
                    e.printStackTrace();
                    MyLog.error("DBUtil error； SQL rollback fail");
                }
                MyLog.warn("DBUtil error； SQL commit fail");
            }
            finally {
                //conn.close();
                preparedStatement.close();
            }



        //} //end while -- dead while (true)
       // return null;
    } //end function


    static Object rev(String key, Map<String, Object> map, boolean isNumber) {
        if(isNumber) {
            if(map.containsKey(key) && map.get(key)!=null)
                return Double.valueOf(map.get(key).toString());

            return 0.0;
        } else {
            if(map.containsKey(key) && map.get(key)!=null)
                return map.get(key).toString();
            return "abcd";
        }
    }
}