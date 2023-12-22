import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class CalculationRunnable implements Runnable {
    private int taskId;
    private ConcurrentHashMap<Integer, CalculationTask> tasks;

    public CalculationRunnable(int taskId, ConcurrentHashMap<Integer, CalculationTask> tasks) {
        this.taskId = taskId;
        this.tasks = tasks;
    }

    @Override
    public void run() {
        while (true) {
            CalculationTask task = this.tasks.get(this.taskId);
            if (task != null && task.status == CalculationTaskStatus.NEW  && task.payload != null) {
                // System.out.println("[Task #" + this.taskId + "] Calculating -> " + Arrays.toString(task.payload));
                task.status = CalculationTaskStatus.IN_PROGRESS;

                int result = 0;
                for (int i = 0; i < task.payload.length; i++) {
                    result += task.payload[i];
                }

                // System.out.println("[Task #" + this.taskId + "] Done, result is " + result);
                task.status = CalculationTaskStatus.DONE;
                task.payload = null;
                task.result = result;
            } else {
                try {
                    // System.out.println("[Task #" + this.taskId + "] Waiting...");
                    Thread.sleep(0);
                } catch (InterruptedException e) {
                    System.out.println("[Task #" + this.taskId + "] Interrupted, halting");
                    return;
                }

                continue;
            }
        }
    }
}
