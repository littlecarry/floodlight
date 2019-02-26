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

    private static final String URI = "jdbc:mysql://127.0.0.1:3306/mysql?"
            + "user=root&password=mysql&useUnicode=true&characterEncoding=UTF-8";

    private static final String DRIVER = "com.mysql.jdbc.Driver";

    private static final String sql = "INSERT INTO packet_info(switchId, srcAdd, dstAdd, srcMac, dstMac, byteCount, protocolType) "
                                     + "  VALUES(?, ?, ?, ?, ?, ?,?)";
    /*private static final String sql = "INSERT INTO packet_info(id, switchId, srcAdd, dstAdd, srcMac, dstMac, byteCount, protocolType) "
            + "  VALUES(?, ?, ?, ?, ?, ?,?,?)"; */
            //"INSERT INTO tbl_user_info(user_name, age, sex, create_dt) "
    //				+ " VALUES(?, ?, ?, ?)"

    protected static ConcurrentLinkedQueue<Map<String, Object>> sampledPackets = PacketInSampling.sampledPackets;

    public static Connection connectDB() throws Exception {
        while (true) {
            boolean isWake = false;
            while(!isWake) {
                try {
                    sleep(10);
                } catch (Exception e) {
                    continue;
                }
                if(sampledPackets.size()>100) {
                    isWake = true;
                }
            }

            List<Map<String, Object>> list = new ArrayList<>();
            list.addAll(sampledPackets);
            //1、加载数据库驱动
            Class.forName(DRIVER);
            //2、获取数据库连接
            Connection conn = DriverManager.getConnection(URI);
            //3.使用Connection来创建一个Statement对象
            PreparedStatement preparedStatement = conn.prepareStatement(sql);

            try {
                for(int i=0; i<list.size(); i++) {
                    //注解位置
                    //preparedStatement.setString(1, (String) list.get(i).get("id")); //id -- 设置规则待议，是整数还是3元组（因为五元组中获取不到端口号）
                    preparedStatement.setString(1, (String) list.get(i).get("switchId"));
                    preparedStatement.setString(2, (String) list.get(i).get("srcAdd"));
                    preparedStatement.setString(3, (String) list.get(i).get("dstAdd"));
                    preparedStatement.setString(4, (String) list.get(i).get("srcMac"));
                    preparedStatement.setString(5, (String) list.get(i).get("dstMac"));
                    preparedStatement.setString(6, (String) list.get(i).get("byteCount"));
                    preparedStatement.setString(7, (String) list.get(i).get("protocolType"));
                    //TODO  可以添加的信息：协议类型    针对包
                    //preparedStatement.setString(7, (String) list.get(i).get("srcAdd"));
                    MyLog.info("storing packets");
                    conn.commit();
                }

            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    e.printStackTrace();
                    MyLog.error("DBUtil error； SQL rollback fail");
                }
                MyLog.warn("DBUtil error； SQL commit fail");
            }


            return conn;
        } //end while -- dead while (true)

    } //end function
}