public class ApiClient {
    public Response callApi(Request request) {
        return httpClient.execute(request);
    }
}