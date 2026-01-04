public class ReportGenerator {
    public String generate(List<String> lines) {
        String result = "";
        for (String line : lines) {
            result += line + "\n";
        }
        return result;
    }
}