#!/usr/bin/env python3
"""Reproducible textual measurements. Run from backend/; does not modify source files."""
from pathlib import Path
import re
files = sorted(Path('src/main/java').rglob('*.java'))
lines = [(str(path), i, line) for path in files for i, line in enumerate(path.read_text().splitlines(), 1)]
print('java_files=',len(files))
print('lines_over_160=',sum(len(line)>160 for _,_,line in lines))
print('lines_with_2_or_more_semicolons=',sum(line.count(';')>=2 for _,_,line in lines))
print('lines_with_3_or_more_semicolons=',sum(line.count(';')>=3 for _,_,line in lines))
# Secondary approximation removes literals/comments and for-header separators before counting.
semantic_two=semantic_three=0
for path in files:
    source=path.read_text()
    pattern=r'\"\"\"[\s\S]*?\"\"\"|\"(?:\\.|[^\"\\])*\"|/\*[\s\S]*?\*/|//[^\n]*'
    clean=re.sub(pattern,lambda match:'\n'*match[0].count('\n'),source)
    clean=re.sub(r'\bfor\s*\([^)]*\)',lambda match:match[0].replace(';',','),clean)
    semantic_two+=sum(line.count(';')>=2 for line in clean.splitlines())
    semantic_three+=sum(line.count(';')>=3 for line in clean.splitlines())
print('estimated_multi_statement_lines_without_literals_and_for_headers=',semantic_two)
print('estimated_3_statement_lines_without_literals_and_for_headers=',semantic_three)
print('ApiResponse_wildcards=',sum(len(re.findall(r'ApiResponse\s*<\s*\?\s*>',path.read_text())) for path in files))
# Uppercase SQL keywords with word boundaries; includes service support Query/Store/Handler/Batch files.
sql_files=[path for path in files if 'service' in path.parts and re.search(r'\b(SELECT|INSERT|UPDATE|DELETE|WITH)\b',path.read_text())]
print('service_files_with_SQL_keywords=',len(sql_files))
for path in sql_files: print(path)
