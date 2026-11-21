import org.jacoco.core.data.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Audit 5b : à partir d'un jacoco.exec à une session par test (PerTestCoverageListener),
 * groupe les tests qui couvrent exactement les mêmes sondes du code livré, et liste les paires
 * quasi identiques. Usage : java -cp jacoco-cli.jar Dup5b.java <exec> <test-classes> <out-dir>
 */
public class Dup5b {
  public static void main(String[] args) throws Exception {
    Path testClasses = Paths.get(args[1]);
    Set<String> testClassNames;
    try (Stream<Path> s = Files.walk(testClasses)) {
      testClassNames = s.filter(p -> p.toString().endsWith(".class"))
        .map(p -> testClasses.relativize(p).toString().replace(".class", ""))
        .collect(Collectors.toSet());
    }
    Map<String, Integer> probeIndex = new HashMap<>(); // "classe#sonde" -> index global
    List<String> probeNames = new ArrayList<>();
    Map<String, BitSet> tests = new LinkedHashMap<>();
    String[] current = {null};
    try (InputStream in = new BufferedInputStream(new FileInputStream(args[0]))) {
      ExecutionDataReader r = new ExecutionDataReader(in);
      r.setSessionInfoVisitor(info -> {
        String id = info.getId();
        current[0] = id.startsWith("T|") ? id.substring(2) : null;
        if (current[0] != null) tests.computeIfAbsent(current[0], k -> new BitSet());
      });
      r.setExecutionDataVisitor(data -> {
        if (current[0] == null || testClassNames.contains(data.getName())) return;
        boolean[] p = data.getProbes();
        BitSet bits = tests.get(current[0]);
        for (int i = 0; i < p.length; i++) {
          if (!p[i]) continue;
          String key = data.getName() + "#" + i;
          Integer idx = probeIndex.get(key);
          if (idx == null) { idx = probeNames.size(); probeIndex.put(key, idx); probeNames.add(key); }
          bits.set(idx);
        }
      });
      r.read();
    }
    Path out = Paths.get(args[2]);
    Files.createDirectories(out);
    List<String> names = new ArrayList<>(tests.keySet());
    System.out.println(names.size() + " tests, " + probeNames.size() + " sondes du code livré couvertes");

    // 1. Groupes identiques
    Map<BitSet, List<String>> groups = new LinkedHashMap<>();
    for (String n : names) groups.computeIfAbsent(tests.get(n), k -> new ArrayList<>()).add(n);
    try (PrintWriter w = new PrintWriter(out.resolve("identiques.txt").toFile())) {
      int g = 0, t = 0;
      for (Map.Entry<BitSet, List<String>> e : groups.entrySet()) {
        if (e.getValue().size() < 2) continue;
        g++; t += e.getValue().size();
        w.println("## " + e.getValue().size() + " tests, " + e.getKey().cardinality() + " sondes");
        e.getValue().forEach(w::println);
        w.println();
      }
      System.out.println("identiques : " + g + " groupes, " + t + " tests");
    }

    // 2. Sondes propres à un seul test, et inclusions strictes
    int[] count = new int[probeNames.size()];
    for (BitSet b : tests.values()) for (int i = b.nextSetBit(0); i >= 0; i = b.nextSetBit(i + 1)) count[i]++;
    try (PrintWriter w = new PrintWriter(out.resolve("par-test.tsv").toFile())) {
      w.println("test\tsondes\tpropres\tinclus_dans");
      for (String n : names) {
        BitSet b = tests.get(n);
        int own = 0;
        for (int i = b.nextSetBit(0); i >= 0; i = b.nextSetBit(i + 1)) if (count[i] == 1) own++;
        String includedIn = "";
        for (String m : names) {
          if (m.equals(n)) continue;
          BitSet o = tests.get(m);
          if (o.cardinality() < b.cardinality()) continue;
          BitSet x = (BitSet) b.clone(); x.andNot(o);
          if (x.isEmpty()) { includedIn = m; break; }
        }
        w.println(n + "\t" + b.cardinality() + "\t" + own + "\t" + includedIn);
      }
    }

    // 3. Paires quasi identiques (écart symétrique ≤ 1 % de l'union, ou ≤ 20 sondes)
    try (PrintWriter w = new PrintWriter(out.resolve("proches.tsv").toFile())) {
      w.println("test_a\ttest_b\tsondes_a\tsondes_b\tseulement_a\tseulement_b");
      int pairs = 0;
      for (int i = 0; i < names.size(); i++) {
        BitSet a = tests.get(names.get(i));
        for (int j = i + 1; j < names.size(); j++) {
          BitSet b = tests.get(names.get(j));
          if (a.equals(b)) continue;
          int ca = a.cardinality(), cb = b.cardinality();
          if (Math.abs(ca - cb) > Math.max(20, 0.01 * Math.max(ca, cb))) continue;
          BitSet onlyA = (BitSet) a.clone(); onlyA.andNot(b);
          BitSet onlyB = (BitSet) b.clone(); onlyB.andNot(a);
          int diff = onlyA.cardinality() + onlyB.cardinality();
          BitSet u = (BitSet) a.clone(); u.or(b);
          if (diff <= Math.max(20, 0.01 * u.cardinality())) {
            pairs++;
            w.println(names.get(i) + "\t" + names.get(j) + "\t" + ca + "\t" + cb + "\t" + onlyA.cardinality() + "\t" + onlyB.cardinality());
          }
        }
      }
      System.out.println("paires proches : " + pairs);
    }

    // 4. Pour chaque test : sondes classées par classe, pour regarder un cas à la main
    try (ObjectOutputStream o = new ObjectOutputStream(new FileOutputStream(out.resolve("bits.ser").toFile()))) {
      o.writeObject(new ArrayList<>(probeNames));
      o.writeObject(new LinkedHashMap<>(tests));
    }
  }
}
