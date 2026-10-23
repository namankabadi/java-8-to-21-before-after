# Java 8 → 21: Code Snippets Before/After

Examples of 20 Java upgrades, each with the old way, the modern way,
and real output. Verified on JDK 21.

## Run
Requires JDK 21 or later.

    java src/BeforeAfterDemo.java

## Notes
- Timings (snippets 15, 16) and the cache load count (snippet 18) vary per machine.
- Everything else is deterministic.

## Topics covered
Records, pattern matching, sealed types, switch expressions, text blocks,
isBlank, List.of, Stream.toList, groupingBy, Map.merge, Optional, Comparator,
getLast, Files.readString, virtual threads, CompletableFuture, LongAdder,
computeIfAbsent, java.time, try-with-resources.
