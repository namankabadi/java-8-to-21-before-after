# Java 8 → 21: Code Snippets Before/After

Examples of 20 Java upgrades, each with the old approach, the modern approach, and sample output. Tested on JDK 21.

## Run

Requires JDK 21 or later.

```bash
java src/BeforeAfterDemo.java
```

## Notes

- Timings (snippets 15 and 16) vary by machine and runtime.
- Snippet 15 uses `Thread.sleep()` to simulate blocking waits, not actual I/O.
- The cache load count in snippet 18 can vary between runs.
- Other outputs are deterministic for the included inputs.

## Topics Covered

Records, pattern matching, sealed types, switch expressions, text blocks, `isBlank()`, `List.of()`, `Stream.toList()`, `groupingBy()`, `Map.merge()`, `Optional`, Comparator chaining, `getLast()`, `Files.readString()`, virtual threads, `CompletableFuture`, `LongAdder`, `computeIfAbsent()`, `java.time`, and try-with-resources.
