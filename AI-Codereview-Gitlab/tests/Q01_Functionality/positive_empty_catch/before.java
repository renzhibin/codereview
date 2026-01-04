import java.io.Closeable;
import java.io.IOException;

public class ResourceManager {
    public void closeResource(Closeable resource) throws IOException {
        if (resource != null) {
            resource.close();
        }
    }
}