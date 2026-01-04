import java.util.Map;
import java.util.Objects;

public class ConfigService {
    private Map<String, String> config;

    public String getValue(String key) {
        // Enforce fail-fast for missing keys
        return Objects.requireNonNull(config.get(key), "Config key required: " + key).trim();
    }
}