package org.patheloper.model.pathing.pathfinder;

import java.util.*;
import org.jheaps.tree.FibonacciHeap;
import org.patheloper.api.pathing.configuration.PathfinderConfiguration;
import org.patheloper.api.pathing.filter.PathFilter;
import org.patheloper.api.pathing.filter.PathFilterStage;
import org.patheloper.api.pathing.filter.PathValidationContext;
import org.patheloper.api.wrapper.PathPosition;
import org.patheloper.api.wrapper.PathVector;
import org.patheloper.model.pathing.Node;
import org.patheloper.model.pathing.Offset;
import org.patheloper.util.ExpiringHashMap;
import org.patheloper.util.GridRegionData;
import org.patheloper.util.Tuple3;
import org.patheloper.util.WatchdogUtil;

public class AStarPathfinder extends AbstractPathfinder {

  private static final int DEFAULT_GRID_CELL_SIZE = 12;
  private static final int PRIORITY_BOOST_IN_PERCENTAGE = 80;

  /**
   * The grid map used to store the regional examined positions and Bloom filters for each grid
   * region.
   */
  private final Map<Tuple3<Integer>, ExpiringHashMap.Entry<GridRegionData>> gridMap =
      new ExpiringHashMap<>();

  public AStarPathfinder(PathfinderConfiguration pathfinderConfiguration) {
    super(pathfinderConfiguration);
  }

  @Override
  protected void tick(
      PathPosition start,
      PathPosition target,
      Node currentNode,
      Depth depth,
      FibonacciHeap<Double, Node> nodeQueue,
      Set<PathPosition> examinedPositions,
      List<PathFilter> filters,
      List<PathFilterStage> filterStages) {

    tickWatchdogIfNeeded(depth);

    evaluateNewNodes(
        nodeQueue,
        examinedPositions,
        currentNode,
        filters,
        filterStages,
        this.pathfinderConfiguration.isAllowingDiagonal());
    depth.increment();
  }

  private void tickWatchdogIfNeeded(Depth depth) {
    if (depth.getDepth() % 500 == 0) {
      WatchdogUtil.tickWatchdog();
    }
  }

  private void evaluateNewNodes(
      FibonacciHeap<Double, Node> nodeQueue,
      Set<PathPosition> examinedPositions,
      Node currentNode,
      List<PathFilter> filters,
      List<PathFilterStage> filterStages,
      boolean allowingDiagonal) {

    Collection<Node> newNodes =
        fetchValidNeighbours(
            examinedPositions, currentNode, filters, filterStages, allowingDiagonal);

    for (Node newNode : newNodes) {
      double nodeCost = newNode.getHeuristic().get();
      if (pathfinderConfiguration.isPrioritizing()) {
        double priorityAdjustment = calculatePriorityAdjustment(newNode, filterStages);
        nodeCost -= priorityAdjustment;
      }
      nodeQueue.insert(nodeCost, newNode);
    }
  }

  private double calculatePriorityAdjustment(Node node, List<PathFilterStage> filterStages) {
    for (PathFilterStage filterStage : filterStages) {
      boolean filterResult =
          filterStage.filter(
              new PathValidationContext(
                  node.getPosition(),
                  node.getParent() != null ? node.getParent().getPosition() : null,
                  snapshotManager));

      if (filterResult) {
        return node.getHeuristic().get() * (PRIORITY_BOOST_IN_PERCENTAGE / 100.0);
      }
    }
    return 0.0;
  }

  private boolean isNodeValid(
      Node currentNode,
      Node newNode,
      Set<PathPosition> examinedPositions,
      List<PathFilter> filters,
      List<PathFilterStage> filterStages,
      boolean allowingDiagonal) {

    if (isNodeInvalid(newNode, filters, filterStages)) return false;

    if (!allowingDiagonal) return examinedPositions.add(newNode.getPosition());

    if (!isDiagonalMove(currentNode, newNode)) return examinedPositions.add(newNode.getPosition());

    return isReachable(currentNode, newNode, filters, filterStages)
        && examinedPositions.add(newNode.getPosition());
  }

  private boolean isDiagonalMove(Node from, Node to) {
    int xDifference = Math.abs(from.getPosition().getBlockX() - to.getPosition().getBlockX());
    int zDifference = Math.abs(from.getPosition().getBlockZ() - to.getPosition().getBlockZ());

    return xDifference != 0 && zDifference != 0;
  }

