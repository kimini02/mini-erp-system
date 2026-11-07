#!/usr/bin/env python3
"""Read-only SQL location, javac string values and transaction boundaries.

Uses only Python's standard library and the installed JDK parser. Literal values
come from LiteralTree.getValue(), including javac text-block indentation and string
folding. Locations are literal occurrences, not physical SQL lines. No database is used.
SQL detection deliberately follows uppercase word matching; keyword-free fragments
must also be reviewed manually. Reduced counts require an explicit exact allow-list.
"""
import argparse
import base64
import collections
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

SQL = re.compile(r'\b(?:SELECT|INSERT|UPDATE|DELETE|FROM|WHERE|JOIN|SET|VALUES|ORDER BY|GROUP BY|LIMIT|FOR UPDATE|AND|OR|IN)\b')
GLOBAL_QUERIES = {'PermissionAccess', 'BatchRunRecorder', 'InvitationTokenStore', 'ScopeFilter'}
FALSE_POSITIVES = {('backend/src/main/java/com/minierp/backend/global/config/WebConfig.java', 'DELETE'): 'HTTP method name, not SQL'}
SCANNER = r'''
import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
class SqlMeasure {
    static String b64(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            List<Path> paths;
            try (var stream = Files.walk(root.resolve("backend/src/main/java"))) {
                paths = stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            }
            var task = (JavacTask) compiler.getTask(null, files, d -> {
                if (d.getKind() == Diagnostic.Kind.ERROR) throw new IllegalStateException(d.toString());
            }, List.of("-proc:none"), null, files.getJavaFileObjectsFromPaths(paths));
            for (var unit : task.parse()) {
                String file = root.relativize(Path.of(unit.getSourceFile().toUri())).toString();
                var positions = Trees.instance(task).getSourcePositions();
                new TreePathScanner<Void, Void>() {
                    Deque<String> classes = new ArrayDeque<>();
                    String method = "<class>";
                    Set<String> notificationReceivers = new HashSet<>();
                    Set<String> scopeFilterReceivers = new HashSet<>();
                    long line(Tree tree) { return unit.getLineMap().getLineNumber(positions.getStartPosition(unit, tree)); }
                    String owner() { return unit.getPackageName() + "." + String.join(".", classes); }
                    void output(String kind, Tree tree, String value) {
                        System.out.println(kind + "\t" + file + "\t" + line(tree) + "\t" + b64(owner() + "#" + method)
                                + "\t" + b64(value));
                    }
                    void transactions(ModifiersTree modifiers, Tree owner) {
                        for (var annotation : modifiers.getAnnotations()) {
                            if (annotation.getAnnotationType().toString().matches("(?:.*\\.)?Transactional")) {
                                output("T", owner, annotation.toString());
                            }
                        }
                    }
                    @Override public Void visitClass(ClassTree tree, Void unused) {
                        String previous = method;
                        method = "<class>";
                        classes.addLast(tree.getSimpleName().toString());
                        transactions(tree.getModifiers(), tree);
                        super.visitClass(tree, unused);
                        classes.removeLast();
                        method = previous;
                        return null;
                    }
                    @Override public Void visitMethod(MethodTree tree, Void unused) {
                        String previous = method;
                        method = tree.getName() + "(" + String.join(",", tree.getParameters().stream()
                                .map(p -> p.getType().toString()).toList()) + ")";
                        transactions(tree.getModifiers(), tree);
                        super.visitMethod(tree, unused);
                        method = previous;
                        return null;
                    }
                    @Override public Void visitVariable(VariableTree tree, Void unused) {
                        if (tree.getType() != null && tree.getType().toString().endsWith("NotificationService"))
                            notificationReceivers.add(tree.getName().toString());
                        if (tree.getType() != null && tree.getType().toString().matches("(?:.*\\.)?ScopeFilter"))
                            scopeFilterReceivers.add(tree.getName().toString());
                        return super.visitVariable(tree, unused);
                    }
                    @Override public Void visitLiteral(LiteralTree tree, Void unused) {
                        if (tree.getValue() instanceof String value) output("L", tree, value);
                        return super.visitLiteral(tree, unused);
                    }
                    @Override public Void visitThrow(ThrowTree tree, Void unused) {
                        output("X", tree, "throw"); return super.visitThrow(tree, unused);
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
                        String name = tree.getMethodSelect().toString();
                        String shortName = name.substring(name.lastIndexOf('.') + 1);
                        if (shortName.equals("valueOf") || shortName.startsWith("parse")) output("P", tree, name);
                        boolean scopeAccessor = tree.getArguments().isEmpty()
                                && scopeFilterReceivers.stream().anyMatch(n -> name.equals(n + ".scope"));
                        if ((Set.of("now", "actor", "scope").contains(shortName) && !scopeAccessor)
                                || name.startsWith("BodyValues.") || name.startsWith("LeaveGrantRules.")
                                || name.startsWith("NotificationService.")
                                || notificationReceivers.stream().anyMatch(n -> name.startsWith(n + "."))) output("V", tree, name);
                        return super.visitMethodInvocation(tree, unused);
                    }
                }.scan(unit, null);
            }
        }
    }
}
'''


