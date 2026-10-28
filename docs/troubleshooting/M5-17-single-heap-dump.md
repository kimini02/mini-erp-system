# M5-17 OOM 힙 덤프를 한 파일로 제한

## 증상
HeapDumpPath를 디렉터리로 설정해 OOM마다 java_pid별 대형 파일이 쌓인다. 덤프에는 프로세스의 비밀값·개인정보가 들어갈 수 있다.

## 재현 방법
사용자 데이터를 전혀 넣지 않은 시험 클래스가 16 MiB heap에서 64 MiB 배열을 할당하도록 했다. Ubuntu 24.04의 Java 17에서 두 번 실행하고 종료 코드·파일 수·동일 파일 checksum을 비교했다. 이 시험 덤프는 컨테이너 안에서만 만들고 즉시 삭제했다.

## 원인
디렉터리 경로는 JVM이 프로세스별 새 파일 이름을 선택한다.

## 검토했지만 버린 방법과 이유
- 주기적으로 덤프 삭제: 삭제 전까지 공간이 계속 늘고 또 하나의 스케줄을 관리해야 한다.
- 덤프를 로컬/외부로 수집: 민감 데이터의 보관·전송 범위가 늘어난다.

## 선택한 방법
새 setup-secrets의 JAVA_OPTS를 /var/lib/workflow/heap.hprof로 고정한다. 첫 덤프만 남으며 이미 있으면 JVM이 덮어쓰지 않는다. README에 stat으로 metadata만 확인하고 서버에서 파일 하나를 삭제하는 방법, 로컬 이동·공유 금지를 적었다.

## 전후 수치·결과

```text
HEAP_BEFORE oom_runs=2 files=2
HEAP_CHECK first_exit=3 second_exit=3 files=1 bytes=3221793 unchanged=YES
java.lang.OutOfMemoryError: Java heap space
Dumping heap to /tmp/heap-check/heap.hprof ...
Heap dump file created [3221793 bytes in 0.059 secs]
Terminating due to java.lang.OutOfMemoryError: Java heap space
java.lang.OutOfMemoryError: Java heap space
Dumping heap to /tmp/heap-check/heap.hprof ...
Unable to create /tmp/heap-check/heap.hprof: File exists
Terminating due to java.lang.OutOfMemoryError: Java heap space
```

## 부작용·남은 위험
기존 env는 비밀값 보존을 위해 자동 수정하지 않는다. 예전 설정 사용자는 점검 시간에 JAVA_OPTS의 해당 경로만 수정하고 재시작해야 한다. 첫 덤프가 남아 있는 동안 이후 OOM의 새 덤프를 얻지 못하는 것은 의도한 제한이다. 첫 파일 하나의 크기와 민감성은 여전히 관리해야 한다.

## 커밋
setup-secrets.sh·README·이 문서의 독립 커밋. 최종 보고서에 해시를 기록한다.
