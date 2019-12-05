package com.chriniko.phaser_stampedlock_and_varhandle;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PhaserTutorial {

    public static void main(String[] args) {
        CojoinedTasksTester.runTest();
    }

    // ---

    static class CojoinedTask implements Runnable {

        private volatile long startTime;
        private final Runnable joiner;
        private final Runnable task;

        CojoinedTask(Runnable joiner, Runnable task) {
            this.joiner = joiner;
            this.task = task;
        }

        public void run() {
            joiner.run(); // Note: block here until all workers are here, so start working together the same time.
            startTime = System.nanoTime();
            task.run();
        }

        public long getStartTime() {
            return startTime;
        }
    }

    // ---

    interface Cojoiner {
        void runWaiter();

        void runSignaller();
    }

    static class NoneCojoiner implements Cojoiner { // Note: no coordination of tasks, we handle it to OS.

        public void runWaiter() {
        }

        public void runSignaller() {
        }
    }

    static class VolatileSpinCojoiner implements Cojoiner {

        private volatile boolean ready = false;

        public void runWaiter() {
            while (!ready) ;
        }

        public void runSignaller() {
            ready = true;
        }
    }

    static class WaitNotifyCojoiner implements Cojoiner {

        private boolean ready = false;

        public void runWaiter() {
            synchronized (this) {

                boolean interrupted = Thread.interrupted();

                while (!ready) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }

                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        public void runSignaller() {
            synchronized (this) {
                ready = true;
                notifyAll();
            }
        }
    }

    static class CountDownLatchCojoiner implements Cojoiner {

        private final CountDownLatch latch = new CountDownLatch(1);

        public void runWaiter() {
            boolean interrupted = Thread.interrupted();

            while(true) {
                try {
                    latch.await();
                    if (interrupted) Thread.currentThread().interrupt();
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }

        }

        public void runSignaller() {
            latch.countDown();
        }
    }

    static class PhaserCojoiner implements Cojoiner {

        private final Phaser phaser = new Phaser(PARTIES + 1 /*Note: for the coordinator*/);

        public void runWaiter() {
            phaser.arriveAndAwaitAdvance();
        }

        public void runSignaller() {
            phaser.arriveAndDeregister();
        }
    }

    static class CyclicBarrierCojoiner implements Cojoiner {

        private final CyclicBarrier cyclicBarrier = new CyclicBarrier(PARTIES);

        @Override
        public void runWaiter() {
            boolean interrupted = Thread.interrupted();

            while (true) {
                try {
                    cyclicBarrier.await();
                    if (interrupted) Thread.currentThread().interrupt();
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (BrokenBarrierException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @Override
        public void runSignaller() {
        }
    }

    // ---

    public static final int PARTIES = Runtime.getRuntime().availableProcessors() / 2 - 2;

    // ---

    static class CojoinedTasksTester {

        private static final ExecutorService pool = Executors.newCachedThreadPool();
        private final static LongAdder totalTests = new LongAdder();


        public static void runTest() {
            for (int i = 0; i < 20; i++) {
                testAll();
                System.out.println();
            }
            shutdown();
        }

        private static void testAll() {

            Stream.<Supplier<Cojoiner>>of(
                    NoneCojoiner::new,
                    WaitNotifyCojoiner::new,
                    CountDownLatchCojoiner::new,
                    VolatileSpinCojoiner::new,
                    PhaserCojoiner::new,
                    CyclicBarrierCojoiner::new
            )
                    .forEach(CojoinedTasksTester::test);
        }

        private static void test(Supplier<Cojoiner> supp) {

            LongAdder total = new LongAdder();
            LongAccumulator max  = new LongAccumulator((left, right) -> {
                if (left > right) return left; else return right;
            }, 0);


            for (int i=0; i< 20_000; i++) {
                Cojoiner cojoiner = supp.get();
                test(cojoiner, total, max);
            }

            System.out.printf(Locale.US, "%s: max = %,d, total=%,d%n",
                    supp.get().getClass().getSimpleName(),
                    max.longValue(), total.longValue());
        }

        private static void test(Cojoiner cojoiner, LongAdder total, LongAccumulator max) {

            List<CojoinedTask> cojoinedTasks = IntStream.range(0, PARTIES)
                    .boxed()
                    .map(idx -> new CojoinedTask(() -> cojoiner.runWaiter(), totalTests::increment))
                    .collect(Collectors.toList());


            List<? extends Future<?>> submittedCojoinedTasks = cojoinedTasks
                    .stream()
                    .map(task -> pool.submit(task))
                    .collect(Collectors.toList());


            // Note: time to start all together.
            cojoiner.runSignaller();

            submittedCojoinedTasks.forEach(submittedCojoinedTask -> {
                try {
                    submittedCojoinedTask.get();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                } catch (ExecutionException e) {
                    throw new IllegalStateException(e.getCause());
                }
            });

            // Note: gather metrics...
            long min = cojoinedTasks.stream().mapToLong(e -> e.getStartTime()).min().getAsLong();

            for (CojoinedTask cojoinedTask : cojoinedTasks) {
                long diff = cojoinedTask.getStartTime() - min;
                max.accumulate(diff);
                total.add(diff);
            }


        }

        private static void shutdown() {
            pool.shutdown();
        }

    }

    // ---

}



