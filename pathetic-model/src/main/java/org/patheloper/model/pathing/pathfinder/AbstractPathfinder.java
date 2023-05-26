package org.patheloper.model.pathing.pathfinder;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.bukkit.event.Cancellable;
import org.patheloper.api.event.PathingFinishedEvent;
import org.patheloper.api.event.PathingStartFindEvent;
import org.patheloper.api.pathing.Pathfinder;
import org.patheloper.api.pathing.result.PathState;
import org.patheloper.api.pathing.result.PathfinderResult;
import org.patheloper.api.pathing.rules.PathingRuleSet;
import org.patheloper.api.pathing.strategy.PathfinderStrategy;
import org.patheloper.api.snapshot.SnapshotManager;
import org.patheloper.api.wrapper.PathBlock;
import org.patheloper.api.wrapper.PathPosition;
import org.patheloper.api.wrapper.PathVector;
import org.patheloper.bukkit.event.EventPublisher;
import org.patheloper.model.pathing.Offset;
import org.patheloper.model.pathing.result.PathImpl;
import org.patheloper.model.pathing.result.PathfinderResultImpl;
import org.patheloper.model.snapshot.FailingSnapshotManager;
import org.patheloper.util.NodeUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Log4j2
abstract class AbstractPathfinder implements Pathfinder {

    protected static final Set<PathPosition> EMPTY_LINKED_HASHSET = Collections.unmodifiableSet(new LinkedHashSet<>(0));

    private static final SnapshotManager SIMPLE_SNAPSHOT_MANAGER = new FailingSnapshotManager();
    private static final SnapshotManager LOADING_SNAPSHOT_MANAGER = new FailingSnapshotManager.RequestingSnapshotManager();

    private static final Executor PATHING_EXECUTOR =
            new ThreadPoolExecutor(
                    Runtime.getRuntime().availableProcessors() / 4,
                    Runtime.getRuntime().availableProcessors(),
                    250L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(10000),
                    new ThreadFactoryBuilder()
                            .setDaemon(true)
                            .setNameFormat("Pathfinder Task-%d")
                            .build(),
                    new ThreadPoolExecutor.AbortPolicy()
            );

    protected final PathingRuleSet pathingRuleSet;
    protected final Offset offset;
    protected final SnapshotManager snapshotManager;

    protected AbstractPathfinder(PathingRuleSet pathingRuleSet) {
        this.pathingRuleSet = pathingRuleSet;

        this.offset = pathingRuleSet.isAllowingDiagonal() ? Offset.MERGED : Offset.VERTICAL_AND_HORIZONTAL;
        this.snapshotManager = pathingRuleSet.isLoadingChunks() ? LOADING_SNAPSHOT_MANAGER : SIMPLE_SNAPSHOT_MANAGER;
    }

    @Override
    public @NonNull CompletionStage<PathfinderResult> findPath(@NonNull PathPosition start, @NonNull PathPosition target) {
        PathfinderStrategy strategy = instantiateStrategy();
        PathingStartFindEvent startEvent = raiseStart(start, target, strategy);

        if(initialChecksFailed(start, target, startEvent))
            return CompletableFuture.completedFuture(finishPathing(new PathfinderResultImpl(PathState.FAILED, new PathImpl(start, target, EMPTY_LINKED_HASHSET))));

        return producePathing(start, target, strategy);
    }

    protected PathfinderResult finishPathing(PathfinderResult pathfinderResult) {
        EventPublisher.raiseEvent(new PathingFinishedEvent(pathfinderResult));
        return pathfinderResult;
    }

    protected SnapshotManager getSnapshotManager() {
        return snapshotManager;
    }

    private PathingStartFindEvent raiseStart(PathPosition start, PathPosition target, PathfinderStrategy strategy) {
        PathingStartFindEvent startEvent = new PathingStartFindEvent(start, target, strategy);
        EventPublisher.raiseEvent(startEvent);

        return startEvent;
    }

    private boolean initialChecksFailed(PathPosition start, PathPosition target, Cancellable startEvent) {

        if (startEvent.isCancelled() || !start.getPathEnvironment().equals(target.getPathEnvironment()) || start.isInSameBlock(target))
            return true;

        return this.pathingRuleSet.isAllowingFailFast() && isBlockUnreachable(target) || isBlockUnreachable(start);
    }

    private boolean isBlockUnreachable(PathPosition position) {
        for(PathVector vector : offset.getVectors()) {

            PathPosition offsetPosition = position.add(vector);
            PathBlock pathBlock = this.getSnapshotManager().getBlock(offsetPosition);

            if(pathBlock.isPassable())
                return false;
        }

        return true;
    }

    private PathfinderStrategy instantiateStrategy() {
        try {
            return pathingRuleSet.getStrategy().getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to instantiate PathfinderStrategy", e);
        }
    }

    private PathPosition relocateTargetPosition(PathPosition target) {
        if (pathingRuleSet.isAllowingAlternateTarget() && isBlockUnreachable(target))
            return NodeUtil.bubbleSearchAlternative(target, offset, snapshotManager).getPathPosition();
        return target;
    }

    private CompletionStage<PathfinderResult> producePathing(PathPosition start, PathPosition target, PathfinderStrategy strategy) {
        if(pathingRuleSet.isAsync())
            return produceAsyncPathing(start, target, strategy);
        return produceSyncPathing(start, target, strategy);
    }
    
    private CompletionStage<PathfinderResult> produceAsyncPathing(PathPosition start, PathPosition target, PathfinderStrategy strategy) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return findPath(start, relocateTargetPosition(target), strategy);
            } catch (Exception e) {
                log.error("Failed to find path", e);
                return new PathfinderResultImpl(PathState.FAILED, new PathImpl(start, target, EMPTY_LINKED_HASHSET));
            }
        }, PATHING_EXECUTOR).thenApply(this::finishPathing);
    }
    
    private CompletionStage<PathfinderResult> produceSyncPathing(PathPosition start, PathPosition target, PathfinderStrategy strategy) {
        try {
            return CompletableFuture.completedFuture(findPath(start, relocateTargetPosition(target), strategy));
        } catch (Exception e) {
            log.error("Failed to find path", e);
            return CompletableFuture.completedFuture(new PathfinderResultImpl(PathState.FAILED, new PathImpl(start, target, EMPTY_LINKED_HASHSET)));
        }
    }

    protected abstract PathfinderResult findPath(PathPosition start, PathPosition target, PathfinderStrategy strategy);
}
