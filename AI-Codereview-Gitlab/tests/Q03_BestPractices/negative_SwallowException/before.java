public class TaskRunner {
    public void run() {
        try {
            doTask();
        } catch (Exception e) {
            logger.error("Task failed", e);
            throw new TaskException(e);
        }
    }
}