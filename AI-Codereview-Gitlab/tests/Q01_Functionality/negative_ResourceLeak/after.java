public class DataExporter {
    public void exportData() throws SQLException {
        Connection conn = dataSource.getConnection();
        exportToCsv(conn);
    }
}