def scan(root):
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
    if not java:
        raise SystemExit('Set JAVA_HOME to the installed JDK 17+ runtime')
    with tempfile.TemporaryDirectory(prefix='r03-sql-measure-') as td:
        source = Path(td) / 'SqlMeasure.java'
        source.write_text(SCANNER, encoding='utf-8')
        run = subprocess.run([java, str(source), str(root)], capture_output=True, text=True, check=True)
    records = []
    for line in run.stdout.splitlines():
        kind, file, number, owner, value = line.split('\t')
        records.append({'kind': kind, 'file': file, 'line': int(number),
                        'owner': base64.b64decode(owner).decode('utf-8'),
                        'value': base64.b64decode(value).decode('utf-8')})
    return records


def summarize(records):
    outside = []
    fragments = []
    false_positives = []
    literals = [r for r in records if r['kind'] == 'L']
    for record in literals:
        file = record['file']
        allowed = ('/repository/' in file or '/global/persistence/' in file
                   or '/global/' in file and Path(file).stem in GLOBAL_QUERIES)
        if (file, record['value']) in FALSE_POSITIVES:
            false_positives.append({**record, 'reason': FALSE_POSITIVES[(file, record['value'])]})
            continue
        if (not allowed and not SQL.search(record['value'])
                and re.search(r'/domain/(?:attendance|home|notification|project|task)/(?:service|handler)/', file)
                and (re.search(r'(?<![\w:]):[A-Za-z_]\w*', record['value']) or re.fullmatch(r'\s*\w+(?:\.\w+)?\s*(?:=|<>|<=|>=|<|>)\s*\w+\s*', record['value']))):
            fragments.append(record)
        if SQL.search(record['value']) and not allowed:
            item = dict(record)
            item['domain'] = file.split('/domain/')[1].split('/')[0] if '/domain/' in file else 'global'
            outside.append(item)
    domains = {}
    for domain in sorted({r['domain'] for r in outside}):
        items = [r for r in outside if r['domain'] == domain]
        files = collections.Counter(r['file'] for r in items)
        domains[domain] = {'sqlValues': len(items), 'files': len(files), 'byFile': dict(sorted(files.items()))}
    return {'literalCount': len(literals), 'uniqueLiteralValues': len({r['value'] for r in literals}),
            'outsideSqlValues': len(outside), 'outsideSqlFiles': len({r['file'] for r in outside}),
            'domains': domains, 'outside': outside, 'falsePositives': false_positives, 'keywordFreeFragments': fragments,
            'transactions': sorted(({'owner': r['owner'], 'annotation': r['value']}
                                   for r in records if r['kind'] == 'T'), key=lambda r: (r['owner'], r['annotation']))}


