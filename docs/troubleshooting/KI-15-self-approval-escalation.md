# KI-15 — 본인 결재 단계 상향·최상위 부서 기안

## 증상·원인

특근·근태 정정은 기안자의 부서장 한 단계다. 팀장 본인이 기안하면 skip_self가 그 단계를 바로 SKIPPED로 만들어 결재자 0명·즉시 승인으로 끝났다. 휴가·품의의 최상위 부서 기안은 UPPER_DEPT_HEAD에서 부모 부서를 찾지 못해 APPROVER_NOT_FOUND가 됐다.

## 선택한 방법

- DEPT_HEAD·UPPER_DEPT_HEAD의 재직 중 결재자가 본인이고 skip_self가 켜져 있으면, 상위 부서를 순회해 본인 외의 재직 중 부서장을 찾는다. 중간 공석·휴직자는 더 위로 올라간다. 찾으면 FALLBACK과 본인 상향 이유를 미리보기에 표시한다. 끝까지 없으면 원래 본인을 SELF로 건너뛴다.
- 본인 상향은 지시의 조건인 skip_self에 따른다. 기존 fallback_to_upper는 일반 공석 탐색에 계속 적용한다. USER·ROLE 직접 지정 및 본인 참조 제외는 기존대로다. skip_self가 꺼져 있으면 본인 결재자도 원래처럼 남긴다.
- UPPER_DEPT_HEAD에서 최초 부모 부서 자체가 없으면 person=null, skipReason=NO_UPPER_DEPT, note=`상위 부서가 없어 건너뜁니다.`로 반환한다. 부모 부서는 있는데 끝까지 부서장이 없는 경우는 APPROVER_NOT_FOUND다.
- 본인을 상향한 뒤 기존 dedupe_approver를 적용한다. 팀장 휴가·품의에서는 첫 단계와 두 번째 단계가 모두 대표를 가리키므로 두 번째를 DUPLICATE로 저장한다.
- 양식·기본 단계·마이그레이션·API 응답 구조는 바꾸지 않았다. 결재선 해석과 아래 저장 조건만 변경했다.

## 대상 없는 SKIPPED 저장 규칙 — 사용자 추가 결정

기존 NO_REFERENCE는 대상 없이 건너뛴 참조 단계를 저장하지 않았다. approval_lines의 결재자는 NOT NULL이므로 NO_UPPER_DEPT 역시 가짜 결재자를 넣지 않고 같은 방식으로 처리한다. DocumentService.storeLines는 `person == null && skipReason != null`인 단계를 제외한다. 기존 참조 SELF 제외도 유지한다.

결재자가 있는 SELF·DUPLICATE는 기존처럼 SKIPPED 행으로 저장한다. 대상 없는 단계는 seen에 넣지 않고, 저장된 PENDING을 기준으로 즉시 승인 여부를 판단하므로 null 때문에 결재자 조회·알림·상세 조회가 실패하지 않는다.

대표 휴가 테스트에서 다음을 모두 확인한다.

- 양식·기안 미리보기: 본인 SELF와 NO_UPPER_DEPT 두 줄 모두 SKIPPED. 두 번째 result는 null이며 상위 부서 없음 안내가 표시된다.
- 저장된 결재선: SELF 한 줄만 존재.
- 문서: 즉시 APPROVED, 상세 GET 정상.
- 원장: onApproved가 USE -1을 기록하고 RESERVE/RELEASE를 정리하여 총 잔여 10 → 9.

## 버린 방법과 이유

- **특근·근태 정정 양식에 상위 부서장 단계 추가**: 일반 직원 신청까지 대표에게 가고, 기존 최상위 부서 처리라면 대표는 상신할 수 없게 된다. 기안자에 따른 해석 문제를 양식 변경으로 우회하지 않았다.
- 모든 APPROVER_NOT_FOUND를 자동 승인 처리: 공석 때문에 필수 결재가 생략되므로 제외했다. 부모 없음과 부모 공석은 구별한다.
- 대상 없는 단계에 기안자를 대신 저장: 실제로 지정되지 않은 사람의 결재 이력처럼 보이므로 제외했다.
- 결재자 NULL 허용 마이그레이션: 이번 지시의 스키마 변경 금지와 기존 NO_REFERENCE 처리 선례에 따라 선택하지 않았다.

## 수정 전 실패

05f32e8을 작업 트리 밖 archive로 풀고 새 테스트만 추가했다. 기존 공개 클래스·실제 API만 사용해 수정 전에도 컴파일됐다.

