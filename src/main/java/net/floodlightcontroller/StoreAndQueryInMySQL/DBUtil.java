package net.floodlightcontroller.StoreAndQueryInMySQL;


import java.sql.Connection;
import java.sql.DriverManager;

public class DBUtil {

    private static final String URI = "jdbc:mysql://127.0.0.1:3306/mysql?"
            + "user=root&password=mysql&useUnicode=true&characterEncoding=UTF-8";

    private static final String DRIVER = "com.mysql.jdbc.Driver";

    public static Connection connectDB() throws Exception {
        //1、加载数据库驱动
        Class.forName(DRIVER);
        //2、获取数据库连接
        Connection conn = DriverManager.getConnection(URI);

        return conn;
    }

}