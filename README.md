## Core Components

1. **Cache Interface**: Defines the basic operations with operator overloading for intuitive syntax
2. **PerpetualCache**: Simple HashMap-based implementation that stores items forever
3. **LRUCache**: Decorator that implements Least Recently Used eviction policy
4. **ExpirableCache**: Time-based cache that clears all entries after a specified interval
5. **TTLCache**: Advanced cache with per-entry Time To Live support
6. **SynchronizedCache**: Thread-safe wrapper for concurrent access

## Advanced Features

- **Builder Pattern**: Easy configuration with `CacheBuilder`
- **Factory Methods**: Convenient creation through `CacheFactory`
- **Type Safety**: Extension functions for type-safe operations
- **Decorator Pattern**: Composable cache behaviors that can be stacked

## Usage Examples

```kotlin
// Simple cache
val cache = CacheFactory.perpetual()
cache["key"] = "value"

// LRU cache with max 100 items
val lruCache = CacheFactory.lru(100)

// Cache with 5-minute TTL per entry
val ttlCache = CacheFactory.ttl(TimeUnit.MINUTES.toMillis(5))

// Complex cache with multiple features
val advancedCache = CacheFactory.builder()
    .maxSize(50)
    .ttl(30, TimeUnit.SECONDS)
    .threadSafe()
    .build()
```
