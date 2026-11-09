#!/usr/bin/env python3
"""Read-only Java format measurements. Python standard library + JDK parser; no external formatter.

Counts Unicode characters, not bytes. Strings/comments do not contribute semicolons.
Text-block content and its closing-delimiter line are excluded from line-length counts.
A literal exception is exactly one ordinary literal plus whitespace and , ; ) ] { } punctuation.
"""
import argparse
import collections
import json
from pathlib import Path
import re
import os
import shutil
import subprocess
import tempfile

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
    result = []
    offset = 0
    while offset < len(source):
        if source.startswith('"""', offset):
            end = offset + 3
            while end < len(source):
                if source[end] == '\\':
                    end += 2
                elif source.startswith('"""', end):
                    end += 3
                    break
                else:
                    end += 1
            result.append(('text', source[offset:end], offset, end))
            offset = end
        else:
            match = TOKEN.match(source, offset)
            if match.lastgroup not in ('ws', 'comment'):
                result.append((match.lastgroup, match.group(), match.start(), match.end()))
            offset = match.end()
    return result


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
    output.update(wrap_measurements(source, location))
    output['comparison'] = comparison
    return output


ANNOTATION_SCANNER = r'''
import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import javax.lang.model.element.Modifier;
import java.nio.file.*;
import java.util.*;

class AnnotationMeasure {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A JDK is required");
        try (var files = compiler.getStandardFileManager(null, null, null)) {
            List<Path> paths;
            try (var stream = Files.walk(root.resolve("backend/src"))) {
                paths = stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            }
            var task = (JavacTask) compiler.getTask(null, files, diagnostic -> {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    throw new IllegalStateException(diagnostic.toString());
                }
            }, List.of("-proc:none"), null, files.getJavaFileObjectsFromPaths(paths));
            for (var unit : task.parse()) {
                var positions = Trees.instance(task).getSourcePositions();
                String source = unit.getSourceFile().getCharContent(true).toString();
                String file = root.relativize(Path.of(unit.getSourceFile().toUri())).toString();
                new TreeScanner<Void, Void>() {
                    long start(Tree tree) { return positions.getStartPosition(unit, tree); }
                    long line(Tree tree) { return unit.getLineMap().getLineNumber(start(tree)); }
                    void parameter(VariableTree parameter, String kind) {
                        var annotations = parameter.getModifiers().getAnnotations();
                        if (!annotations.isEmpty()) {
                            long end = positions.getEndPosition(unit, annotations.get(annotations.size() - 1));
                            System.out.println(kind + "\t" + file + "\t"
                                    + unit.getLineMap().getLineNumber(end - 1) + "\t" + line(parameter.getType()));
                        }
                    }
                    void invocation(Tree call, Tree selector, List<? extends ExpressionTree> arguments) {
                        long first = start(call);
                        long end = positions.getEndPosition(unit, call);
                        long selectorEnd = positions.getEndPosition(unit, selector);
                        boolean[] lambda = {false};
                        new TreeScanner<Void, Void>() {
                            @Override public Void visitLambdaExpression(LambdaExpressionTree tree, Void unused) {
                                lambda[0] = true;
                                return null;
                            }
                        }.scan(call, null);
                        if (!lambda[0]) {
                            System.out.println("C\t" + file + "\t" + first + "\t" + end + "\t" + selectorEnd);
                        }
                        String name = selector.toString();
                        if (name.equals("map") || name.equals("SqlStore.map") || name.equals("Map.of")) {
                            for (int i = 0; i + 1 < arguments.size(); i += 2) {
                                long keyEnd = positions.getEndPosition(unit, arguments.get(i));
                                long valueStart = start(arguments.get(i + 1));
                                System.out.println("K\t" + file + "\t" + keyEnd + "\t" + valueStart);
                            }
                        }
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree call, Void ignored) {
                        invocation(call, call.getMethodSelect(), call.getArguments());
                        return super.visitMethodInvocation(call, ignored);
                    }
                    @Override public Void visitNewClass(NewClassTree call, Void ignored) {
                        invocation(call, call.getIdentifier(), call.getArguments());
                        return super.visitNewClass(call, ignored);
                    }
                    @Override public Void visitMethod(MethodTree method, Void ignored) {
                        method.getParameters().forEach(parameter -> parameter(parameter, "P"));
                        return super.visitMethod(method, ignored);
                    }
                    @Override public Void visitClass(ClassTree type, Void ignored) {
                        if (type.getKind() == Tree.Kind.RECORD) {
                            for (Tree member : type.getMembers()) {
                                if (member instanceof VariableTree variable
                                        && !variable.getModifiers().getFlags().contains(Modifier.STATIC)) {
                                    parameter(variable, "R");
                                }
                            }
                        }
                        return super.visitClass(type, ignored);
                    }
                    @Override public Void visitAnnotatedType(AnnotatedTypeTree type, Void ignored) {
                        if (!type.getAnnotations().isEmpty()) {
                            System.out.println("T\t" + file + "\t" + unit.getLineMap().getLineNumber(positions.getEndPosition(unit,
                                    type.getAnnotations().get(type.getAnnotations().size() - 1)) - 1)
                                    + "\t" + line(type.getUnderlyingType()));
                        }
                        return super.visitAnnotatedType(type, ignored);
                    }
                }.scan(unit, null);
            }
        }
    }
}
'''


