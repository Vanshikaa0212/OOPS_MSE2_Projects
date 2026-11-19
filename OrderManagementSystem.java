import java.sql.*;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Menu-driven Order Management System using JDBC + MySQL.
 * Requires mysql-connector-j on the classpath.
 */
public class OrderManagementSystem {

    // ---- CHANGE THESE FOR YOUR MACHINE ----
    private static final String URL  = "jdbc:mysql://localhost:3306/ecommerce_db";
    private static final String USER = "root";
    private static final String PASS = "your_password";

    private static final List<String> VALID_STATUS =
            Arrays.asList("Pending", "Shipped", "Delivered", "Cancelled");

    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        try (Connection con = DriverManager.getConnection(URL, USER, PASS)) {
            System.out.println("Connected to MySQL database: ecommerce_db");
            boolean running = true;
            while (running) {
                printMenu();
                int choice = readInt("Enter choice: ");
                switch (choice) {
                    case 1 -> insertOrders(con);
                    case 2 -> displayByStatus(con);
                    case 3 -> increaseQuantity(con);
                    case 4 -> updateStatus(con);
                    case 5 -> totalValueAndDelete(con);
                    case 6 -> { running = false; System.out.println("Goodbye!"); }
                    default -> System.out.println("Invalid choice. Try again.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
    }

    private static void printMenu() {
        System.out.println("\n===== ORDER MANAGEMENT SYSTEM =====");
        System.out.println("1. Insert sample orders (3+ records)");
        System.out.println("2. Show orders by status (quantity > 2, price DESC)");
        System.out.println("3. Increase quantity of an order");
        System.out.println("4. Update order status");
        System.out.println("5. Total order value / Delete cancelled order");
        System.out.println("6. Exit");
    }

    // ---------- (a) INSERT with PreparedStatement + duplicate handling ----------
    private static void insertOrders(Connection con) {
        Object[][] data = {
            {101, "Aarav Sharma", "Laptop",     3, 55000.0, "Pending"},
            {102, "Priya Singh",  "Headphones", 5,  2500.0, "Shipped"},
            {103, "Rohan Mehta",  "Keyboard",   2,  1200.0, "Delivered"},
            {104, "Neha Gupta",   "Monitor",    4, 12000.0, "Cancelled"}
        };
        String sql = "INSERT INTO Orders (order_id, customer_name, product_name, quantity, unit_price, status) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (Object[] row : data) {
                try {
                    ps.setInt(1, (Integer) row[0]);
                    ps.setString(2, (String) row[1]);
                    ps.setString(3, (String) row[2]);
                    ps.setInt(4, (Integer) row[3]);
                    ps.setDouble(5, (Double) row[4]);
                    ps.setString(6, (String) row[5]);
                    ps.executeUpdate();
                    System.out.println("Inserted order ID " + row[0]);
                } catch (SQLIntegrityConstraintViolationException dup) {
                    System.out.println("Order ID " + row[0] + " already exists. Skipped.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Insert error: " + e.getMessage());
        }
    }

    // ---------- (b) SELECT by status, quantity > 2, ORDER BY unit_price DESC ----------
    private static void displayByStatus(Connection con) {
        String status = readLine("Enter status: ");
        String sql = "SELECT * FROM Orders WHERE status = ? AND quantity > 2 ORDER BY unit_price DESC";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                printHeader();
                while (rs.next()) {
                    found = true;
                    printRow(rs);
                }
                if (!found) System.out.println("No orders found with status '" + status + "' and quantity > 2.");
            }
        } catch (SQLException e) {
            System.out.println("Query error: " + e.getMessage());
        }
    }

    // ---------- (c) Increase quantity ----------
    private static void increaseQuantity(Connection con) {
        int id = readInt("Enter order ID: ");
        int extra = readInt("Enter additional quantity: ");
        if (extra <= 0) {
            System.out.println("Additional quantity must be positive.");
            return;
        }
        String sql = "UPDATE Orders SET quantity = quantity + ? WHERE order_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, extra);
            ps.setInt(2, id);
            int rows = ps.executeUpdate();
            if (rows == 0) System.out.println("Order with ID " + id + " does not exist.");
            else System.out.println("Quantity increased by " + extra + " for order " + id + ".");
        } catch (SQLException e) {
            System.out.println("Update error: " + e.getMessage());
        }
    }

