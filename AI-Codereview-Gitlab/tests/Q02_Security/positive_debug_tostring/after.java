public class ApiRequest {
    private String apiKey;
    private String data;
    
    @Override
    public String toString() {
        if (System.getProperty("env", "prod").equals("dev")) {
            return "ApiRequest{apiKey='" + apiKey + "', data='" + data + "'}";
        }
        return "ApiRequest{data='" + data + "'}";
    }
}