def annotation_measurements(root):
    """Use only the JDK parser: no classpath, annotation processing, or external library.

    P counts method/constructor parameters; R counts record components separately.
    T counts annotated type nodes. A target is detached when the last annotation ends
    on a different line from the parameter type / underlying type. This distinguishes
    wrapping long annotation arguments from separating an annotation from its target.
    Multiple annotations on one parameter count once.
    C examines full method/new invocation spans (including a parenthesized first argument).
    Short calls are <= 60 characters after whitespace compression and have a multiline
    argument list; text blocks, lambdas, and comments are excluded.
    K compares each key end with its paired value start for map/SqlStore.map/Map.of.
    javac UTF-16 offsets are converted before indexing the Python source string.
    """
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
    if not java:
        raise SystemExit('JDK 17+ is required for annotation measurements; set JAVA_HOME')
    with tempfile.TemporaryDirectory(prefix='r02-annotation-measure-') as temporary:
        source = Path(temporary) / 'AnnotationMeasure.java'
        source.write_text(ANNOTATION_SCANNER, encoding='utf-8')
        run = subprocess.run([java, str(source), str(root)], capture_output=True, text=True, check=True)
    result = {part: {'annotated_parameters': [], 'detached_parameter_annotations': [],
                     'annotated_records': [], 'detached_record_annotations': [],
                     'annotated_types': [], 'detached_type_annotations': [],
                     'short_call_splits': [], 'key_value_splits': []} for part in ('main', 'test')}
    sources = {}
    for line in run.stdout.splitlines():
        kind, file, *values = line.split('\t')
        part = 'main' if '/main/' in file else 'test'
        if kind in ('C', 'K'):
            if file not in sources:
                sources[file] = (root / file).read_text(encoding='utf-8')
            source = sources[file]
            # javac positions count UTF-16 code units, Python indexes Unicode code points.
            def offset(value):
                return len(source.encode('utf-16-le')[:int(value) * 2].decode('utf-16-le'))
            positions = list(map(offset, values))
            if kind == 'C':
                first, end, selector_end = positions
                call = source[first:end]
                tokens = tokenize(call)
                # The JDK visitor excludes lambdas; lexical checks exclude text blocks and comments.
                if any(token[0] == 'text' for token in tokens):
                    continue
                residual = list(call)
                for token in tokens:
                    if token[0] == 'string':
                        residual[token[2]:token[3]] = ' ' * (token[3] - token[2])
                if re.search(r'//|/\*', ''.join(residual)):
                    continue
                if len(re.sub(r'\s+', ' ', call).strip()) <= 60 and '\n' in source[selector_end:end]:
                    result[part]['short_call_splits'].append(f'{file}:{source.count(chr(10), 0, first) + 1}')
            else:
                key_end, value_start = positions
                if '\n' in source[key_end:value_start]:
                    result[part]['key_value_splits'].append(f'{file}:{source.count(chr(10), 0, key_end - 1) + 1}')
            continue
        first, target = values
        all_key, detached_key = ({'P': ('annotated_parameters', 'detached_parameter_annotations'),
                                  'R': ('annotated_records', 'detached_record_annotations'),
                                  'T': ('annotated_types', 'detached_type_annotations')}[kind])
        location = f'{file}:{first}'
        result[part][all_key].append(location)
        if first != target:
            result[part][detached_key].append(location)
    return result


