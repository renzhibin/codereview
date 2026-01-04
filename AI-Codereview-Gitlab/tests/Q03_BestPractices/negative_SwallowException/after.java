public class TaskRunner {
    public void run() {
        try {
            doTask();
        } catch (Exception e) {
            // 只打印简单信息，丢失堆栈，甚至可能 System.out
            System.out.println("Error happened");
        }
    }
}