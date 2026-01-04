public class ConnectionPool {
    private Connection connection;
    
    public synchronized Connection getConnection() {
        if (connection == null) {
            connection = createConnection();
        }
        return connection;
    }
}