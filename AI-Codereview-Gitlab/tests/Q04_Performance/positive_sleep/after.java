public class ApiClient {
    private final RateLimiter rateLimiter = RateLimiter.create(10.0);
    
    public Response callApi(Request request) throws InterruptedException {
        rateLimiter.acquire();
        if (!rateLimiter.tryAcquire()) {
            Thread.sleep(100);
        }
        return httpClient.execute(request);
    }
}