def wrap_measurements(source, location):
    """Exclude comment/literal contents; keep a text block's trailing Java code visible.

    Name/member: the last token of a physical line is an identifier and the first token
    of the next physical line is a dot. Non-chain is the requested physical-line heuristic:
    the immediately following (third) line does not start with a dot.
    """
    grouped = collections.defaultdict(list)
    for token in tokenize(source):
        if token[0] == 'text':
            continue
        grouped[source.count('\n', 0, token[2]) + 1].append(token)
    result = {'name_member_breaks': [], 'non_chain_breaks': [], 'angle_line_ends': []}
    for line, tokens in grouped.items():
        if tokens[-1][1] == '<':
            result['angle_line_ends'].append(f'{location}:{line}')
        following = grouped.get(line + 1, [])
        if tokens[-1][0] == 'id' and following and following[0][1] == '.':
            result['name_member_breaks'].append(f'{location}:{line}')
            third = grouped.get(line + 2, [])
            if not third or third[0][1] != '.':
                result['non_chain_breaks'].append(f'{location}:{line}')
    return result


STRUCTURE_SCANNER = r'''
import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;
class StructureMeasure {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var files = compiler.getStandardFileManager(null, null, null)) {
            List<Path> paths;
            try (var stream = Files.walk(root.resolve("backend/src"))) {
                paths = stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            }
            var task = (JavacTask) compiler.getTask(null, files, diagnostic -> {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) throw new IllegalStateException(diagnostic.toString());
            }, List.of("-proc:none"), null, files.getJavaFileObjectsFromPaths(paths));
            for (var unit : task.parse()) {
                var positions = Trees.instance(task).getSourcePositions();
                String file = root.relativize(Path.of(unit.getSourceFile().toUri())).toString();
                new TreePathScanner<Void, Void>() {
                    long start(Tree tree) { return positions.getStartPosition(unit, tree); }
                    long end(Tree tree) { return positions.getEndPosition(unit, tree); }
                    void emit(String kind, Tree parent, List<? extends Tree> children) {
                        emit(kind, parent, children, start(parent));
                    }
                    void emit(String kind, Tree parent, List<? extends Tree> children, long anchor) {
                        StringJoiner spans = new StringJoiner(",");
                        for (Tree child : children) spans.add(start(child) + ":" + end(child) + ":" + child.getKind());
                        List<String> exclusions = new ArrayList<>();
                        new TreeScanner<Void, Void>() {
                            void exclude(Tree tree) { exclusions.add(start(tree) + ":" + end(tree)); }
                            @Override public Void visitLambdaExpression(LambdaExpressionTree tree, Void unused) {
                                if (tree.getBodyKind() == LambdaExpressionTree.BodyKind.STATEMENT) exclude(tree);
                                return super.visitLambdaExpression(tree, unused);
                            }
                            @Override public Void visitSwitchExpression(SwitchExpressionTree tree, Void unused) {
                                exclude(tree); return super.visitSwitchExpression(tree, unused);
                            }
                            @Override public Void visitNewClass(NewClassTree tree, Void unused) {
                                if (tree.getClassBody() != null) exclude(tree);
                                return super.visitNewClass(tree, unused);
                            }
                        }.scan(parent, null);
                        System.out.println(kind + "\t" + file + "\t" + start(parent) + "\t" + end(parent)
                                + "\t" + spans + "\t" + String.join(",", exclusions) + "\t" + anchor);
                    }
                    void flatten(Tree tree, Tree.Kind kind, List<Tree> result) {
                        if (tree instanceof BinaryTree binary && tree.getKind() == kind) {
                            flatten(binary.getLeftOperand(), kind, result); flatten(binary.getRightOperand(), kind, result);
                        } else result.add(tree);
                    }
                    @Override public Void visitBinary(BinaryTree tree, Void unused) {
                        Tree.Kind kind = tree.getKind();
                        if (kind == Tree.Kind.CONDITIONAL_OR || kind == Tree.Kind.CONDITIONAL_AND || kind == Tree.Kind.PLUS) {
                            Tree parent = getCurrentPath().getParentPath().getLeaf();
                            if (!(parent instanceof BinaryTree) || parent.getKind() != kind) {
                                List<Tree> children = new ArrayList<>(); flatten(tree, kind, children);
                                emit(kind == Tree.Kind.PLUS ? "+" : kind == Tree.Kind.CONDITIONAL_OR ? "||" : "&&", tree, children);
                            }
                        }
                        return super.visitBinary(tree, unused);
                    }
                    @Override public Void visitConditionalExpression(ConditionalExpressionTree tree, Void unused) {
                        emit("?:", tree, List.of(tree.getCondition(), tree.getTrueExpression(), tree.getFalseExpression()));
                        return super.visitConditionalExpression(tree, unused);
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
                        String name = tree.getMethodSelect().toString();
                        emit(name.equals("map") || name.equals("SqlStore.map") || name.equals("Map.of") ? "pairs" : "args",
                                tree, tree.getArguments(), end(tree.getMethodSelect()));
                        return super.visitMethodInvocation(tree, unused);
                    }
                    @Override public Void visitNewClass(NewClassTree tree, Void unused) {
                        emit("args", tree, tree.getArguments(), end(tree.getIdentifier())); return super.visitNewClass(tree, unused);
                    }
                    @Override public Void visitNewArray(NewArrayTree tree, Void unused) {
                        if (tree.getInitializers() != null) emit("array", tree, tree.getInitializers());
                        return super.visitNewArray(tree, unused);
                    }
                }.scan(unit, null);
            }
        }
    }
}
'''


