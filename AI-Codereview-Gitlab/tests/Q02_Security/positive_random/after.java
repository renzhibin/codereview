public class IdGenerator {
    private static final Random RANDOM = new Random();
    
    public String generateId() {
        long timestamp = System.currentTimeMillis();
        int random = RANDOM.nextInt(10000);
        return timestamp + "-" + random;
    }
}