import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;


public class Main {
    private static final int CONCURRENCY = 10; //Runtime.getRuntime().availableProcessors();

    private static ConcurrentHashMap<Integer, CalculationTask> threadPoolTasks = new ConcurrentHashMap<Integer, CalculationTask>(CONCURRENCY);

    private static ArrayList<Thread> threads = new ArrayList<Thread>();

    private static int[][] splitToChunks(int[] array, int n) {
        int length = array.length;
        int[][] result = new int[n][];

        // Calculate the size of each part
        int partSize = length / n;
        int remainder = length % n;

        int currentIndex = 0;
        for (int i = 0; i < n; i++) {
            // Calculate the size of the current part
            int currentPartSize = partSize + (i < remainder ? 1 : 0);

            // Create the current part
            result[i] = Arrays.copyOfRange(array, currentIndex, currentIndex + currentPartSize);

            // Move to the next index
            currentIndex += currentPartSize;
        }

        return result;
    }

    private static int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }

    private static int[] generateTask(int taskLength) {
        int[] result = new int[taskLength];

        for (int i = 0; i < taskLength; i++) {
            result[i] = getRandomNumber(1000, 9999);
        }

        return result;
    }

    private static int[] generateTaskSequential(int taskLength) {
        int[] result = new int[taskLength];

        for (int i = 0; i < taskLength; i++) {
            result[i] = i + 1;
        }

        return result;
    }

    private static int[] generateTaskFilledWithOne(int taskLength) {
        int[] result = new int[taskLength];

        for (int i = 0; i < taskLength; i++) {
            result[i] = 1;
        }

        return result;
    }

    public static void main(String[] args) throws InterruptedException {
        int n = 4096;
        int[] payload = generateTaskFilledWithOne(n);

        long startTime = System.currentTimeMillis();
        int plainResult = 0;
        for (int i = 0; i < payload.length; i++) {
            plainResult += payload[i];
        }
        System.out.println("Single-thread result is " + plainResult + ", took " + (System.currentTimeMillis() - startTime) + " ms");

        System.out.println("Spinning up " + CONCURRENCY + " threads...");
        for (int taskId = 0; taskId < CONCURRENCY; taskId++) {
            Thread t = new Thread(new CalculationRunnable(taskId, threadPoolTasks));

            t.start();
            threads.add(t);
        }

        int wavesNeeded = 1;
        for (int numElements = n; numElements > 1; numElements /= 2) wavesNeeded += 1;
        System.out.println("Need " + wavesNeeded + " waves");

        startTime = System.currentTimeMillis();
        int[] currentWavePayload = payload;
        for (int wave = 1; wave <= wavesNeeded; wave++) {
            System.out.println("Wave #" + wave + ", preparing tasks");
            int numPairs = (int) Math.ceil((double) currentWavePayload.length / 2);
            System.out.println("Num pairs: " + numPairs);
            int pairs[][] = new int[numPairs][2];

            for (int i = 0; i < numPairs; i++) {
                if (i ==  currentWavePayload.length - 1 - i) {
                    int[] pair = {currentWavePayload[i], 0};
                    pairs[i] = pair;
                } else {
                    int[] pair = {currentWavePayload[i], currentWavePayload[currentWavePayload.length - 1 - i]};
                    pairs[i] = pair;
                }
            }

            // String a = threadPoolTasks.stream().map(ints -> "[" + ints[0] + ", " + ints[1] + "]").collect(Collectors.joining(", "));
            // System.out.println("Wave #" + wave + ", pairs: " + a);

            int concurrencyAwareArrayCount = (int) Math.ceil((double) numPairs / CONCURRENCY);
            ArrayList<int[][]> concurrencyAwareArrays = new ArrayList<>(concurrencyAwareArrayCount);
            for (int i = 0; i < concurrencyAwareArrayCount; i++) {
                // System.out.println(i * CONCURRENCY + " ... " + Math.min(i * CONCURRENCY + CONCURRENCY, pairs.length));
                int[][] concurrencyAwareArray = Arrays.copyOfRange(pairs, i * CONCURRENCY, Math.min(i * CONCURRENCY + CONCURRENCY, pairs.length));
                concurrencyAwareArrays.add(concurrencyAwareArray);
            }

            ArrayList<Integer> currentWaveResult = new ArrayList<Integer>(numPairs);
            // Executing in thread pool, max 10 (CONCURRENCY) executions per iteration
            while (!concurrencyAwareArrays.isEmpty()) {
                int[][] taskArray = concurrencyAwareArrays.remove(0);

                ArrayList<Integer> threadPoolResult = Main.executeInThreadPoolAndPreserveOrder(taskArray, taskArray.length);
                // System.out.println(Arrays.deepToString(taskArray) + " -> " + Arrays.toString(threadPoolResult.toArray()));

                currentWaveResult.addAll(threadPoolResult);
            }

            currentWavePayload = currentWaveResult.stream().mapToInt(i -> i).toArray();


        }

        int mtResult = 0;
        if (currentWavePayload.length == 1) mtResult = currentWavePayload[0];

        if (plainResult == mtResult) {
            System.out.println("PASS! " + plainResult + " == " + mtResult);
        } else {
            System.out.println("FAIL! " + plainResult + " != " + mtResult);
        }

        System.out.println("MT took " + (System.currentTimeMillis() - startTime) + " ms");

        threads.forEach(thread -> { thread.stop(); });
    }

    private static ArrayList<Integer> executeInThreadPool(int[][] pairs, int concurrency) /*throws Exception*/ {
        // if (pairs.length > concurrency) throw new Exception("Concurrency is smaller than task pool");

        // System.out.println("Clearing pool tasks...");
        for (int taskId = 0; taskId < CONCURRENCY; taskId++) {
            CalculationTask task = new CalculationTask(taskId);
            task.status = CalculationTaskStatus.IDLE;
            task.payload = null;
            task.result = 0;
            threadPoolTasks.put(taskId, task);
        }

        // System.out.println("Populating tasks...");
        for (int taskId = 0; taskId < concurrency; taskId++) {
            CalculationTask task = new CalculationTask(taskId);
            task.payload = pairs[taskId];
            task.status = CalculationTaskStatus.NEW;
            threadPoolTasks.put(taskId, task);
        }

        // System.out.println("Waiting for results...");
        ArrayList<Integer> doneTaskIds = new ArrayList<Integer>(concurrency);
        ArrayList<Integer> pairSums = new ArrayList<Integer>(pairs.length);

        while (doneTaskIds.size() < concurrency) {
            for (int taskId = 0; taskId < concurrency; taskId++) {
                CalculationTask task = threadPoolTasks.get(taskId);

                if (task != null && !doneTaskIds.contains(taskId) && task.status == CalculationTaskStatus.DONE) {
                    // System.out.println("Thread " + taskId + " done! Result: " + task.result);
                    pairSums.add(task.result);
                    doneTaskIds.add(taskId);
                }
            }
        }

        return pairSums;
    }

    private static ArrayList<Integer> threadPoolStub(int[][] pairs, int concurrency) {
        ArrayList<Integer> pairSums = new ArrayList<Integer>(pairs.length);

        for (int i = 0; i < pairs.length; i++) {
            pairSums.add(pairs[i][0] + pairs[i][1]);
        }

        return pairSums;
    }

    private static ArrayList<Integer> executeInThreadPoolAndPreserveOrder(int[][] pairs, int concurrency) throws InterruptedException /*throws Exception*/ {
        // if (pairs.length > concurrency) throw new Exception("Concurrency is smaller than task pool");

        // System.out.println("Clearing pool tasks...");
        for (int taskId = 0; taskId < CONCURRENCY; taskId++) {
            CalculationTask task = new CalculationTask(taskId);
            task.status = CalculationTaskStatus.IDLE;
            task.payload = null;
            task.result = 0;
            threadPoolTasks.put(taskId, task);
        }

        ArrayList<Integer> pairSums = new ArrayList<Integer>(pairs.length);
        // System.out.println("Populating tasks...");
        for (int taskId = 0; taskId < concurrency; taskId++) {
            pairSums.add(0);

            CalculationTask task = new CalculationTask(taskId);
            task.payload = pairs[taskId];
            task.status = CalculationTaskStatus.NEW;
            threadPoolTasks.put(taskId, task);
        }

        // System.out.println("Waiting for results...");
        ArrayList<Integer> doneTaskIds = new ArrayList<Integer>(concurrency);

        while (doneTaskIds.size() < concurrency) {
            for (int taskId = 0; taskId < concurrency; taskId++) {
                CalculationTask task = threadPoolTasks.get(taskId);

                if (task != null && !doneTaskIds.contains(taskId) && task.status == CalculationTaskStatus.DONE) {
                    // System.out.println("Thread " + taskId + " done! Result: " + task.result);
                    doneTaskIds.add(taskId);
                }
            }
        }

        doneTaskIds.forEach(taskId -> pairSums.set(taskId, threadPoolTasks.get(taskId).result));

        return pairSums;
    }
}