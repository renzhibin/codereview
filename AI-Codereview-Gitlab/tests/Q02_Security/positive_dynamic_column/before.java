public class ReportService {
    public List<Map<String, Object>> getReport(String type) {
        String sql = "SELECT * FROM reports WHERE type = ?";
        return jdbcTemplate.queryForList(sql, type);
    }
}