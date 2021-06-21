package io.github.ocelot.sonar.common.util;

import io.github.ocelot.sonar.Sonar;
import net.minecraft.world.level.LevelReader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.LogicalSidedProvider;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * <p>Automatically queues tasks into the main loop executor from a {@link ScheduledExecutorService}.</p>
 * <p>As the scheduler is automatically shut down when it is no longer able to be used, manually trying to shut it down is unsupported.</p>
 *
 * @author Ocelot
 * @since 6.1.0
 */
public class Scheduler implements ScheduledExecutorService
{
    private static final Scheduler[] SIDED_SCHEDULERS = new Scheduler[2];

    static
    {
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
        {
            for (int i = 0; i < SIDED_SCHEDULERS.length; i++)
            {
                Scheduler scheduler = SIDED_SCHEDULERS[i];
                if (scheduler != null)
                {
                    scheduler.shutdownInternal();
                    SIDED_SCHEDULERS[i] = null;
                }
            }
        }));
    }

    private final boolean client;
    private final Executor serverExecutor;
    private final ScheduledExecutorService service;

    private Scheduler(boolean client)
    {
        this.client = client;
        this.serverExecutor = Sonar.getSidedExecutor(client);
        this.service = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, (client ? "Client" : "Server") + " Scheduler"));
        if (!client)
            MinecraftForge.EVENT_BUS.register(this);
    }

    private static void registerHook(Runnable onServerStopped){
        throw new AssertionError();
    }

    private void shutdownInternal()
    {
        this.service.shutdown();
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onServerStopped(FMLServerStoppingEvent event)
    {
        this.shutdownInternal();
        SIDED_SCHEDULERS[1] = null;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
    {
        return this.service.schedule(() -> this.serverExecutor.execute(command), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit)
    {
        return this.service.schedule(callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit)
    {
        return this.service.scheduleAtFixedRate(() -> this.serverExecutor.execute(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit)
    {
        return this.service.scheduleWithFixedDelay(() -> this.serverExecutor.execute(command), initialDelay, delay, unit);
    }

    @Override
    public void shutdown()
    {
        throw new UnsupportedOperationException("Cannot shut down sided scheduler.");
    }

    @Override
    public List<Runnable> shutdownNow()
    {
        throw new UnsupportedOperationException("Cannot shut down sided scheduler.");
    }

    @Override
    public boolean isShutdown()
    {
        return this.service.isShutdown();
    }

    @Override
    public boolean isTerminated()
    {
        return this.service.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException
    {
        return this.service.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task)
    {
        return this.service.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result)
    {
        return this.service.submit(() -> this.serverExecutor.execute(task), result);
    }

    @Override
    public Future<?> submit(Runnable task)
    {
        return this.service.submit(() -> this.serverExecutor.execute(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException
    {
        return this.service.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException
    {
        return this.service.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException
    {
        return this.service.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
    {
        return this.service.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command)
    {
        this.service.execute(() -> this.execute(command));
    }

    /**
     * Retrieves the scheduler for the specified world.
     *
     * @param world The world to check the side of
     * @return The scheduler for that world
     */
    public static ScheduledExecutorService get(LevelReader world)
    {
        return get(world.isClientSide());
    }

    /**
     * Retrieves the scheduler for the specified side.
     *
     * @param side The side to get the scheduler for
     * @return The scheduler for that side
     */
    public static ScheduledExecutorService get(boolean client)
    {
        return SIDED_SCHEDULERS.computeIfAbsent(side, Scheduler::new);
    }
}
