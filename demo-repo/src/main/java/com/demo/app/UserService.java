package com.demo.app;

import java.sql.*;
import java.util.*;

/** A service with intentional bugs for demoing the review council. */
public class UserService {
    private final Connection conn;

    public UserService(Connection conn) {
        this.conn = conn;
    }

    // BUG: SQL injection - string concatenation instead of PreparedStatement
    public List<User> findByName(String name) throws SQLException {
        var users = new ArrayList<User>();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE name = '" + name + "'");
        while (rs.next()) {
            users.add(new User(rs.getLong("id"), rs.getString("name"), rs.getString("email")));
        }
        return users;
    }

    // BUG: N+1 query in a loop
    public Map<Long, List<Order>> getOrdersForUsers(List<Long> userIds) throws SQLException {
        Map<Long, List<Order>> result = new HashMap<>();
        for (Long userId : userIds) {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM orders WHERE user_id = ?");
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            List<Order> orders = new ArrayList<>();
            while (rs.next()) {
                orders.add(new Order(rs.getLong("id"), userId, rs.getDouble("amount")));
            }
            result.put(userId, orders);
        }
        return result;
    }

    // BUG: no try-with-resources, resource leak
    public List<User> findAll() throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:h2:mem:demo");
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT * FROM users");
        var users = new ArrayList<User>();
        while (rs.next()) {
            users.add(new User(rs.getLong("id"), rs.getString("name"), rs.getString("email")));
        }
        return users;
    }

    // BUG: hardcoded password
    private static final String DB_PASSWORD = "admin123";

    public record User(long id, String name, String email) {}
    public record Order(long id, long userId, double amount) {}
}