def compare(before, after, allowed_reductions, rename_classes=None):
    rename_classes = rename_classes or {}
    old = collections.Counter(r['value'] for r in before if r['kind'] == 'L')
    new = collections.Counter(r['value'] for r in after if r['kind'] == 'L')
    reduced = {value: count - new[value] for value, count in old.items() if new[value] < count}
    increased = {value: count - old[value] for value, count in new.items() if count > old[value]}
    added = sorted(new.keys() - old.keys())
    removed = sorted(old.keys() - new.keys())
    old_tx = summarize(before)['transactions']
    for item in old_tx:
        cls, method = item['owner'].split('#', 1)
        item['owner'] = rename_classes.get(cls, cls) + '#' + method
    old_tx.sort(key=lambda r: (r['owner'], r['annotation']))
    new_tx = summarize(after)['transactions']
    unapproved = {v: count for v, count in reduced.items() if allowed_reductions.get(v) != count}
    unused = {v: count for v, count in allowed_reductions.items() if reduced.get(v) != count}
    return {'sameValueSet': not added and not removed, 'addedValues': added, 'removedValues': removed,
            'reducedOccurrences': reduced, 'increasedOccurrences': increased,
            'unapprovedReductions': unapproved, 'unusedReductionApprovals': unused,
            'sameTransactions': old_tx == new_tx,
            'beforeTransactionCount': len(old_tx), 'afterTransactionCount': len(new_tx)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--baseline', type=Path)
    parser.add_argument('--allow-count-reductions', type=Path,
                        help='JSON object: literal value -> exact reduction justified by merged identical SQL')
    parser.add_argument('--check-repository', action='append', default=[],
                        help='Changed repository source path, relative to root; repeat for each file')
    parser.add_argument('--rename-class', action='append', default=[], help='old fully qualified class=new class')
    parser.add_argument('--preserve-repository', action='append', default=[], help='old source path=new repository path; exact existing exceptions only')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    missing = [p for p in args.check_repository if not (args.root / p).is_file()]
    if missing:
        parser.error('Missing repository files: ' + ', '.join(missing))
    records = scan(args.root.resolve())
    result = summarize(records)
    violations = [r for r in records if r['file'] in args.check_repository and r['kind'] in ('T', 'X', 'P', 'V')]
    baseline_records = scan(args.baseline.resolve()) if args.baseline else []
    moves = dict(x.split('=', 1) for x in args.preserve_repository)
    existing = []
    allowed = {
        ('AttendanceStore.java', 'time(Object)', 'P', 'LocalTime.parse'),
        ('ProjectStateStore.java', 'recalculate(long)', 'T', '@Transactional(propagation = Propagation.MANDATORY)'),
        ('ProjectStateStore.java', 'recalculate(long)', 'V', 'LocalDateTime.now'),
    }
    for row in violations[:]:
        key = (Path(row['file']).name, row['owner'].split('#', 1)[1], row['kind'], row['value'])
        originals = [old for old, new in moves.items() if new == row['file']]
        if key in allowed and any(old['file'] in originals and old['kind'] == row['kind']
                and old['value'] == row['value'] and old['owner'].split('#', 1)[1] == key[1] for old in baseline_records):
            existing.append(row)
            violations.remove(row)
    result['preservedRepositoryViolations'] = existing
    result['repositoryViolations'] = violations
    failed = bool(violations)
    if args.baseline:
        allowed = json.loads(args.allow_count_reductions.read_text()) if args.allow_count_reductions else {}
        comparison = compare(baseline_records, records, allowed, dict(x.split("=", 1) for x in args.rename_class))
        result['comparison'] = comparison
        failed |= (not comparison['sameValueSet'] or not comparison['sameTransactions']
                   or bool(comparison['unapprovedReductions']) or bool(comparison['unusedReductionApprovals']))
    text = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.write_text(text, encoding='utf-8')
    print(json.dumps({key: value for key, value in result.items()
                      if key not in ('outside', 'transactions', 'comparison')}, ensure_ascii=False))
    if 'comparison' in result:
        c = result['comparison']
        print(json.dumps({key: (len(value) if isinstance(value, (dict, list)) else value)
                          for key, value in c.items()}, ensure_ascii=False))
    raise SystemExit(1 if failed else 0)


if __name__ == '__main__':
    main()