  /**
   * Returns whether the diagonal jump is possible by checking if the adjacent nodes are passable or
   * not. With adjacent nodes are the shared overlapping neighbours meant.
   */
  private boolean isReachable(
      Node from, Node to, List<PathFilter> filters, List<PathFilterStage> filterStages) {
    boolean hasYDifference = from.getPosition().getBlockY() != to.getPosition().getBlockY();
    PathVector[] offsets = Offset.VERTICAL_AND_HORIZONTAL.getVectors();

    for (PathVector vector1 : offsets) {
      if (vector1.getY() != 0) continue;

      Node neighbour1 = createNeighbourNode(from, vector1);
      for (PathVector vector2 : offsets) {
        if (vector2.getY() != 0) continue;

        Node neighbour2 = createNeighbourNode(to, vector2);
        if (neighbour1.getPosition().equals(neighbour2.getPosition())) {

          /*
           * if it has a Y difference, we also need to check the nodes above or below,
           *  depending on the Y difference
           */
          boolean heightDifferencePassable =
              isHeightDifferencePassable(from, to, vector1, hasYDifference);

          if (doAllFiltersPass(filters, neighbour1)
              && (!pathfinderConfiguration.isPrioritizing()
                  && doAnyFilterStagePass(filterStages, neighbour1))
              && heightDifferencePassable) return true;
        }
      }
    }

    return false;
  }

  private boolean isHeightDifferencePassable(
      Node from, Node to, PathVector vector1, boolean hasHeightDifference) {
    if (!hasHeightDifference) return true;

    int yDifference = from.getPosition().getBlockY() - to.getPosition().getBlockY();
    Node neighbour3 = createNeighbourNode(from, vector1.add(new PathVector(0, yDifference, 0)));

    return snapshotManager.getBlock(neighbour3.getPosition()).isPassable();
  }

  private Collection<Node> fetchValidNeighbours(
      Set<PathPosition> examinedPositions,
      Node currentNode,
      List<PathFilter> filters,
      List<PathFilterStage> filterStages,
      boolean allowingDiagonal) {
    Set<Node> newNodes = new HashSet<>(offset.getVectors().length);

    for (PathVector vector : offset.getVectors()) {
      Node newNode = createNeighbourNode(currentNode, vector);

      if (isNodeValid(
          currentNode, newNode, examinedPositions, filters, filterStages, allowingDiagonal)) {
        newNodes.add(newNode);
      }
    }

    return newNodes;
  }

  private Node createNeighbourNode(Node currentNode, PathVector offset) {
    Node newNode =
        new Node(
            currentNode.getPosition().add(offset),
            currentNode.getStart(),
            currentNode.getTarget(),
            pathfinderConfiguration.getHeuristicWeights(),
            currentNode.getDepth() + 1);
    newNode.setParent(currentNode);
    return newNode;
  }

  /**
   * Checks if the node is invalid. A node is invalid if it is outside the world bounds or is not
   * valid according to the filters.
   */
  private boolean isNodeInvalid(
      Node node, List<PathFilter> filters, List<PathFilterStage> filterStages) {

    int gridX = node.getPosition().getBlockX() / DEFAULT_GRID_CELL_SIZE;
    int gridY = node.getPosition().getBlockY() / DEFAULT_GRID_CELL_SIZE;
    int gridZ = node.getPosition().getBlockZ() / DEFAULT_GRID_CELL_SIZE;

    GridRegionData regionData =
        gridMap
            .computeIfAbsent(
                new Tuple3<>(gridX, gridY, gridZ),
                k -> new ExpiringHashMap.Entry<>(new GridRegionData()))
            .getValue();

    regionData.getRegionalExaminedPositions().add(node.getPosition());

    if (regionData.getBloomFilter().mightContain(node.getPosition())) {
      if (regionData.getRegionalExaminedPositions().contains(node.getPosition())) {
        return true; // Node is invalid if already examined
      }
    }

    if (!isWithinWorldBounds(node.getPosition())) {
      return true; // Node is invalid if out of bounds
    }

    boolean filtersPass = doAllFiltersPass(filters, node);
    boolean stagesPass = doAnyFilterStagePass(filterStages, node);

    if (!filtersPass) {
      return true; // Node is invalid if filters fail
    }

    return !pathfinderConfiguration.isPrioritizing() && !stagesPass;
  }

  private boolean doAllFiltersPass(List<PathFilter> filters, Node node) {
    for (PathFilter filter : filters) {
      PathValidationContext context =
          new PathValidationContext(
              node.getPosition(),
              node.getParent() != null ? node.getParent().getPosition() : null,
              snapshotManager);

      if (!filter.filter(context)) {
        return false;
      }
    }
    return true;
  }

  private boolean doAnyFilterStagePass(List<PathFilterStage> filterStages, Node node) {
    if (filterStages.isEmpty()) return true;

    for (PathFilterStage filterStage : filterStages) {
      if (filterStage.filter(
          new PathValidationContext(
              node.getPosition(),
              node.getParent() != null ? node.getParent().getPosition() : null,
              snapshotManager))) {
        return true;
      }
    }
    return false;
  }

  private boolean isWithinWorldBounds(PathPosition position) {
    return position.getPathEnvironment().getMinHeight() < position.getBlockY()
        && position.getBlockY() < position.getPathEnvironment().getMaxHeight();
  }
}
