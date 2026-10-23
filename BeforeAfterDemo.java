import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.stream.Collectors.groupingBy;

/**
 * Java 8 to 21: 20 before/after upgrades, each one runnable with real output.
 * Tested on JDK 21.   Run:  java BeforeAfterDemo.java
 * (Timings and race counts vary per machine; everything else is deterministic.)
 */
public class BeforeAfterDemo {

    // ---------- shared helper types ----------
    record Person(String name, int age) {}
    sealed interface Shape permits Circle, Square {}
    record Circle(double r) implements Shape {}
    record Square(double side) implements Shape {}

    static class Res implements AutoCloseable {
        public void close() { throw new IllegalStateException("close failed"); }
    }
    static void doWork() { throw new IllegalStateException("work failed"); }
    static void blockingCall() { sleep(50); }
    static String fetchUser() { sleep(200); return "Asha"; }
    static String fetchOrders() { sleep(200); return "3 orders"; }
    static String findCity(int id) { return null; }
    static final AtomicInteger LOADS = new AtomicInteger();
    static String load(String key) { sleep(50); LOADS.incrementAndGet(); return key.toUpperCase(); }
    static void sleep(int ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { throw new RuntimeException(e); } }
    static String fmt(double d) { return String.format("%.2f", d); }

    // ---------- "before" (legacy) versions that need their own types ----------
    static final class Legacy {
        static final class User {
            private final String name;
            private final int age;
            User(String name, int age) { this.name = name; this.age = age; }
            String name() { return name; }
            int age() { return age; }
            @Override public boolean equals(Object o) {
                if (!(o instanceof User)) return false;
                User u = (User) o;
                return age == u.age && name.equals(u.name);
            }
            @Override public int hashCode() { return Objects.hash(name, age); }
            @Override public String toString() { return "User{name=" + name + ", age=" + age + "}"; }
        }

        static double area(Shape s) {
            if (s instanceof Circle) {
                Circle c = (Circle) s;
                return Math.PI * c.r() * c.r();
            } else if (s instanceof Square) {
                Square q = (Square) s;
                return q.side() * q.side();
            }
            throw new IllegalArgumentException();
        }

        static class Counter {
            private long count;
            void hit() { synchronized (this) { count++; } }
            long get() { synchronized (this) { return count; } }
        }
    }

    record User(String name, int age) {}

    static double area(Shape s) {
        return switch (s) {
            case Circle c -> Math.PI * c.r() * c.r();
            case Square q -> q.side() * q.side();
        };
    }

    static class Counter {
        private final LongAdder count = new LongAdder();
        void hit() { count.increment(); }
        long get() { return count.sum(); }
    }

    // ---------- 1 to 20 ----------
    static String[] s01() {
        var a = new Legacy.User("Asha", 30); var b = new Legacy.User("Asha", 30);
        var c = new User("Asha", 30);        var d = new User("Asha", 30);
        return new String[]{a + "  equals=" + a.equals(b), c + "  equals=" + c.equals(d)};
    }