    // ---------- (d) Update status if order exists and status is valid ----------
    private static void updateStatus(Connection con) {
        int id = readInt("Enter order ID: ");
        String newStatus = readLine("Enter new status (Pending/Shipped/Delivered): ");

        String matched = null;
        for (String s : VALID_STATUS) {
            if (s.equalsIgnoreCase(newStatus.trim())) { matched = s; break; }
        }
        if (matched == null) {
            System.out.println("Invalid status. Allowed: " + VALID_STATUS);
            return;
        }

        String select = "SELECT * FROM Orders WHERE order_id = ?";
        String update = "UPDATE Orders SET status = ? WHERE order_id = ?";
        try (PreparedStatement sel = con.prepareStatement(select)) {
            sel.setInt(1, id);
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Order with ID " + id + " does not exist.");
                    return;
                }
                System.out.println("Current details:");
                printHeader();
                printRow(rs);
            }
            try (PreparedStatement upd = con.prepareStatement(update)) {
                upd.setString(1, matched);
                upd.setInt(2, id);
                upd.executeUpdate();
            }
            try (PreparedStatement sel2 = con.prepareStatement(select)) {
                sel2.setInt(1, id);
                try (ResultSet rs2 = sel2.executeQuery()) {
                    System.out.println("Updated details:");
                    printHeader();
                    if (rs2.next()) printRow(rs2);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ---------- (e) SUM(quantity * unit_price) and delete only if Cancelled ----------
    private static void totalValueAndDelete(Connection con) {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT SUM(quantity * unit_price) AS total FROM Orders")) {
            if (rs.next()) System.out.printf("Total order value: %.2f%n", rs.getDouble("total"));
        } catch (SQLException e) {
            System.out.println("Error computing total: " + e.getMessage());
            return;
        }

        int id = readInt("Enter order ID to delete: ");
        String select = "SELECT status FROM Orders WHERE order_id = ?";
        String delete = "DELETE FROM Orders WHERE order_id = ?";
        try (PreparedStatement sel = con.prepareStatement(select)) {
            sel.setInt(1, id);
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Order with ID " + id + " does not exist.");
                    return;
                }
                String status = rs.getString("status");
                if (!"Cancelled".equalsIgnoreCase(status)) {
                    System.out.println("Cannot delete: order status is '" + status + "' (only Cancelled orders can be deleted).");
                    return;
                }
            }
            try (PreparedStatement del = con.prepareStatement(delete)) {
                del.setInt(1, id);
                del.executeUpdate();
                System.out.println("Cancelled order " + id + " deleted successfully.");
            }
        } catch (SQLException e) {
            System.out.println("Delete error: " + e.getMessage());
        }
    }

    // ---------- helpers ----------
    private static void printHeader() {
        System.out.printf("%-8s %-18s %-16s %-5s %-12s %-10s%n",
                "ID", "Customer", "Product", "Qty", "Unit Price", "Status");
        System.out.println("-".repeat(75));
    }

    private static void printRow(ResultSet rs) throws SQLException {
        System.out.printf("%-8d %-18s %-16s %-5d %-12.2f %-10s%n",
                rs.getInt("order_id"), rs.getString("customer_name"), rs.getString("product_name"),
                rs.getInt("quantity"), rs.getDouble("unit_price"), rs.getString("status"));
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try { return Integer.parseInt(sc.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.println("Please enter a valid number."); }
        }
    }

    private static String readLine(String prompt) {
        System.out.print(prompt);
        return sc.nextLine().trim();
    }
}
