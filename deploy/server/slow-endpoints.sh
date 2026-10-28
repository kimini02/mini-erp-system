#!/usr/bin/env bash
set -euo pipefail
# Accept a JSON access log path or stdin; bodies/queries are never printed.
exec 3<&0
python3 - "${1:--}" <<'PY'
import json,sys,math,re
from collections import defaultdict
rows=defaultdict(list);invalid=0
source=open(3,closefd=False) if sys.argv[1]=='-' else open(sys.argv[1])
for line in source:
    try:
        record=json.loads(line);uri=record['uri'].split('?',1)[0]
        if not uri.startswith('/api/'):continue
        path=re.sub(r'(?<=/)\d+(?=/|$)','{id}',uri)
        elapsed=float(record['request_time'])
        if not math.isfinite(elapsed) or elapsed<0:raise ValueError()
        rows[(record['request_method'],path)].append(elapsed)
    except (ValueError,KeyError,TypeError):invalid+=1
print('method\tpath\tcount\tp50_ms\tp95_ms\tmax_ms')
for (method,path),values in sorted(rows.items()):
    values.sort()
    p=lambda q:values[max(0,math.ceil(q*len(values))-1)]*1000
    print(f'{method}\t{path}\t{len(values)}\t{p(.5):.3f}\t{p(.95):.3f}\t{values[-1]*1000:.3f}')
print(f'invalid_lines={invalid}',file=sys.stderr)
PY