```text
ApprovalSelfEscalationTest: Tests run: 17, Failures: 8, Errors: 0, Skipped: 0
팀장 특근·정정 finalLine: expected [대표 ID], but was []
팀장 휴가·품의 첫 단계: expected FALLBACK, but was SKIPPED
대표 휴가·품의: preview.error가 null이어야 하나 APPROVER_NOT_FOUND 안내 반환
중간 공석을 지난 본인 상향 2건: 대표 결재선/상향 안내 단언 실패
→ 수정 후 17/0/0/0
```

초대·운영 설정 테스트와 함께 실행한 최초 재현은 86건 중 실패 24·오류 0·건너뜀 0이었다. 메일 본문 등 일회용 민감 정보가 들어가는 원문은 저장소 기록에 복사하지 않았다.

## 수정 후 기능 검증

ApprovalSelfEscalationTest는 실제 V3/V4 기본 단계를 사용하며 양식 미리보기와 기안 미리보기를 서로 대조하고 실제 임시 저장 → 상신 → 상세 조회를 검사한다.

| 기안자 | 특근·근태 정정 | 휴가·품의 |
| --- | --- | --- |
| 사원 | 팀장, IN_PROGRESS | 팀장 → 대표, IN_PROGRESS |
| 팀장 | 대표, IN_PROGRESS | 대표 한 번, IN_PROGRESS; 중복 단계 SKIPPED |
| 대표 | 결재자 없음, APPROVED | 결재자 없음, APPROVED; 부모 없는 단계는 저장 제외 |

네 양식 × 세 기안자 12건, 중간 공석을 지난 상향 2건, 부모는 있으나 끝까지 공석 1건, 재직 상위 부서장 없는 본인 SELF 1건, skip_self=false 유지 1건 = 17건이다. 실제 대표 휴가의 연차 차감도 포함한다.

기존 테스트 기대값 변경은 3곳이다. 관련 선택 실행에서 두 실패를, 첫 전체 실행에서 나머지 한 실패를 확인했다.

- M2DocumentIntegrationTest#lineRulesHandleNormalSelfFallbackAndDuplicateApprovers: expected SKIPPED / actual FALLBACK. 이제 상신은 IN_PROGRESS이며 USE가 없고, 대표 승인 뒤 APPROVED·USE 1건을 확인한다.
- M2ReviewTest#fallbackExplainsSourceAndDestinationAndSkippedLineHasReason: expected SELF / actual null. 이제 PENDING·대표 결재자·skipReason=null을 확인한다.

- M3BatchTest#batchFinalizesAbsenceLeaveMissingAndLateHalfApprovalIdempotently: 첫 전체 실행에서 expected LEAVE / actual WORKDAY. 팀장의 병가가 이제 상신 후 IN_PROGRESS이므로 대표 승인 뒤 배치를 실행하도록 준비 순서를 바꿨다. 배치 판정·멱등성 단언과 운영 코드는 그대로다.

기존 M3AttendanceFixTest 7건은 사원 → 팀장 정상 승인 경로라 수정 없이 통과했다. 첫 전체 실행은 370/1/0/0, 07:25 min이었으며 완료 증거로 사용하지 않는다. 위 배치 테스트 수정 후 새 외부 archive에서 전체를 다시 실행했다.

## 기준선 응답 영향

외부 사본에서 기존 기록 정책을 그대로 켜고 M2DocumentIntegrationTest·M2ReviewTest·M3BatchTest를 실행했다. **20/0/0/0**, 57.483초, 29개 프레임(HTTP가 있는 프레임 18개), 응답 137개를 기록했다. 기준선과 정규화된 응답 본문·요청 정보를 대조했을 때 아래 3개 테스트만 달랐다. 나머지 기록 프레임의 응답은 동일했다. 전체 기준선을 갱신하거나 전체 기록 비교가 통과했다고 주장하지 않는다.