def structure_measurements(root):
    """Measure expression structure with javac's parser (no resolution or processing).

    Flatten only identical direct binary operators. Each distinct child span counts
    once even when both an outer invocation and a nested operator expose it. A map
    key/value is one span. For call arguments the parent starts at the call selector's
    end, not at the first line of a potentially long receiver chain. Placement includes
    operator prefixes and closing punctuation. Literal interiors are never compressed.

    javac folds adjacent string literals. Their source span is atomic: neither its
    internal newline nor a comment inside it creates an actionable fragment. A fragment
    starting on a protected text-block closing line is reported separately, never hidden.
    """
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
    if not java:
        raise SystemExit('JDK 17+ is required for structural measurements; set JAVA_HOME')
    with tempfile.TemporaryDirectory(prefix='r02-structure-measure-') as temporary:
        scanner = Path(temporary) / 'StructureMeasure.java'
        scanner.write_text(STRUCTURE_SCANNER, encoding='utf-8')
        run = subprocess.run([java, str(scanner), str(root)], capture_output=True, text=True, check=True)
    keys = ('fragment_splits', 'outer_operator_unsplit', 'ternary_layout',
            'array_initializer_splits', 'protected_fragment_splits')
    result = {part: {key: [] for key in keys} for part in ('main', 'test')}
    sources = {}
    seen = set()
    for record in run.stdout.splitlines():
        kind, file, first, last, spans, exclusions, anchor = record.split('\t')
        if file not in sources:
            source = (root / file).read_text(encoding='utf-8')
            protected = {source.count('\n', 0, token[3]) + 1
                         for token in tokenize(source) if token[0] == 'text'}
            sources[file] = (source, source.encode('utf-16-le'), protected)
        source, utf16, protected = sources[file]

        def offset(value):
            return len(utf16[:int(value) * 2].decode('utf-16-le'))

        start, end, anchor = map(offset, (first, last, anchor))
        children = []
        for span in spans.split(',') if spans else []:
            begin, finish, node_kind = span.split(':')
            children.append((offset(begin), offset(finish), node_kind))
        excluded_spans = [tuple(map(offset, span.split(':')))
                          for span in exclusions.split(',')] if exclusions else []
        part = 'main' if '/main/' in file else 'test'

        def line(position):
            return source.count('\n', 0, position) + 1

        def column(position):
            return position - (source.rfind('\n', 0, position) + 1)

        def indent(position):
            physical_line = source[source.rfind('\n', 0, position) + 1:]
            return len(physical_line) - len(physical_line.lstrip(' \t'))

        def excluded(begin, finish):
            if any(begin <= a and b <= finish for a, b in excluded_spans):
                return True
            text = source[begin:finish]
            tokens = tokenize(text)
            if any(token[0] == 'text' for token in tokens):
                return True
            residual = list(text)
            for token in tokens:
                if token[0] == 'string':
                    residual[token[2]:token[3]] = ' ' * (token[3] - token[2])
            return bool(re.search(r'//|/\*', ''.join(residual)))

        def at_line_head(position):
            return not source[source.rfind('\n', 0, position) + 1:position].strip()

        def operator(begin, finish, value):
            return next((begin + token[2] for token in tokenize(source[begin:finish])
                         if token[1] == value), None)

        def mark(key, position):
            result[part][key].append(f'{file}:{line(position)}')

        whole_excluded = excluded(start, end)
        visible = list(source[start:end])
        for begin, finish, node_kind in children:
            if node_kind.endswith('_LITERAL'):
                for position in range(begin, finish):
                    if source[position] == '\n':
                        visible[position - start] = ' '
        multiline = '\n' in ''.join(visible)
        if kind == 'array' and not whole_excluded:
            if children:
                opening = source.rfind('{', start, children[0][0])
                closing = source.rfind('}', children[-1][1], end)
                broken = ('\n' in source[opening + 1:children[0][0]]
                          or '\n' in source[children[-1][1]:closing])
            else:
                opening = source.find('{', start, end)
                closing = source.rfind('}', start, end)
                broken = '\n' in source[opening + 1:closing]
            if broken:
                mark('array_initializer_splits', start)
        if kind in ('||', '&&', '+') and not whole_excluded and multiline:
            operators = [operator(a[1], b[0], kind) for a, b in zip(children, children[1:])]
            if not any(position is not None and at_line_head(position) for position in operators):
                mark('outer_operator_unsplit', start)
        if kind == '?:' and not whole_excluded and line(start) != line(end - 1):
            question = operator(children[0][1], children[1][0], '?')
            colon = operator(children[1][1], children[2][0], ':')
            if not (question is not None and colon is not None
                    and at_line_head(question) and at_line_head(colon)):
                mark('ternary_layout', start)
        if kind == 'array':
            continue
        pieces = children
        if kind == 'pairs':
            pieces = [(children[i][0], children[i + 1][1], 'PAIR')
                      for i in range(0, len(children) - 1, 2)]
        for index, (begin, finish, node_kind) in enumerate(pieces):
            if line(begin) == line(finish - 1) or excluded(begin, finish) or node_kind.endswith('_LITERAL'):
                continue
            prefix = ''
            if index and kind in ('||', '&&', '+'):
                prefix = kind + ' '
            elif index and kind == '?:':
                prefix = '? ' if index == 1 else ': '
            first_operand = index == 0 and kind in ('||', '&&', '+', '?:')
            placement = column(begin) if first_operand else min(column(begin), indent(anchor) + 8)
            # Only immediately following punctuation belongs to the fragment's width.
            suffix = ''
            position = finish
            while position < len(source):
                char = source[position]
                if char.isspace():
                    position += 1
                    continue
                if char in ')],;':
                    suffix += char
                    position += 1
                    continue
                if char == '{':
                    suffix += ' {'
                break
            text = source[begin:finish]
            compact = []
            previous = 0
            for token in tokenize(text):
                if token[0] == 'string':
                    compact.extend((re.sub(r'\s+', ' ', text[previous:token[2]]), token[1]))
                    previous = token[3]
            compact.append(re.sub(r'\s+', ' ', text[previous:]))
            width = placement + len(prefix) + len(''.join(compact).strip()) + len(suffix)
            identity = (file, begin, finish)
            if width <= 120 and identity not in seen:
                seen.add(identity)
                mark('protected_fragment_splits' if line(begin) in protected else 'fragment_splits', begin)
    return result


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
    annotations = annotation_measurements(root)
    structures = structure_measurements(root)
    for part in ('main', 'test'):
        entries = {name: [] for name in ('long_lines', 'literal_exceptions', 'multiple_statements', 'multiple_declarators',
                                       'name_member_breaks', 'non_chain_breaks', 'angle_line_ends')}
        comparison = collections.Counter()
        files = sorted((root / 'backend/src' / part / 'java').rglob('*.java'))
        for path in files:
            measured = measure_file(path, root)
            comparison.update(measured.pop('comparison'))
            for name, values in measured.items():
                entries[name].extend(values)
        entries.update(annotations[part])
        entries.update(structures[part])
        result[part] = {'files': len(files), 'counts': {k: len(v) for k, v in entries.items()},
                        'comparison': dict(comparison), 'locations': entries}
    text = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.write_text(text, encoding='utf-8')
    print(json.dumps({part: {k: v for k, v in row.items() if k != 'locations'}
                      for part, row in result.items()}, ensure_ascii=False))


if __name__ == '__main__':
    main()
