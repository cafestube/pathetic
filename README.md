# Pathetic

Pathetic is a simple and intuitive backwards-compatible up-to-date pathfinding API for Spigot and forks.
See more info here: https://www.spigotmc.org/threads/how-pathetic.578998/#post-4644823

Note on our Fork: We use paper plugins and these are expected to not be remapped on 1.20.5 or later.
Therefore, we have modified the plugin to build with paper userdev and get a non-remapped jar.

### How to import

#### Maven
```xml
	<repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
	</repositories>
 
	<dependency>
	    <groupId>com.github.patheloper.pathetic</groupId>
	    <artifactId>pathetic-mapping</artifactId>
	    <version>VERSION</version>
	</dependency>
```

#### Gradle
```xml
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
    
	dependencies {
	        implementation 'com.github.patheloper.pathetic:pathetic-mapping:VERSION'
	}
```

### Example API Usage
```java
public class PathExample extends JavaPlugin {

  @Override
  public void onEnable() {
    PatheticMapper.initialize(this);
    goFindSomePath(randomLocation(), randomLocation());
  }

  @Override
  public void onDisable() {
    PatheticMapper.shutdown();
  }

  private void goFindSomePath(PathPosition start, PathPosition end) {
    Pathfinder pathfinder = PatheticMapper.newPathfinder();
    pathfinder
        .findPath(start, end, new DirectPathfinderStrategy())
        .thenAccept(
            pathfinderResult ->
                pathfinderResult
                    .getPath()
                    .forEach(
                        location ->
                            player.sendBlockChange(
                                location, Material.YELLOW_STAINED_GLASS.createBlockData())));
  }

  private PathPosition randomLocation() {
    ThreadLocalRandom instance = ThreadLocalRandom.current();
    return new PathPosition(
        instance.nextInt(0, 100), instance.nextInt(0, 100), instance.nextInt(0, 100));
  }
}

```

#### See the `pathetic-example` module for a more in-depth example plugin.

### Docs:
Access the Javadocs [here](https://javadocs.pathetic.ollieee.xyz/)
Access our Docs [here](https://docs.pathetic.ollieee.xyz/)
