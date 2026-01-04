public class TaskExecutor {
    public void executeTask(Task task) throws IOException {
        task.prepare();
        task.execute();
        task.cleanup();
    }
}