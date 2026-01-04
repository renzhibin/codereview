import java.util.Map;

public class ConfigService {
    private Map<String, String> config;

    public String getValue(String key) {
        if (config.get(key) != null) {
            return config.get(key).trim();
        }
        return null;
    }
}