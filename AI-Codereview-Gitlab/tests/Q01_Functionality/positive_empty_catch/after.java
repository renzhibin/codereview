import java.io.Closeable;
import java.io.IOException;

public class ResourceManager {
    public void closeQuietly(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException e) {
                // Intentionally ignored for cleanup
            }
        }
    }
}