    static String capture(Runnable r) {
        PrintStream old = System.out; var buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf));
        try { r.run(); } finally { System.setOut(old); }
        return buf.toString().trim();
    }
    static String[] s02() {
        Object obj = "hello";
        String before = capture(() -> {
            if (obj instanceof String) {
                String s = (String) obj;
                if (s.length() > 3) System.out.println(s);
            }
        });
        String after = capture(() -> {
            if (obj instanceof String s && s.length() > 3) {
                System.out.println(s);
            }
        });
        return new String[]{before, after};
    }

    static String[] s03() {
        return new String[]{fmt(Legacy.area(new Circle(5))) + " / " + fmt(Legacy.area(new Square(4))),
                fmt(area(new Circle(5))) + " / " + fmt(area(new Square(4)))};
    }

    static String typeBefore(String day) {
        String type;
        switch (day) {
            case "SAT": case "SUN": type = "WEEKEND"; break;
            default: type = "WEEKDAY";
        }
        return type;
    }
    static String typeAfter(String day) {
        String type = switch (day) {
            case "SAT", "SUN" -> "WEEKEND";
            default -> "WEEKDAY";
        };
        return type;
    }
    static String[] s04() {
        return new String[]{"SAT=" + typeBefore("SAT") + ", MON=" + typeBefore("MON"),
                "SAT=" + typeAfter("SAT") + ", MON=" + typeAfter("MON")};
    }

    static String[] s05() {
        String old, neu;
        {
            String sql = "SELECT id, name\n"
                    + "FROM users\n"
                    + "WHERE active = 1";
            old = sql;
        }
        {
            String sql = """
                    SELECT id, name
                    FROM users
                    WHERE active = 1""";
            neu = sql;
        }
        return new String[]{old.replace("\n", " | "), neu.replace("\n", " | ") + "   (identical string: " + old.equals(neu) + ")"};
    }

    static String[] s06() {
        String[] r = new String[2];
        {
            String input = "\u2003";  // em space
            boolean blank = input.trim().isEmpty();
            r[0] = "trim().isEmpty() = " + blank;
        }
        {
            String input = "\u2003";  // em space
            boolean blank = input.isBlank();
            r[1] = "isBlank() = " + blank;
        }
        return r;
    }

    static String[] s07() {
        String[] r = new String[2];
        {
            List<String> roles = Collections.unmodifiableList(
                    Arrays.asList("ADMIN", "USER"));
            r[0] = roles + "  " + tryAdd(roles);
        }
        {
            List<String> roles = List.of("ADMIN", "USER");
            r[1] = roles + "  " + tryAdd(roles);
        }
        return r;
    }
    static String tryAdd(List<String> l) {
        try { l.add("X"); return "add() works"; } catch (UnsupportedOperationException e) { return "add() -> UnsupportedOperationException"; }
    }

    static String[] s08() {
        List<Integer> numbers = List.of(1, 2, 3, 4);
        String[] r = new String[2];
        {
            List<Integer> evens = numbers.stream()
                    .filter(n -> n % 2 == 0)
                    .collect(Collectors.toList());
            r[0] = evens + "  " + tryAddInt(evens);
        }
        {
            List<Integer> evens = numbers.stream()
                    .filter(n -> n % 2 == 0)
                    .toList();
            r[1] = evens + "  " + tryAddInt(evens);
        }
        return r;
    }
    static String tryAddInt(List<Integer> l) {
        try { l.add(6); return "add(6) works (modifiable)"; } catch (UnsupportedOperationException e) { return "add(6) -> UnsupportedOperationException"; }
    }

    static String[] s09() {
        List<String> words = List.of("cat", "dog", "horse");
        String[] r = new String[2];
        {
            Map<Integer, List<String>> byLength = new HashMap<>();
            for (String w : words) {
                List<String> list = byLength.get(w.length());
                if (list == null) {
                    list = new ArrayList<>();
                    byLength.put(w.length(), list);
                }
                list.add(w);
            }
            r[0] = byLength.toString();
        }
        {
            Map<Integer, List<String>> byLength = words.stream()
                    .collect(groupingBy(String::length));
            r[1] = byLength.toString();
        }
        return r;
    }

    static String[] s10() {
        List<String> words = List.of("a", "b", "a");
        String[] r = new String[2];
        {
            Map<String, Integer> counts = new TreeMap<>();
            for (String word : words) {
                Integer c = counts.get(word);
                counts.put(word, c == null ? 1 : c + 1);
            }
            r[0] = counts.toString();
        }
        {
            Map<String, Integer> counts = new TreeMap<>();
            for (String word : words) {
                counts.merge(word, 1, Integer::sum);
            }
            r[1] = counts.toString();
        }
        return r;
    }

    static String[] s11() {
        List<String> list = new ArrayList<>(List.of("A", "B", "C"));
        List<String> empty = new ArrayList<>();
        String[] r = new String[2];
        {
            String last = list.get(list.size() - 1);
            r[0] = last + "   empty list -> " + catches(() -> empty.get(empty.size() - 1));
        }
        {
            String last = list.getLast();
            r[1] = last + "   empty list -> " + catches(() -> empty.getLast());
        }
        return r;
    }
    static String catches(Runnable r) {
        try { r.run(); return "no error"; } catch (RuntimeException e) { return e.getClass().getSimpleName(); }
    }

    static String[] s12() {
        int id = 7;
        String[] r = new String[2];
        {
            String city = findCity(id);   // may return null
            if (city == null || city.isEmpty()) {
                city = "Unknown";
            }
            r[0] = city;
        }
        {
            String city = Optional.ofNullable(findCity(id))
                    .filter(c -> !c.isEmpty())
                    .orElse("Unknown");
            r[1] = city;
        }
        return r;
    }

    static String[] s13() {
        List<Person> data = List.of(new Person("Ravi", 30), new Person("Asha", 25), new Person("Zoya", 30));
        String[] r = new String[2];
        {
            List<Person> people = new ArrayList<>(data);
            Collections.sort(people, new Comparator<Person>() {
                @Override
                public int compare(Person a, Person b) {
                    int byAge = Integer.compare(b.age(), a.age());
                    return byAge != 0 ? byAge : a.name().compareTo(b.name());
                }
            });
            r[0] = people.stream().map(Person::name).toList().toString();
        }
        {
            List<Person> people = new ArrayList<>(data);
            people.sort(Comparator.comparingInt(Person::age).reversed()
                    .thenComparing(Person::name));
            r[1] = people.stream().map(Person::name).toList().toString();
        }
        return r;
    }

    static String[] s14() throws Exception {
        Path path = Files.createTempFile("demo", ".txt");
        Files.writeString(path, "Hello\nJava");
        File file = path.toFile();
        String[] r = new String[2];
        {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            String text = sb.toString();
            r[0] = "\"" + text.replace("\n", "\\n") + "\"";
        }
        {
            String text = Files.readString(path);
            r[1] = "\"" + text.replace("\n", "\\n") + "\"";
        }
        return r;
    }

    static String[] s15() {
        long t0 = System.nanoTime();
        try (var executor = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 200; i++) {
                executor.submit(() -> { Thread.sleep(50); return 1; });
            }
        }
        long fixed = (System.nanoTime() - t0) / 1_000_000;
        t0 = System.nanoTime();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 200; i++) {
                executor.submit(() -> { Thread.sleep(50); return 1; });
            }
        }
        long virt = (System.nanoTime() - t0) / 1_000_000;
        return new String[]{"~" + fixed + " ms", "~" + virt + " ms"};
    }

    static String[] s16() {
        String[] r = new String[2];
        long t0 = System.nanoTime();
        {
            String user = fetchUser();      // 200 ms
            String orders = fetchOrders();  // 200 ms
            String page = user + " | " + orders;
            r[0] = page + "  in ~" + (System.nanoTime() - t0) / 1_000_000 + " ms";
        }
        t0 = System.nanoTime();
        {
            String page = supplyAsync(() -> fetchUser())
                    .thenCombine(supplyAsync(() -> fetchOrders()),
                            (u, o) -> u + " | " + o)
                    .join();
            r[1] = page + "  in ~" + (System.nanoTime() - t0) / 1_000_000 + " ms";
        }
        return r;
    }

    static String[] s17() throws Exception {
        Legacy.Counter oldC = new Legacy.Counter(); Counter newC = new Counter();
        Thread[] ts = new Thread[4];
        for (int i = 0; i < 4; i++) {
            ts[i] = new Thread(() -> { for (int k = 0; k < 100_000; k++) { oldC.hit(); newC.hit(); } });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        return new String[]{"total = " + oldC.get(), "total = " + newC.get()};
    }

    static void before18(Map<String, String> cache, String key) {
        String v = cache.get(key);
        if (v == null) {
            v = load(key);          // may run many times
            cache.put(key, v);
        }
    }
    static void after18(Map<String, String> cache, String key) {
        String v = cache.computeIfAbsent(key, k -> load(k));
    }
    static String[] s18() {
        String[] res = new String[2];
        for (int mode = 0; mode < 2; mode++) {
            Map<String, String> cache = new ConcurrentHashMap<>();
            LOADS.set(0);
            try (var ex = Executors.newFixedThreadPool(8)) {
                for (int i = 0; i < 8; i++) {
                    if (mode == 0) ex.submit(() -> before18(cache, "user:7"));
                    else ex.submit(() -> after18(cache, "user:7"));
                }
            }
            res[mode] = "loader ran " + LOADS.get() + (LOADS.get() == 1 ? " time" : " times");
        }
        return res;
    }

    static String[] s19() {
        String[] r = new String[2];
        {
            Calendar cal = new GregorianCalendar(2026, Calendar.OCTOBER, 23);
            cal.add(Calendar.DAY_OF_MONTH, 30);
            String due = new SimpleDateFormat("dd MMM yyyy").format(cal.getTime());
            r[0] = due;
        }
        {
            String due = LocalDate.of(2026, 10, 23).plusDays(30)
                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
            r[1] = due;
        }
        return r;
    }

    static String before20() {
        try {
            Res res = new Res();
            try {
                doWork();           // throws "work failed"
            } finally {
                res.close();        // throws "close failed"
            }
        } catch (Exception e) { return "caught: " + e.getMessage(); }
        return "";
    }
    @SuppressWarnings("try")
    static String after20() {
        try {
            try (Res res = new Res()) {
                doWork();           // throws "work failed"
            }
        } catch (Exception e) { return "caught: " + e.getMessage() + " (suppressed: " + e.getSuppressed()[0].getMessage() + ")"; }
        return "";
    }
    static String[] s20() { return new String[]{before20(), after20()}; }

    // ---------- runner ----------
    interface Snippet { String[] run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ENGLISH);
        String[] titles = {"Record vs boilerplate class", "Pattern matching for instanceof", "Sealed types + switch patterns",
                "Switch expression", "Text block", "isBlank vs trim().isEmpty()", "List.of", "Stream.toList()", "groupingBy",
                "Map.merge", "getLast()", "Optional", "Comparator chaining", "Files.readString", "Virtual threads (200 x 50 ms I/O)",
                "Parallel calls with CompletableFuture", "LongAdder (4 threads x 100,000)", "computeIfAbsent (8 concurrent calls)",
                "java.time vs Calendar", "try-with-resources"};
        Snippet[] all = {BeforeAfterDemo::s01, BeforeAfterDemo::s02, BeforeAfterDemo::s03, BeforeAfterDemo::s04, BeforeAfterDemo::s05,
                BeforeAfterDemo::s06, BeforeAfterDemo::s07, BeforeAfterDemo::s08, BeforeAfterDemo::s09, BeforeAfterDemo::s10,
                BeforeAfterDemo::s11, BeforeAfterDemo::s12, BeforeAfterDemo::s13, BeforeAfterDemo::s14, BeforeAfterDemo::s15,
                BeforeAfterDemo::s16, BeforeAfterDemo::s17, BeforeAfterDemo::s18, BeforeAfterDemo::s19, BeforeAfterDemo::s20};
        for (int i = 0; i < all.length; i++) {
            String[] r = all[i].run();
            System.out.printf("%n%2d. %s%n    BEFORE: %s%n    AFTER : %s%n", i + 1, titles[i], r[0], r[1]);
        }
        System.out.println("\nAll 20 snippets ran. (Timings and race counts vary per machine.)");
    }
}