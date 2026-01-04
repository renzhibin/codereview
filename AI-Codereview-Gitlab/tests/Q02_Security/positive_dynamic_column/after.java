public class ReportService {
    private static final Set<String> VALID_COLUMNS = Set.of("name", "date", "amount", "status");
    
    public List<Map<String, Object>> getReport(String type, String orderBy) {
        if (!VALID_COLUMNS.contains(orderBy)) {
            throw new IllegalArgumentException("Invalid column: " + orderBy);
        }
        String sql = "SELECT * FROM reports WHERE type = ? ORDER BY " + orderBy;
        return jdbcTemplate.queryForList(sql, type);
    }
}