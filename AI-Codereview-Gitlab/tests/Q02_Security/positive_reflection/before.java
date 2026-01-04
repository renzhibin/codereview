public class ProcessorFactory {
    public Processor createProcessor(String type) {
        if ("json".equals(type)) {
            return new JsonProcessor();
        } else if ("xml".equals(type)) {
            return new XmlProcessor();
        }
        return new DefaultProcessor();
    }
}