| 테스트 | 요청 수 전 → 후 | 바뀐 응답 항목과 이유 |
| --- | --- | --- |
| M2DocumentIntegrationTest#lineRulesHandleNormalSelfFallbackAndDuplicateApprovers | 6 → 7 | 본인 미리보기 rows[0].status SKIPPED → FALLBACK, note 상향 안내, result 본인 → 대표, finalLine 0 → 1. 상신은 APPROVED → IN_PROGRESS. 대표 승인 요청 추가로 마지막 미리보기 requestIndex 6 → 7. |
| M2ReviewTest#fallbackExplainsSourceAndDestinationAndSkippedLineHasReason | 3 → 3 | 본인이었던 상신 결재자가 대표로 바뀌고 SKIPPED(SELF) → PENDING(null), APPROVED → IN_PROGRESS. |
| M3BatchTest#batchFinalizesAbsenceLeaveMissingAndLateHalfApprovalIdempotently | 8 → 9 | 팀장 병가 상신 결재자가 대표로 바뀌고 IN_PROGRESS가 된다. 배치 전에 대표 승인 요청을 추가했다. |

세 상신 응답에 공통으로 바뀐 키는 data.status, completedAt(시각 → null), line[0].approver의 id/name/department, line[0].status, skipReason(SELF → null), myActions.canWithdraw(false → true)다. 추가 승인 응답은 APPROVED·완료 시각을 가진다. 키 구조와 날짜 형식은 바뀌지 않았다.

기존 SnapshotComparator를 세 메서드 필터로 실행한 종료 코드는 각각 1이며 differences는 36·15·25, sqlOrderChanges는 모두 0이었다. 이 숫자는 응답 값뿐 아니라 상향 조회·승인 추가에 따른 SQL 횟수/집합과 요청 수 차이도 포함한다. 기능 변경이므로 differences=0을 목표로 하지 않았다.

재현 명령(외부 archive의 backend에서):

```bash
JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home ./mvnw -Duser.timezone=UTC -Drefactoring.record=target/ki15-record -Dtest=M2DocumentIntegrationTest,M2ReviewTest,M3BatchTest test
REFACTORING_CLASSPATH=target/ki15-record/classpath.txt JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home bash src/test/resources/refactoring-tools/compare.sh src/test/resources/refactoring-baseline target/ki15-record lineRulesHandleNormalSelfFallbackAndDuplicateApprovers
```

마지막 인자를 나머지 두 메서드 이름으로 바꾸면 해당 비교를 재현할 수 있다. sql.json/responses.json/masked-fields.txt는 변경하지 않았다. 기준선 갱신 여부는 리뷰에서 결정한다.

## 전체 검증·커밋

- 최종 전체: **370/0/0/0**(실행/실패/오류/건너뜀), 67개 테스트 클래스, Maven **10:15 min**, BUILD SUCCESS. 기존 293개 + 등록/재초대 2개 + 운영 설정 58개 + 결재선 17개.
- `/private/tmp/onboarding-approval-review/full-final` 외부 archive, Java 17, `-Duser.timezone=UTC`, Testcontainers만 사용했다. 기록 옵션을 끈 일반 실행이다. `backend/src` 373개 파일의 SHA-256이 최종 작업 트리와 동일하다.
- 응답 기록은 별도 `/private/tmp/onboarding-approval-review/record-final`에서 위 세 클래스만 수행했으며, 일반 전체 실행을 대체하지 않는다.
- 초대 메일: `9b77385` — `fix(api): 초대 메일 아이디 안내`.
- 초기 관리자 설정: `e8e5eb2` — `fix(deploy): 초기 관리자 비밀번호 검사 일치`.
- 결재선: 이 기록과 같은 `fix(api): 본인 결재 단계 상향과 최상위 부서 기안` 커밋.
- 셸 구문·정책 51개 일치·기존 메일 설정 검사 결과는 KI-14를 따른다. frontend·backend/md·마이그레이션·기준선·정책은 변경하지 않았다. 실제 DB/Redis/서버/메일 호출, 병합·푸시·배포는 하지 않았다.
- 작업 중 별도로 바뀐 KI-10 문서는 수정·스테이징·커밋하지 않았다.

최종 전체 재현(외부 사본의 backend에서):

```bash
JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home ./mvnw -Duser.timezone=UTC test
```

archive에는 `backend docs/refactoring`을 함께 포함한다. 셸 검증까지 실행할 때는 `deploy`도 포함한다. 작업 트리에서 mvn clean은 실행하지 않았다.

## 남은 한계

대표 또는 본인 위에 재직 중 부서장이 없는 단일 단계 문서는 지시대로 즉시 승인될 수 있다. 모든 경우에 반드시 다른 사람의 승인을 요구하는 정책은 추가하지 않았다. 기존 상신 시점의 조직 조회·스냅샷과 기존 권한/잠금 경계는 유지했다. 실제 서버·메일·사용자 화면 확인은 수행하지 않았다.
