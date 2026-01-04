public class DataExporter {
    public void exportData() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            exportToCsv(conn);
        }
    }
}