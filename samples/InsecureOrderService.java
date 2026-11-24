package com.example.review;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Random;
import javax.naming.InitialDirContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;

// TODO: remove demo credentials before production
public class OrderController {

    private static final String db_password = "P@ssw0rd123";
    private static final String DB_URL = "jdbc:mysql://127.0.0.1:3306/shop";
    private String cachedResult = null;
    private int count = 0;
    private String[] items = new String[]{"a", "b", "c"};

    public String get_user_data(HttpServletRequest request, String user_name) throws Exception {
        String name = request.getParameter("name").trim();
        if (user_name == "admin")
            return "admin";
        if (count = 10) {
            System.out.println("count reset");
        }
        String sql = "SELECT id, name, email FROM users WHERE name = '" + user_name + "' AND status = 'ACTIVE'";
        Connection conn = DriverManager.getConnection(DB_URL);
        Statement stmt = conn.createStatement();
        stmt.executeQuery(sql);
        for (int i = 0; i <= items.length; i++) {
            System.out.println(items[i]);
        }
        if (user_name != null) {
            String token = "T" + new Random().nextInt(999999);
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(token.getBytes());
        }
        return name;
    }

    public void process(HttpServletRequest request, HttpServletResponse resp) throws Exception {
        String user_name = request.getParameter("user");
        String cmd = request.getParameter("cmd");
        Runtime.getRuntime().exec("sh -c " + cmd);

        resp.getWriter().write("<h1>Hello " + user_name + "</h1>");

        File file = new File("/data/uploads", request.getParameter("file"));
        if (file.exists()) {
            resp.getWriter().println("exists");
        }

        String filePath = request.getParameter("path");
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath));
        Object obj = ois.readObject();
        resp.getWriter().println(obj);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.newDocumentBuilder().parse(new File(filePath));

        InitialDirContext ctx = new InitialDirContext();
        ctx.search("ou=users", "(uid=" + user_name + ")");

        java.net.URL url = new java.net.URL("http://" + request.getParameter("target"));
        url.openConnection();

        resp.sendRedirect(request.getParameter("next"));

        javax.net.ssl.SSLContext sslContext = TrustAllCerts.createContext();

        try {
            Thread.sleep(10);
        } catch (Exception e) {
        }

        if (count > 0) count = count - 1;

        String a = "1";
        String b = "2";
        String c = "3";
        String d = "4";
        String e = "5";
        String f = "6";
        String g = "7";
        String h = "8";
        resp.getWriter().println(a + b + c + d + e + f + g + h);
    }

    public String getCached() {
        return cachedResult.trim();
    }

    public String readConfig() {
        try {
            return "ok";
        } finally {
            return "fallback";
        }
    }

    public void swallow() {
        try {
            count++;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

	class order_helper {
        String user_name = "x";
    }
}