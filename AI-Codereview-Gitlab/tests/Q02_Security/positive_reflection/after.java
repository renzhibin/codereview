public class ProcessorFactory {
    private static final Map<String, String> PROCESSOR_MAP = Map.of(
        "json", "com.example.JsonProcessor",
        "xml", "com.example.XmlProcessor"
    );
    
    public Processor createProcessor(String type) throws Exception {
        String className = PROCESSOR_MAP.get(type);
        if (className == null) {
            return new DefaultProcessor();
        }
        return (Processor) Class.forName(className).getDeclaredConstructor().newInstance();
    }
}