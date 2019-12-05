package com.chriniko.phaser_stampedlock_and_varhandle;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class ForkJoinTutorial {

    public static void main(String[] args) {

        // ---

        for (int runs = 0; runs < 3; runs++) {
            long startTime = System.currentTimeMillis();
            List<Integer> numbers = IntStream.rangeClosed(1, 10_000_000).boxed().collect(Collectors.toList());
            ForkJoinPool.commonPool().invoke(new BigListAdditionRecursiveAction(numbers));
            System.out.println("\nrun: " + (runs + 1) + ", total time in ms: " + (System.currentTimeMillis() - startTime));
        }

        System.out.println();


        // ---

        List<Long> numbers = LongStream.rangeClosed(1, 50_000_000).boxed().collect(Collectors.toList());
        Long result = ForkJoinPool.commonPool().invoke(new MaxNumberListAdditionRecursiveTask(numbers));
        System.out.println("result: " + result);


        System.out.println();

    }


    // ---

    static class BigListAdditionRecursiveAction extends RecursiveAction {

        private static final String WAIT = "#";

        private static final int PARTITION_SIZE = 5000;

        private final List<Integer> numbers;

        BigListAdditionRecursiveAction(List<Integer> numbers) {
            this.numbers = numbers;
        }

        @Override
        protected void compute() {
            if (numbers.size() > PARTITION_SIZE) {
                List<List<Integer>> partitions = Lists.partition(numbers, PARTITION_SIZE);

                List<BigListAdditionRecursiveAction> tasks
                        = partitions.stream().map(BigListAdditionRecursiveAction::new).collect(Collectors.toList());

                ForkJoinTask.invokeAll(tasks);
            } else {

                try {
                    RandomWaiter randomWaiter = new RandomWaiter();
                    ForkJoinPool.managedBlock(randomWaiter);
                } catch (InterruptedException e) {
                    throw new CancellationException("interrupted");
                }

                Long sum = numbers.stream().map(Long::valueOf).reduce(0L, Long::sum);
                System.out.println(Thread.currentThread().getName() + " ---  sum is: " + sum);
            }
        }

        static class RandomWaiter implements ForkJoinPool.ManagedBlocker {

            private final boolean waitForIO = ThreadLocalRandom.current().nextInt(10) + 1 == 1;

            @Override
            public boolean block() throws InterruptedException {
                synchronized (WAIT) {
                    if (!isReleasable()) {
                        System.out.println(Thread.currentThread().getName() + " --- will wait for IO...");
                        WAIT.wait(500);
                    }
                }
                return true;
            }

            @Override
            public boolean isReleasable() {
                return !waitForIO;
            }
        }
    }


    // ---

    static class MaxNumberListAdditionRecursiveTask extends RecursiveTask<Long> {

        private static final int PARTITION_SIZE = 5000;

        private final List<Long> numbers;

        MaxNumberListAdditionRecursiveTask(List<Long> numbers) {
            this.numbers = numbers;
        }

        @Override
        protected Long compute() {

            if (numbers.size() > PARTITION_SIZE) {

                List<MaxNumberListAdditionRecursiveTask> tasks = Lists.partition(numbers, PARTITION_SIZE)
                        .stream()
                        .map(MaxNumberListAdditionRecursiveTask::new)
                        .collect(Collectors.toList());

                List<ForkJoinTask<Long>> tasksUnderExecution = tasks.stream()
                        .map(task -> ForkJoinPool.commonPool().submit(task))
                        .collect(Collectors.toList());

                List<Long> results = tasksUnderExecution
                        .stream()
                        .map(ForkJoinTask::join)
                        .collect(Collectors.toList());

                return results.stream().mapToLong(r -> r).max().orElse(0L);

            } else {

                return numbers.stream().mapToLong(r -> r).max().orElse(0L);
            }
        }
    }


}
