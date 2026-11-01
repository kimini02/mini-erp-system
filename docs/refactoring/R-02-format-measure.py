#!/usr/bin/env python3
"""Read-only Java format measurements. Python standard library; no compiler or formatter.

Counts Unicode characters, not bytes. Strings/comments do not contribute semicolons.
Text-block content and its closing-delimiter line are excluded from line-length counts.
A literal exception is exactly one ordinary literal plus whitespace and , ; ) ] { } punctuation.
"""
import argparse
import collections
import json
from pathlib import Path
import re

TOKEN = re.compile(
    r'(?P<ws>\s+)'
    r'|(?P<text>"""[\s\S]*?(?<!\\)""")'
    r'|(?P<comment>//[^\n]*|/\*[\s\S]*?\*/)'
    r'|(?P<string>"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\')'
    r'|(?P<id>[^\W\d]\w*|\$[\w$]*)'
    r'|(?P<number>\d+(?:\.\d+)?(?:[eE][+-]?\d+)?[\w]*)'
    r'|(?P<op>>>>=?|>>=?|<<=?|\.\.\.|::|->|\+\+|--|==|!=|<=|>=|&&|\|\||[+*/%&|^!-]=|.)'
)
KEYWORDS = set(('return throw new class interface enum record package import extends implements '
                'if else for while do switch case default try catch finally assert break continue '
                'public protected private static final volatile transient synchronized abstract '
                'native strictfp void instanceof this super true false null').split())


def tokenize(source):
    return [(m.lastgroup, m.group(), m.start(), m.end())
            for m in TOKEN.finditer(source) if m.lastgroup not in ('ws', 'comment')]


def angle_end(tokens, start):
    """Recognize type-argument syntax; reject expression operators in its interior."""
    depth = 0
    for i in range(start, len(tokens)):
        kind, value, _, _ = tokens[i]
        if value == '<':
            depth += 1
        elif value in ('>', '>>', '>>>'):
            depth -= len(value)
            if depth <= 0:
                return i + 1 if depth == 0 else None
        elif kind != 'id' and value not in ('.', ',', '?', '[', ']', '&', '@'):
            return None
    return None


def contexts(tokens):
    """Parenthesis context includes brace depth so lambda statements are not for separators."""
    stack = []
    braces = 0
    result = []
    previous = ''
    for _, value, _, _ in tokens:
        result.append((bool(stack), any(kind == 'for' and depth == braces for kind, depth in stack)))
        if value == '(':
            stack.append(('for' if previous == 'for' else 'other', braces))
        elif value == ')' and stack:
            stack.pop()
        elif value == '{':
            braces += 1
        elif value == '}':
            braces -= 1
        previous = value
    return result


def declarations(tokens, context):
    """Find a type + name followed by declarators; count top-level commas, not arguments.

    Declared-name offsets deduplicate modifier/type entry points. Initializer parentheses,
    arrays, anonymous/lambda bodies, and generic type arguments are balanced separately.
    For-header declarations are counted too; for-header semicolons are not statements.
    """
    found = {}
    for start, (kind, value, _, _) in enumerate(tokens):
        if kind != 'id' or value in KEYWORDS:
            continue
        in_parens, in_for = context[start]
        if in_parens and not in_for:
            continue
        i = start + 1
        while i < len(tokens):
            if tokens[i][1] == '<':
                end = angle_end(tokens, i)
                if end is None:
                    break
                i = end
            elif tokens[i][1] == '.' and i + 1 < len(tokens) and tokens[i + 1][0] == 'id':
                i += 2
            elif tokens[i][1] == '[' and i + 1 < len(tokens) and tokens[i + 1][1] == ']':
                i += 2
            else:
                break
        if i >= len(tokens) or tokens[i][0] != 'id' or tokens[i][1] in KEYWORDS:
            continue
        name = i
        i += 1
        while i + 1 < len(tokens) and tokens[i][1] == '[' and tokens[i + 1][1] == ']':
            i += 2
        if i >= len(tokens) or tokens[i][1] not in ('=', ',', ';'):
            continue
        count = 1
        levels = {'(': 0, '[': 0, '{': 0}
        closes = {')': '(', ']': '[', '}': '{'}
        while i < len(tokens):
            value = tokens[i][1]
            if value == '<':
                end = angle_end(tokens, i)
                if end is not None:
                    i = end
                    continue
            if value in levels:
                levels[value] += 1
            elif value in closes:
                levels[closes[value]] -= 1
                if levels[closes[value]] < 0:
                    break
            elif not any(levels.values()):
                if value == ',':
                    count += 1
                elif value == ';':
                    if count > 1:
                        found[tokens[name][2]] = count
                    break
            i += 1
    return found


def measure_file(path, root):
    source = path.read_text(encoding='utf-8')
    tokens = tokenize(source)
    context = contexts(tokens)
    lines = source.splitlines()
    line_of = lambda offset: source.count('\n', 0, offset) + 1
    excluded = set()
    literal_lines = set()
    for kind, value, start, end in tokens:
        if kind == 'text':
            excluded.update(range(line_of(start) + 1, line_of(end) + 1))
        elif kind == 'string' and re.search(r'\b(select|insert|update|delete|alter|create|drop|with)\b', value, re.I):
            literal_lines.add(line_of(start))
    location = str(path.relative_to(root))
    output = {name: [] for name in ('long_lines', 'literal_exceptions', 'multiple_statements', 'multiple_declarators')}
    comparison = {'long_without_sql_literal': 0, 'long_with_sql_literal': 0}
    for number, line in enumerate(lines, 1):
        if number in excluded or len(line) <= 120:
            continue
        comparison['long_with_sql_literal' if number in literal_lines else 'long_without_sql_literal'] += 1
        items = tokenize(line)
        literals = [item for item in items if item[0] == 'string']
        remainder = [item[1] for item in items if item[0] != 'string']
        exception = len(literals) == 1 and all(item in (',', ';', ')', ']', '{', '}') for item in remainder)
        output['literal_exceptions' if exception else 'long_lines'].append(f'{location}:{number}')
    semicolons = collections.Counter()
    for token, (_, in_for) in zip(tokens, context):
        if token[1] == ';' and not in_for:
            semicolons[line_of(token[2])] += 1
    output['multiple_statements'] = [f'{location}:{line}' for line, count in semicolons.items() if count > 1]
    output['multiple_declarators'] = [f'{location}:{line_of(offset)}' for offset in declarations(tokens, context)]
    output['comparison'] = comparison
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    for part in ('main', 'test'):
        if not (root / 'backend/src' / part / 'java').is_dir():
            parser.error(f'Java source directory not found: {root / "backend/src" / part / "java"}')
    result = {}
    for part in ('main', 'test'):
        entries = {name: [] for name in ('long_lines', 'literal_exceptions', 'multiple_statements', 'multiple_declarators')}
        comparison = collections.Counter()
        files = sorted((root / 'backend/src' / part / 'java').rglob('*.java'))
        for path in files:
            measured = measure_file(path, root)
            comparison.update(measured.pop('comparison'))
            for name, values in measured.items():
                entries[name].extend(values)
        result[part] = {'files': len(files), 'counts': {k: len(v) for k, v in entries.items()},
                        'comparison': dict(comparison), 'locations': entries}
    text = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.write_text(text, encoding='utf-8')
    print(json.dumps({part: {k: v for k, v in row.items() if k != 'locations'}
                      for part, row in result.items()}, ensure_ascii=False))


if __name__ == '__main__':
    main()
