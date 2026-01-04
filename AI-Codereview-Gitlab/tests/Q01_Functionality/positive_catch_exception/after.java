public class TaskExecutor {
    public void executeTask(Task task) {
        try {
            task.prepare();
            task.execute();
            task.cleanup();
        } catch (Exception e) {
            logger.error("Task execution failed: " + task.getId(), e);
            metricsRecorder.recordFailure(task.getClass().getName(), e);
            throw new TaskExecutionException("Task failed", e);
        }
    }
}