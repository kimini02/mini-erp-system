#!/usr/bin/env python3
"""Read-only group-3 verification of compiled external source archives.

Use --before=d693843 archive and --after=a domain commit archive. Both archives
must be test-compiled with Java 17. --renames/--responses are committed group3
mapping JSON files. This tool uses only the JDK and Python standard library.
It never runs application tests, accesses a database, or edits either archive.
"""
import argparse, collections, hashlib, importlib.util, json, re, subprocess, sys
from pathlib import Path
sys.dont_write_bytecode = True
TOOLS = Path(__file__).parent

def module(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + '.py'))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['before', 'after', 'classpath', 'renames', 'responses', 'output']:
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    def save(name, value):
        (out / (name + '.json')).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')
    analysis = module('R-04-java-analysis')
    before = analysis.analyze(args.before, args.classpath, cache=out / 'cache')
    after = analysis.analyze(args.after, args.classpath, cache=out / 'cache')
    renames = json.loads(args.renames.read_text())
    responses = json.loads(args.responses.read_text())
    result = {}
    for name, value in [
        ('order', module('R-04-call-order-check').compare(before, after, renames)),
        ('sql', module('R-04-sql-literal-check').compare(args.before, args.after, args.classpath)),
        ('response', module('R-04-response-check').check(before, after, responses)),
        ('unused', module('R-04-unused-imports').inspect(after))
    ]:
        save(name, value)
        count = value.get('differences', value.get('unusedCount'))
        result[name] = count
        assert count == 0, (name, count)
    sql = module('R-03-sql-location-measure')
    old_rows, new_rows = sql.scan(args.before), sql.scan(args.after)
    transaction_renames = {}
    for unit, owner in analysis.classes(before):
        for method in owner['members']:
            if method['kind'] != 'METHOD':
                continue
            old = owner['qualified'] + '#' + method['name']
            target = renames.get(owner['qualified'] + '#' + method['signature'], renames.get(old, old))
            target_name = target.split('#')[-1].split('(')[0]
            for row in old_rows:
                if row['kind'] == 'T' and row['owner'].startswith(old + '(') and target_name != method['name']:
                    transaction_renames[row['owner']] = owner['qualified'] + '#' + target_name + row['owner'][len(old):]
    tx = sql.compare(old_rows, new_rows, {}, {}, transaction_renames)
    save('transactions', tx)
    assert tx['sameTransactions']
    result['transactions'] = tx['afterTransactionCount']
    result['outsideSql'] = len(sql.summarize(new_rows)['outside'])
    assert result['outsideSql'] == 0
    def hashes(root, subdir):
        return {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in sorted((root / subdir).rglob('*')) if p.is_file()}
    result['testSourceSame'] = hashes(args.before, 'backend/src/test') == hashes(args.after, 'backend/src/test')
    assert result['testSourceSame']
    save('source-hashes', hashes(args.after, 'backend/src'))
    response = module('R-04-response-check')
    left, right = response.inventory(before), response.inventory(after)
    result['apiSame'] = sorted((r['method'], r['path']) for r in left['apis']) == sorted((r['method'], r['path']) for r in right['apis'])
    assert result['apiSame']
    result['apiCount'] = len(right['apis'])
    for label, units, root in [('before', before, args.before), ('after', after, args.after)]:
        names = module('R-04-names').measure(units, root)
        save(label + '-names', names)
        inventory = response.inventory(units)
        save(label + '-responses', inventory)
        metrics = {}
        for domain in names['domains']:
            segment = '/global/' if domain == 'global' else '/domain/' + domain + '/'
            public_maps = []
            api_wildcard = entity_wildcard = 0
            for unit, owner in analysis.classes(units):
                if segment not in unit['file']:
                    continue
                for method in owner['members']:
                    if method['kind'] != 'METHOD':
                        continue
                    api_wildcard += 'ApiResponse<?>' in method['returns']
                    entity_wildcard += 'ResponseEntity<?>' in method['returns']
                    if '/repository/' not in unit['file'] and 'public' in method['modifiers'] and (
                            re.search(r'\bMap\b', method['returns']) or method['returns'] in ['Object', 'java.lang.Object']):
                        public_maps.append(owner['qualified'] + '#' + method['signature'])
            factories = [row for row in inventory['maps'] if segment in row['file']]
            metrics[domain] = {
                'apiWildcard': api_wildcard, 'entityWildcard': entity_wildcard,
                'publicMapOrObject': len(public_maps), 'mapMethods': public_maps,
                'factoryLines': len({(row['file'], row['line']) for row in factories}),
                'oneLetter': names['domains'][domain]['oneLetterCount']
            }
        save(label + '-metrics', metrics)
    units = module('R-03-format-units').measure(args.after)
    save('format-units', units)
    assert units['main']['count'] == 1 and units['test']['count'] == 0
    assert units['main']['locations'][0]['file'].endswith('/ProjectMember.java')
    subprocess.run([sys.executable, str(TOOLS / 'R-02-format-measure.py'), '--root', str(args.after),
                    '--output', str(out / 'format.json')], check=True)
    formatting = json.loads((out / 'format.json').read_text())['main']
    for key in ['long_lines', 'multiple_statements', 'multiple_declarators', 'fragment_splits',
                'outer_operator_unsplit', 'ternary_layout', 'detached_parameter_annotations',
                'detached_record_annotations', 'detached_type_annotations', 'array_initializer_splits']:
        assert formatting['counts'][key] == 0, key
    # The original protected text-block closing line ends with the outResult key.
    # Keeping that exact line necessarily retains its following value-line split.
    for location in formatting['locations']['key_value_splits']:
        file, line = location.rsplit(':', 1)
        assert file.endswith('/attendance/repository/AttendanceStore.java')
        assert '\"\"\"' in (args.after / file).read_text().splitlines()[int(line) - 1]
    lexer = module('R-02-format-measure')
    closing_changes = []
    for path in (args.before / 'backend/src/main/java').rglob('*.java'):
        candidate = args.after / path.relative_to(args.before)
        if not candidate.exists():
            continue
        def closing(file):
            text = file.read_text()
            return [(v, text.splitlines()[text[:end].count('\n')])
                    for kind, v, start, end in lexer.tokenize(text) if kind == 'text']
        if closing(path) != closing(candidate):
            closing_changes.append(str(path.relative_to(args.before)))
    # Query code is unchanged except the approved Schedule.view removal and Java aliases.
    prefix = Path('backend/src/main/java/com/minierp/backend')
    aliases = {
        'domain/attendance/repository/AttendanceStore.java': {'s': {'scheduleRow'}, 't': {'sqlTime', 'localTime'}},
        'domain/project/repository/ProjectStore.java': {'p': {'projectId'}, 'u': {'userId'}},
        'domain/project/repository/ProjectStateStore.java': {'n': {'statusCount'}},
        'domain/task/repository/TaskQueries.java': {'p': {'projectId'}},
        'domain/task/repository/TaskStore.java': {'p': {'projectId'}},
        'global/entity/BaseEntity.java': {'o': {'other'}},
        'global/persistence/SqlStore.java': {'v': {'value'}, 'b': {'booleanValue'}},
        'global/exception/GlobalExceptionHandler.java': {'e': {'exception'}}
    }
    token_results = []
    for file, replacements in aliases.items():
        old = (args.before / prefix / file).read_text()
        new = (args.after / prefix / file).read_text()
        if file.endswith('AttendanceStore.java') and 'public Map<String, Object> view()' not in new:
            unit, owner = next((unit, owner) for unit, owner in analysis.classes(before)
                               if owner['qualified'].endswith('.AttendanceStore.Schedule'))
            method = next(member for member in owner['members']
                          if member['kind'] == 'METHOD' and member['name'] == 'view')
            old = old[:method['start']] + old[method['end']:]
            old = old.replace('import java.time.format.DateTimeFormatter;\n', '')
        left = [(kind, value) for kind, value, *_ in lexer.tokenize(old)]
        right = [(kind, value) for kind, value, *_ in lexer.tokenize(new)]
        assert len(left) == len(right), file
        changed = 0
        for original, current in zip(left, right):
            if original == current:
                continue
            assert original[0] == current[0] == 'id' and current[1] in replacements.get(original[1], set()), (file, original, current)
            changed += 1
        token_results.append({'file': file, 'identifierChanges': changed, 'otherTokenChanges': 0})
    save('query-and-global-tokens', token_results)
    result['textBlockClosingChanges'] = closing_changes
    assert not closing_changes
    save('summary', result)
    print('GROUP3_STATIC_PASS ' + json.dumps(result, ensure_ascii=False))

if __name__ == '__main__':
    main()
