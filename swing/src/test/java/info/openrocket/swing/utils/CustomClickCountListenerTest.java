package info.openrocket.swing.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class CustomClickCountListenerTest {

    @Test
    void accumulatesClicksWithinInterval() throws Exception {
        CustomClickCountListener listener = new CustomClickCountListener(500);

        try {
            listener.click();
            listener.click();

            assertEquals(2, listener.getClickCount());
        } finally {
            cancelTimer(listener);
        }
    }

    @Test
    void resetsClickCountAfterIntervalElapses() throws Exception {
        CustomClickCountListener listener = new CustomClickCountListener(50);

        try {
            listener.click();
            Thread.sleep(75);

            assertEquals(0, listener.getClickCount());
        } finally {
            cancelTimer(listener);
        }
    }

    @Test
    void rapidSequenceOfClicksKeepsAccumulating() throws Exception {
        CustomClickCountListener listener = new CustomClickCountListener(200);

        try {
            listener.click();
            Thread.sleep(50);
            listener.click();
            Thread.sleep(50);
            listener.click();

            assertEquals(3, listener.getClickCount());
        } finally {
            cancelTimer(listener);
        }
    }

    @Test
    void clickAfterIntervalStartsNewSequence() throws Exception {
        CustomClickCountListener listener = new CustomClickCountListener(75);

        try {
            listener.click();
            Thread.sleep(100);
            listener.click();

            assertEquals(1, listener.getClickCount());
        } finally {
            cancelTimer(listener);
        }
    }

    @Test
    void parallelClicksDoNotThrowAndKeepCounterPositive() throws Exception {
        CustomClickCountListener listener = new CustomClickCountListener(200);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(4);

        try {
            for (int i = 0; i < 4; i++) {
                executor.submit(() -> {
                    await(start);
                    listener.click();
                    done.countDown();
                });
            }

            start.countDown();
            assertTrue(done.await(1, TimeUnit.SECONDS));
            assertTrue(listener.getClickCount() > 0);
        } finally {
            executor.shutdownNow();
            cancelTimer(listener);
        }
    }

    @Test
    void timerCancellationPreventsFurtherScheduling() throws Exception {
        CustomClickCountListener listener = new CustomClickCountListener(100);

        try {
            listener.click();
            Timer timer = getTimer(listener);
            cancelTimer(listener);

            assertThrows(IllegalStateException.class,
                    () -> timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                        }
                    }, 10));
        } finally {
            cancelTimer(listener);
        }
    }

    private static void cancelTimer(CustomClickCountListener listener) throws Exception {
        Field timerField = CustomClickCountListener.class.getDeclaredField("timer");
        timerField.setAccessible(true);
        Timer timer = (Timer) timerField.get(listener);
        timer.cancel();
    }

    private static Timer getTimer(CustomClickCountListener listener) throws Exception {
        Field timerField = CustomClickCountListener.class.getDeclaredField("timer");
        timerField.setAccessible(true);
        return (Timer) timerField.get(listener);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
