# M3-1 — 근태 스냅샷 스키마와 v1 이관

## 증상
v1 근태는 날짜/시각이 분리되고 단일 상태만 저장해 조퇴·퇴근 누락과 판정 근거를 보존하지 못한다.

## 재현 방법
M3DataMigrationTest에서 V3까지 빈 MariaDB에 적용한 뒤 정상/지각/결근/휴가 4행을 넣고 V4를 실행한다. 부서 기준을 회사 기본과 다르게 만들어 이관이 회사 기본을 쓰는지 확인한다.

## 원인
clock_in_time/clock_out_time과 att_status로는 스냅샷과 정정 문서 근거를 표현할 수 없다.

## 검토했지만 버린 방법과 이유
V1~V3 수정 또는 Hibernate update는 기존 이력을 바꾸므로 제외했다. 과거 시각에 시간대 변환을 적용하는 방식은 DATETIME의 한국 지역 시각을 깨뜨리므로 제외했다.

## 선택한 방법
V4에서 날짜+시각을 DATETIME으로 결합하고 회사 기본 근무시간으로 판정을 재계산한다. ABSENT와 LEAVE 의미는 보존하고 이관 행 수를 M3: migrated_attendances=로 출력한다. 사용자/날짜 유니크는 유지한다. batch_runs, ATTENDANCE_FIX 양식·본문 테이블 및 근태의 fix_document_id를 만든다. schedule.source/departmentName도 출근 뒤 바뀌지 않아야 하므로 sched_source/sched_department_name을 스냅샷 보조 컬럼으로 저장한다.

## 부작용·남은 위험
Flyway SQL에는 Spring Clock을 주입할 수 없어 이관 판단 시각만 UTC_TIMESTAMP + 9시간으로 한국 지역 시각을 구한다. 실행 중 업무 시각은 모두 주입 Clock이다. 근태 정정 처리기는 2차에 연결하므로 1차 중간 커밋은 배포 완료본이 아니다. V4는 새 스키마이므로 기존 마이그레이션 체크섬은 바꾸지 않는다.

## 결과
V3→V4 이관 1회 및 재실행 0회, 데이터 4행 보존, 회사 기본 기준, 초를 버린 경계, 구 컬럼 삭제를 검증한다. MariaDbMigrationTest는 V4까지 전체 실행·validate, 28개 테이블·38개 외래키를 확인한다. 전체 실행 결과는 1차 보고서를 따른다.
