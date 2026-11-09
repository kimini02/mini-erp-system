# R-04 4단계 묶음 2 — approval · leave · overtime

## 비교 기준과 범위

시작 커밋은 `c531e1f4ade77950eb692b55622ac18bbb8e7400`이다. 명세 10차 병합은 준비보다 먼저 수행했으며 `backend/md/17-refactoring.md` 한 파일만 들어왔다. 병합 후 컴파일을 확인했다. 이후 명세는 편집하지 않았다.

이번 묶음은 응답 record, 이름, 같은 클래스의 private 메서드 분할을 함께 적용했다. SQL·트랜잭션·빈 호출/시계/throw 순서·JSON 계약을 그대로 유지한다. 묶음 3은 시작하지 않았다.

## 커밋

첫 보고서·증거 커밋은 `83c80d3`이다. 사용자 확인 뒤 검색 매개변수 이름을 `24ac286`에서 보완했다. 최종 문서 커밋은 `docs(refactor): 결재함 검색 이름 보완 검증 기록`이며 Java 변경은 없다. 아래 추가 검증은 최종 Java 소스와 동일한 `24ac286`을 대상으로 새로 실행했다.

| 커밋 | 묶음 | 변경 파일 |
| --- | --- | --- |
| 1b4cab1 | 준비 병합 | 1 |
| c531e1f | 묶음 1 리뷰 보완 | 7 |
| fac0625 | 검증 도구 | 3 |
| 21995b1 | 본문 4종 | 10 |
| 34aa34e | 문서·결재선 | 9 |
| c46cbf2 | 출현 위치별 검사 보완 | 2 |
| 47a2cca | 양식·결재함 | 9 |
| 941e351 | 휴가 | 13 |
| 83c80d3 | 첫 보고서·증거 | 64 |
| 24ac286 | 결재함 검색 매개변수 이름 | 1 |

## 준비 보완 결과

- LockedRole.from(role,locked)를 사용하고 CHIEF 판단은 서비스에 남겼다. updateDepartmentWorkSchedule/getLookupUsers 이름 대응을 반영했다.
- 묶음 1 보고서의 구성요소 근거와 남은 행 Map 용도를 수정했다. 직급·휴일 생성 응답의 요청 식, 목록 page 식, 트리의 조회 없는 head 변환도 실제 사용처에 맞췄다. data_scope/status는 ENUM이 아닌 실제 VARCHAR(20)이다.
- test-compile, 순서·응답·SQL 대조 차이 0, @Transactional 83개 동일, 미사용 import 0.
- 준비 기록 ON: 231/0/0/0, 592.06초, differences=0, 순서 경고 12개. permission·user 임시 비교도 차이 0.
- 승인된 순서 경고 목록은 14개다. 추가된 M4LegacyExperimentTest의 격리수준 조회와 UPDATE는 barrier 전 작업 스레드에서 섞일 수 있다. 목록 자체와 실제 경고 목록은 증거 JSON에 남겼다.

## 커밋별 검증

(a) 테스트 소스·리소스 무변경, (b) repository/허용 global의 문자열과 domain 외 SQL 위치, (c) 트랜잭션, (d) 실행 순서, (e) 응답 구조, (f) import·형식, (g) 이름/길이 측정, (h) 기록 ON, (i) 기록 OFF, (j) 추가 probe를 각각 확인했다. 컴파일·정적 대조·probe를 작업 트리 밖 후보에서 완료한 뒤 적용했고, 필수 전체 ON/OFF는 적용한 작업 트리에서 순차 실행했다.

| 묶음 | (a) 테스트 | (b) 차이/외부 SQL | (c) Tx | (d) 순서 | (e) 응답 | (f) import | (h) ON | (i) OFF | (j) probe |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| bodies | 동일 | 0/0 | 83 | 0 | 0 | 0 | 231/0/0/0 · 799.93s · diff 0 · 경고 10 | 231/0/0/0 · 609.27s · 기록 0 | 2/0/0/0 · 52.8s · diff 0 |
| documents | 동일 | 0/0 | 83 | 0 | 0 | 0 | 231/0/0/0 · 439.85s · diff 0 · 경고 12 | 231/0/0/0 · 406.22s · 기록 0 | 2/0/0/0 · 43.45s · diff 0 |
| approval | 동일 | 0/0 | 83 | 0 | 0 | 0 | 231/0/0/0 · 421.87s · diff 0 · 경고 12 | 231/0/0/0 · 419.44s · 기록 0 | 2/0/0/0 · 20.9s · diff 0 |
| leave | 동일 | 0/0 | 83 | 0 | 0 | 0 | 231/0/0/0 · 414.33s · diff 0 · 경고 11 | 231/0/0/0 · 407.17s · 기록 0 | 2/0/0/0 · 21.13s · diff 0 |
| query 이름 보완 | 동일 | 0/0 | 83 | 0 | 0 | 0 | 231/0/0/0 · 463.83s · diff 0 · 경고 10 | 231/0/0/0 · 458.55s · 기록 0 | 2/0/0/0 · 29.17s · diff 0 |

형식 검사는 새 120자 초과·여러 문장·여러 선언자·부적절한 분할 0이다. 기존 문자열 예외 88곳, 수정 범위 밖 AttendanceStore의 보호된 텍스트 블록 닫는 줄과 ProjectMember 1개 단위 예외는 그대로다. 테스트 형식 단위 위반은 0이다. 안전망 파일과 SQL/응답 기준선 해시는 그대로다.

초기 외부 본문 후보의 ON/OFF 실행(482.79/449.26초)은 사전 확인이다. 위 표는 이를 재사용하지 않고 작업 트리 적용 뒤 다시 실행한 필수 결과다. 시간은 개발 머신 상태에 따른 관측값이며 성능 개선 근거로 쓰지 않는다.

## 도구 보완과 판별력

처리기 수신자의 정적 타입이 주입 빈 필드 타입 또는 List<빈> 요소 타입이면 호출 사건으로 기록한다. onSubmitted를 updateSubmission 앞으로 옮기면 차이 1, body를 findHistory 앞으로 옮기면 차이 5다(그 상세를 부르는 다른 공개 경로에도 전파). 원본 자체 대조는 0이다.

InboxRepository.search에 있던 쪽 검사를 서비스로 옮기므로, 순서 도구는 이 정확한 한 메서드 경계를 양쪽 모두 펼쳐 throw와 SqlStore 호출을 대조한다. 다른 repository 경계는 그대로다. 검사를 조회 뒤로 옮긴 변조 사본은 차이 1로 실패했다.

응답 도구는 presentation에서 만든 map과 같은 out의 put을 연결하고, 람다 map·중첩 메서드 원천·JsonProperty를 대조한다. 최종 응답 대조 결과는 group2-query-rename-checks.json의 response에 있다. 응답 소스는 이번 이름 보완에서 바뀌지 않았으며, 구성요소별 대조 내용은 group2-leave-checks.json의 response.rows에 보존했다.

### 결재함 문자열 예외 (추가 사용자 결정)

처음 지시의 “빠진 모든 문자열은 InboxService에 새로 남아야 한다”는 조건과 record 전환이 충돌했다. 사용자가 출현 위치별 분류를 확정했다. backend/md는 편집하지 않았다.

| 분류 | 빠진 출현 | 대조 방법 |
| --- | --- | --- |
| SQL 문장·조각·파라미터 | 0 | 응답 영역 밖 문자열 다중집합 엄격 동일 |
| 응답 map 키 | 16 | 원래 위치의 map 키 → 중첩 record 구성요소 1:1 |
| 행 조회 키 | 15 | InboxService + 항목 record에서 같은 값의 증가 횟수 |
| 상태값 | 20 | 같은 두 파일에서 같은 값의 증가 횟수 |
| 미분류 | 0 | 하나라도 있으면 실패 |

합계 51곳은 빠진 문자열 전체와 같다. repository에 새로 생긴 값은 total/content/lines 각 2회다. SQL 소유 파일의 전체 문자열 출현은 1,022→977이며 차이 45는 제거 51·추가 6으로만 설명된다. title/status/id/name이 키와 값으로 겹쳐도 AST의 인자 위치로 나눠 세므로 서로 상쇄하지 않는다.

원래 응답 키 경로: `id, docNo, formCode, formName, title, drafter, drafter.name, drafter.title, drafter.department, submittedAt, updatedAt, status, steps, currentApprover, currentApprover.name, currentApprover.title`.


출현 분류 `rowKey`

| 문자열 값 | 횟수 |
| --- | --- |
| approver_name | 1 |
| approver_title | 1 |
| code | 2 |
| department_name | 1 |
| doc_no | 1 |
| id | 2 |
| position_name | 1 |
| status | 2 |
| submitted_at | 1 |
| title | 1 |
| updated_at | 1 |
| user_name | 1 |

출현 분류 `stateValue`

| 문자열 값 | 횟수 |
| --- | --- |
| APPROVED | 1 |
| CURRENT | 2 |
| DONE | 2 |
| DRAFT | 1 |
| IN_PROGRESS | 2 |
| PENDING | 2 |
| REJECTED | 2 |
| SKIPPED | 2 |
| TODO | 2 |
| WITHDRAWN | 4 |

출현 분류 `responseKey`

| 문자열 값 | 횟수 |
| --- | --- |
| currentApprover | 1 |
| department | 1 |
| docNo | 1 |
| drafter | 1 |
| formCode | 1 |
| formName | 1 |
| id | 1 |
| name | 2 |
| status | 1 |
| steps | 1 |
| submittedAt | 1 |
| title | 3 |
| updatedAt | 1 |

SQL 파라미터, 응답 키, 행 조회 키 횟수, 상태값 횟수, 미분류 원본 문자열을 각각 바꾼 사본은 모두 차이 1로 실패했다. 응답 키 title만 바꾸고 행 조회 title은 둔 사본도 차이 1이다. 상세 위치(파일 내 문자 오프셋·줄·분류)는 증거의 occurrences에 있다.

## 추가 임시 비교 테스트

`R-04-probe/R04Group2Probe.java`를 외부 시작·후보 사본의 src/test에만 복사해 실행했다. 저장소 src/test에는 추가하지 않았다. PUT/DELETE 성공, 품의 amount 값, 휴가 handover 값, 연결 문서가 있는 관리자 원장, 빈 결재함을 검증했다. 시작 기록 대비 SQL·JSON 차이는 각 후보에서 0이다.

## API → 응답 record

ApiResponse의 공통 외피와 message는 유지한다. 아래는 data의 타입이다.

| API | 반환 타입 | 근거 |
| --- | --- | --- |
| POST /api/v1/approval-documents/line-preview | ApiResponse&lt;ApprovalLinePreviewResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| POST /api/v1/approval-documents | ApiResponse&lt;ApprovalDraftResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| PUT /api/v1/approval-documents/{id} | ApiResponse&lt;ApprovalDocumentResponseDto&gt; | PUT/DELETE 문서는 추가 probe로 성공 경로 보완 |
| DELETE /api/v1/approval-documents/{id} | ApiResponse&lt;Void&gt; | PUT/DELETE 문서는 추가 probe로 성공 경로 보완 |
| GET /api/v1/approval-documents/{id} | ApiResponse&lt;ApprovalDocumentResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| POST /api/v1/approval-documents/{id}/submit | ApiResponse&lt;ApprovalDocumentResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| POST /api/v1/approval-documents/{id}/approve | ApiResponse&lt;ApprovalDocumentResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| POST /api/v1/approval-documents/{id}/reject | ApiResponse&lt;ApprovalDocumentResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| POST /api/v1/approval-documents/{id}/withdraw | ApiResponse&lt;ApprovalDocumentResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| POST /api/v1/approval-documents/{id}/resubmit | ApiResponse&lt;ApprovalDraftResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| GET /api/v1/approval-forms | ApiResponse&lt;List&lt;ApprovalFormSummaryResponseDto&gt;&gt; | 기존 전체 기록 + 구조 대조 |
| GET /api/v1/approval-forms/{code} | ApiResponse&lt;ApprovalFormResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| PATCH /api/v1/approval-forms/{code} | ApiResponse&lt;ApprovalFormResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| PUT /api/v1/approval-forms/{code}/steps | ApiResponse&lt;ApprovalFormResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| POST /api/v1/approval-forms/{code}/line-preview | ApiResponse&lt;ApprovalLinePreviewResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| GET /api/v1/approval-inbox | ApiResponse&lt;PageResponseDto&lt;ApprovalInboxItemResponseDto&gt;&gt; | 기존 전체 기록 + 구조 대조 |
| GET /api/v1/approval-inbox/counts | ApiResponse&lt;ApprovalInboxCountsResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| GET /api/v1/leave/admin/balances | ApiResponse&lt;PageResponseDto&lt;LeaveAdminBalanceResponseDto&gt;&gt; | 기존 전체 기록 + 구조 대조 |
| GET /api/v1/leave/admin/ledger/{userId} | ApiResponse&lt;List&lt;LeaveLedgerEntryResponseDto&gt;&gt; | 기존 전체 기록 + 구조 대조 |
| POST /api/v1/leave/admin/adjustments | ApiResponse&lt;LeaveAdjustmentResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| POST /api/v1/leave/admin/grant-runs | ApiResponse&lt;LeaveGrantRunResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| GET /api/v1/leave/admin/grant-runs | ApiResponse&lt;List&lt;LeaveGrantRunResponseDto&gt;&gt; | 기존 전체 기록 + 구조 대조 |
| GET /api/v1/leave/policy | ApiResponse&lt;List&lt;LeavePolicyResponseDto&gt;&gt; | 기존 전체 기록 + 구조 대조 |
| GET /api/v1/leave/balance | ApiResponse&lt;LeaveBalanceResponseDto&gt; | 기존 전체 기록 + 구조 대조 |
| GET /api/v1/leave/ledger | ApiResponse&lt;List&lt;LeaveLedgerEntryResponseDto&gt;&gt; | 기존 전체 기록 + 구조 대조 |
| POST /api/v1/leave/days-preview | ApiResponse&lt;LeaveDaysPreviewResponseDto&gt; | 기존 전체 기록 + 구조 대조 |

## 구성요소 근거

일반 품의 amount, 휴가 handover, 관리자 원장 document는 기준선의 늘 null인 경로라 추가 probe로 값 있는 경우를 확인한다.

### ApprovalDocumentResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| id | id | long | 요청 id(long) |
| docNo | docNo | java.lang.String | approval_documents.doc_no VARCHAR(50) NULL |
| form | form | com.minierp.backend.domain.approval.dto.ApprovalDocumentResponseDto.Form | 문서 SELECT의 form_code / form_name 문자열 |
| title | title | java.lang.String | approval_documents.title VARCHAR(255) NOT NULL |
| status | status | java.lang.String | approval_documents.status VARCHAR(20) NOT NULL |
| drafter | drafter | com.minierp.backend.domain.approval.dto.ApprovalPersonResponseDto | resolver.getPerson(drafter_id)의 id/name/title/department; 본문보다 먼저 조회 |
| submittedAt | submittedAt | java.time.LocalDateTime | approval_documents.submitted_at DATETIME NULL → LocalDateTime |
| completedAt | completedAt | java.time.LocalDateTime | approval_documents.completed_at DATETIME NULL → LocalDateTime |
| body | body | com.minierp.backend.domain.approval.dto.ApprovalFormBodyDto | 이력 조회 후 handler(form_code).body(id); marker 인터페이스의 본문 record 4종 |
| line | line | java.util.List&lt;com.minierp.backend.domain.approval.dto.ApprovalDocumentResponseDto.Line&gt; | APPROVER 행만 원래 결재선 순서로 변환 |
| references | references | java.util.List&lt;com.minierp.backend.domain.approval.dto.ApprovalPersonResponseDto&gt; | REFERENCE 행의 snapshot만 원래 순서로 변환 |
| history | history | java.util.List&lt;com.minierp.backend.domain.approval.dto.ApprovalDocumentResponseDto.History&gt; | findHistory 결과 원래 순서 |
| myActions | myActions | com.minierp.backend.domain.approval.dto.ApprovalDocumentResponseDto.MyActions | 원래 canAct / own / pending 및 line 상태 검사 boolean 식 |
| resubmittedFromId | resubmittedFromId | java.lang.Long | approval_documents.resubmitted_from_id BIGINT NULL → Long |

### ApprovalDocumentResponseDto.Form

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| code | code | java.lang.String | approval_forms.code VARCHAR NOT NULL AS form_code |
| name | name | java.lang.String | approval_forms.name VARCHAR NOT NULL AS form_name |

### ApprovalDocumentResponseDto.Line

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| order | order | long | approval_lines.step_order INT NOT NULL → long |
| kind | kind | java.lang.String | approval_lines.line_kind VARCHAR(20) NOT NULL |
| approver | approver | com.minierp.backend.domain.approval.dto.ApprovalPersonResponseDto | approver_id/name/title/dept 스냅샷 행 |
| source | source | java.lang.String | approval_lines.source VARCHAR(20) NOT NULL |
| rule | rule | java.lang.String | approval_lines.rule_label VARCHAR NULL |
| status | status | java.lang.String | approval_lines.status VARCHAR(20) NULL |
| skipReason | skipReason | java.lang.String | approval_lines.skip_reason VARCHAR NULL |
| actedAt | actedAt | java.time.LocalDateTime | approval_lines.acted_at DATETIME NULL → LocalDateTime |
| comment | comment | java.lang.String | approval_lines.comment VARCHAR NULL |

### ApprovalDocumentResponseDto.History

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| action | action | java.lang.String | approval_histories.action VARCHAR NOT NULL |
| actor | actor | com.minierp.backend.domain.approval.dto.ApprovalDocumentResponseDto.Actor | actor_id가 null이면 null, 아니면 Actor.from(row) |
| comment | comment | java.lang.String | approval_histories.comment VARCHAR NULL |
| at | at | java.time.LocalDateTime | approval_histories.created_at DATETIME NOT NULL → LocalDateTime |

### ApprovalDocumentResponseDto.Actor

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| id | id | long | actor_id BIGINT NULL을 null 검사 후 long으로 변환 |
| name | name | java.lang.String | users.user_name VARCHAR LEFT JOIN 결과 |

### ApprovalDocumentResponseDto.MyActions

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| canApprove | canApprove | boolean | canAct(boolean) |
| canReject | canReject | boolean | canAct(boolean) |
| canWithdraw | canWithdraw | boolean | own && pending && line.stream().noneMatch(APPROVED) |
| canResubmit | canResubmit | boolean | own && Set.of(REJECTED,WITHDRAWN).contains(status) |

### ApprovalDraftResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| id | id | long | insertDraft 반환 또는 newId(long) |
| status | status | java.lang.String | 기존 리터럴 "DRAFT" |

### ApprovalFormResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| code | code | java.lang.String | approval_forms.code VARCHAR(30) NOT NULL |
| name | name | java.lang.String | approval_forms.name VARCHAR(100) NOT NULL |
| shortName | shortName | java.lang.String | FormService.shortName(formCode), findSteps 앞에서 평가 |
| prefix | prefix | java.lang.String | approval_forms.doc_prefix VARCHAR(10) NOT NULL |
| description | description | java.lang.String | description(formCode)의 switch 결과 String |
| options | options | com.minierp.backend.domain.approval.dto.ApprovalFormResponseDto.Options | 같은 form 행의 bool 네 값 |
| steps | steps | java.util.List&lt;com.minierp.backend.domain.approval.dto.ApprovalFormResponseDto.Step&gt; | findSteps(form.id) 행을 원래 정렬 순서로 변환 |

### ApprovalFormResponseDto.Options

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| dedupeApprover | dedupeApprover | boolean | bool(row,"dedupe_approver") boolean |
| fallbackToUpper | fallbackToUpper | boolean | bool(row,"fallback_to_upper") boolean |
| skipSelf | skipSelf | boolean | bool(row,"skip_self") boolean |
| allowLineEdit | allowLineEdit | boolean | bool(row,"allow_line_edit") boolean |

### ApprovalFormResponseDto.Step

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| order | order | long | approval_form_steps.step_order INT NOT NULL → number(row,"step_order") long |
| lineKind | lineKind | java.lang.String | approval_form_steps.line_kind VARCHAR(20) NOT NULL |
| approverType | approverType | java.lang.String | approval_form_steps.approver_type VARCHAR(30) NOT NULL |
| roleCode | roleCode | java.lang.String | LEFT JOIN roles.code VARCHAR(40) → String 또는 null |
| userId | userId | java.lang.Long | approval_form_steps.user_id BIGINT NULL → null 또는 number(row,"user_id") long |
| label | label | java.lang.String | 서비스 stepLabel의 switch 결과; role_name·user_name은 LEFT JOIN String 또는 null |

### ApprovalFormSummaryResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| code | code | java.lang.String | approval_forms.code VARCHAR(30) NOT NULL |
| name | name | java.lang.String | approval_forms.name VARCHAR(100) NOT NULL |
| shortName | shortName | java.lang.String | FormService.shortName(code)의 switch 결과 String |
| prefix | prefix | java.lang.String | approval_forms.doc_prefix VARCHAR(10) NOT NULL |
| description | description | java.lang.String | description(code)의 switch 결과 String |
| allowLineEdit | allowLineEdit | boolean | bool(form,"allow_line_edit")의 boolean |
| approverStepCount | approverStepCount | long | findForms의 APPROVER COUNT 서브쿼리 AS approvers → number(form,"approvers") long |
| referenceStepCount | referenceStepCount | long | findForms의 REFERENCE COUNT 서브쿼리 AS refs → number(form,"refs") long |

### ApprovalGeneralBodyResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| content | content | java.lang.String | general_requests.content TEXT NOT NULL → String |
| amount | amount | java.lang.Long | general_requests.amount BIGINT NULL → null 또는 Number.longValue() |

### ApprovalInboxCountsResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| todo | todo | long | InboxRepository.counts의 해당 BOX COUNT 서브쿼리 → number(row,"todo") long |
| in-progress | inProgress | long | InboxRepository.counts의 해당 BOX COUNT 서브쿼리 → number(row,"in-progress") long |
| done | done | long | InboxRepository.counts의 해당 BOX COUNT 서브쿼리 → number(row,"done") long |
| mine | mine | long | InboxRepository.counts의 해당 BOX COUNT 서브쿼리 → number(row,"mine") long |
| drafts | drafts | long | InboxRepository.counts의 해당 BOX COUNT 서브쿼리 → number(row,"drafts") long |
| reference | reference | long | InboxRepository.counts의 해당 BOX COUNT 서브쿼리 → number(row,"reference") long |

### ApprovalInboxItemResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| id | id | long | approval_documents.id BIGINT NOT NULL; 기존 d.get("id")의 Long → long |
| docNo | docNo | java.lang.String | approval_documents.doc_no VARCHAR(50) NULL; DRAFT는 null |
| formCode | formCode | java.lang.String | approval_forms.code VARCHAR(30) NOT NULL |
| formName | formName | java.lang.String | FormService.shortName((String) document.get("code")) String |
| title | title | java.lang.String | approval_documents.title VARCHAR(255) NOT NULL |
| drafter | drafter | com.minierp.backend.domain.approval.dto.ApprovalInboxItemResponseDto.Drafter | 목록 SELECT의 user_name/position_name/department_name; id 키 없음 |
| submittedAt | submittedAt | java.time.LocalDateTime | approval_documents.submitted_at DATETIME(6) NULL → LocalDateTime |
| updatedAt | updatedAt | java.time.LocalDateTime | approval_documents.updated_at DATETIME(6) NULL → LocalDateTime |
| status | status | java.lang.String | approval_documents.status VARCHAR(20) NOT NULL |
| steps | steps | java.util.List&lt;java.lang.String&gt; | 기안·결재 단계별 상태를 원래 조건과 순서로 추가한 List<String> |
| currentApprover | currentApprover | com.minierp.backend.domain.approval.dto.ApprovalInboxItemResponseDto.CurrentApprover | PENDING이고 문서가 IN_PROGRESS인 줄의 이름·직급, 없으면 null |

### ApprovalInboxItemResponseDto.Drafter

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| name | name | java.lang.String | users.user_name VARCHAR(50) NOT NULL |
| title | title | java.lang.String | positions.name VARCHAR(100) NOT NULL AS position_name |
| department | department | java.lang.String | departments.name VARCHAR(100) NOT NULL AS department_name |

### ApprovalInboxItemResponseDto.CurrentApprover

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| name | name | java.lang.String | approval_lines.approver_name VARCHAR(50) NOT NULL |
| title | title | java.lang.String | approval_lines.approver_title VARCHAR(100) NOT NULL |

### ApprovalLinePreviewResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| rows | rows | java.util.List&lt;com.minierp.backend.domain.approval.dto.ApprovalLinePreviewResponseDto.Row&gt; | resolved.stream().map(previewRow), 실패 때 ERROR 행 하나 추가 |
| finalLine | finalLine | java.util.List&lt;com.minierp.backend.domain.approval.dto.ApprovalPersonResponseDto&gt; | APPROVER이며 skipReason==null인 person, 실패 때 빈 List |
| references | references | java.util.List&lt;com.minierp.backend.domain.approval.dto.ApprovalPersonResponseDto&gt; | REFERENCE이며 skipReason==null인 person |
| error | error | java.lang.String | 성공 null / ResolutionFailure.getMessage() String |

### ApprovalLinePreviewResponseDto.Row

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| order | order | int | Resolved.order() 또는 ex.order의 int |
| kind | kind | java.lang.String | Resolved.kind() 또는 ex.kind의 String |
| rule | rule | java.lang.String | Resolved.rule() 또는 실패 rule switch의 String |
| result | result | com.minierp.backend.domain.approval.dto.ApprovalPersonResponseDto | person 없으면 null, 아니면 ApprovalPersonResponseDto.from |
| status | status | java.lang.String | skipReason != null ? SKIPPED : fallback ? FALLBACK : OK; 실패 ERROR |
| note | note | java.lang.String | 기존 note, SELF/DUPLICATE 설명 또는 ex.getMessage() |

### ApprovalPersonResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| id | id | long | users.id 또는 approval_lines.approver_id BIGINT NOT NULL → long |
| name | name | java.lang.String | users.user_name / approval_lines.approver_name VARCHAR NOT NULL |
| title | title | java.lang.String | positions.name / approval_lines.approver_title VARCHAR |
| department | department | java.lang.String | departments.name / approval_lines.approver_dept VARCHAR |

### AttendanceFixBodyResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| workDate | workDate | java.time.LocalDate | attendance_fix_requests.work_date DATE NOT NULL → LocalDate |
| clockIn | clockIn | java.lang.String | hh(clock_in): null 또는 HH:mm 문자열 |
| clockOut | clockOut | java.lang.String | hh(clock_out): null 또는 HH:mm 문자열 |
| reason | reason | java.lang.String | attendance_fix_requests.reason VARCHAR NOT NULL |
| before | before | com.fasterxml.jackson.databind.JsonNode | json(before_json): null 또는 ObjectMapper.readTree의 JsonNode |

### LeaveAdjustmentResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| id | id | long | store.insertAdjustment 반환 long |
| userId | userId | long | 요청 user(long) |
| year | year | int | 요청 year(int), year(year) 검증 유지 |
| available | available | java.math.BigDecimal | current.add(days)의 BigDecimal; 원래 반환 식과 동일 |

### LeaveAdminBalanceResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| user | user | com.minierp.backend.domain.leave.dto.LeaveAdminBalanceResponseDto.User | findBalances의 users/positions/departments JOIN 행 |
| hireDate | hireDate | java.time.LocalDate | users.hire_date DATE NULL → LocalDate |
| hireDateMissing | hireDateMissing | boolean | row.get("hire_date") == null boolean |
| granted | granted | java.math.BigDecimal | COALESCE(l.granted,0), GRANT days 합계 → BigDecimal |
| used | used | java.math.BigDecimal | COALESCE(l.used,0), USE days 합계의 음수 → BigDecimal |
| reserved | reserved | java.math.BigDecimal | COALESCE(l.reserved,0), RESERVE/RELEASE days 합계의 음수 → BigDecimal |
| adjusted | adjusted | java.math.BigDecimal | COALESCE(l.adjusted,0), ADJUST days 합계 → BigDecimal |
| available | available | java.math.BigDecimal | COALESCE(l.available,0), 해당 연도 days 합계 → BigDecimal |

### LeaveAdminBalanceResponseDto.User

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| id | id | long | users.id BIGINT NOT NULL → number(row,"id") long |
| name | name | java.lang.String | users.user_name VARCHAR(50) NOT NULL |
| title | title | java.lang.String | positions.name VARCHAR(100) NOT NULL AS title |
| department | department | java.lang.String | departments.name VARCHAR(100) NOT NULL AS department |

### LeaveBalanceResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| granted | granted | java.math.BigDecimal | COALESCE(SUM(CASE GRANT THEN days ELSE 0 END),0) AS granted → BigDecimal |
| used | used | java.math.BigDecimal | -COALESCE(SUM(CASE USE THEN days ELSE 0 END),0) AS used → BigDecimal |
| reserved | reserved | java.math.BigDecimal | -COALESCE(SUM(CASE RESERVE/RELEASE THEN days ELSE 0 END),0) AS reserved → BigDecimal |
| available | available | java.math.BigDecimal | COALESCE(SUM(days),0) AS available → BigDecimal |
| year | year | int | year(requestedYear)의 검증된 int; Clock 평가 후 SQL 결과와 함께 전달 |

### LeaveBodyResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| leaveType | leaveType | java.lang.String | leave_requests.leave_type VARCHAR(50) NOT NULL → String |
| startDate | startDate | java.time.LocalDate | leave_requests.start_date DATE NOT NULL → SqlStore LocalDate |
| endDate | endDate | java.time.LocalDate | leave_requests.end_date DATE NOT NULL → SqlStore LocalDate |
| usedDays | usedDays | java.math.BigDecimal | leave_requests.used_days DECIMAL(4,1) NOT NULL → BigDecimal; scale 1.0 유지 |
| handover | handover | com.minierp.backend.domain.leave.dto.LeaveBodyResponseDto.Handover | handover_user_id가 있을 때만 findHandoverName, 없으면 null |
| reason | reason | java.lang.String | leave_requests.reason VARCHAR(500) NULL → String |

### LeaveBodyResponseDto.Handover

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| id | id | long | users.id BIGINT NOT NULL → number(row,"id") |
| name | name | java.lang.String | users.user_name VARCHAR NOT NULL AS name |

### LeaveDaysPreviewResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| days | days | java.math.BigDecimal | BigDecimal.ZERO 또는 handler.days(type,start,end)의 BigDecimal |
| offDays | offDays | long | ChronoUnit.DAYS.between(start,end)+1-workingDays.count(start,end)의 long; 예외 LEAVE_NO_WORKING_DAY이면 기간 전체 일수 |
| available | available | java.math.BigDecimal | handler.available(access.actor(),start.getYear())의 BigDecimal |
| after | after | java.math.BigDecimal | available.subtract(days)의 BigDecimal |
| error | error | java.lang.String | 성공 null 또는 기존 ErrorCode/BusinessException 메시지 String |

### LeaveGrantRunResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| id | id | long | batch_runs.id BIGINT NOT NULL → number(row,"id") long |
| jobName | jobName | java.lang.String | batch_runs.job_name VARCHAR(50) NOT NULL AS jobName |
| targetDate | targetDate | java.time.LocalDate | batch_runs.target_date DATE NOT NULL AS targetDate → LocalDate |
| status | status | java.lang.String | batch_runs.status VARCHAR(20) NOT NULL |
| processedCount | processedCount | long | batch_runs.processed_count INT NOT NULL AS processedCount → number(row,"processedCount") long |
| skippedCount | skippedCount | long | batch_runs.skipped_count INT NOT NULL AS skippedCount → number(row,"skippedCount") long |
| message | message | java.lang.String | batch_runs.message TEXT NULL |
| startedAt | startedAt | java.time.LocalDateTime | batch_runs.started_at DATETIME(6) NOT NULL AS startedAt → LocalDateTime |
| finishedAt | finishedAt | java.time.LocalDateTime | batch_runs.finished_at DATETIME(6) NOT NULL AS finishedAt → LocalDateTime |

### LeaveLedgerEntryResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| date | date | java.time.LocalDate | leave_ledger.created_at DATETIME(6) NOT NULL → LocalDateTime.toLocalDate() |
| type | type | java.lang.String | leave_ledger.entry_type VARCHAR(20) NOT NULL |
| days | days | java.math.BigDecimal | leave_ledger.days DECIMAL(8,1) NOT NULL → BigDecimal |
| balance | balance | java.math.BigDecimal | SUM(x.days) OVER(ORDER BY x.created_at,x.id ROWS UNBOUNDED PRECEDING) → BigDecimal |
| memo | memo | java.lang.String | leave_ledger.memo VARCHAR(500) NULL |
| document | document | com.minierp.backend.domain.leave.dto.LeaveLedgerEntryResponseDto.Document | document_id가 null이면 null, 아니면 같은 원장 행의 연결 문서 정보 |

### LeaveLedgerEntryResponseDto.Document

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| id | id | long | leave_ledger.document_id BIGINT NULL을 null 검사 후 long 변환 |
| docNo | docNo | java.lang.String | LEFT JOIN approval_documents.doc_no VARCHAR(50) NULL |
| status | status | java.lang.String | LEFT JOIN approval_documents.status VARCHAR(20) NOT NULL; 조회 결과 String 또는 null |

### OvertimeBodyResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| overtimeDate | overtimeDate | java.time.LocalDate | overtime_requests.overtime_date DATE NOT NULL AS overtimeDate |
| startTime | startTime | java.lang.String | TIME_FORMAT(start_time,'%H:%i:%s') 문자열 |
| endTime | endTime | java.lang.String | TIME_FORMAT(end_time,'%H:%i:%s') 문자열 |
| reason | reason | java.lang.String | overtime_requests.reason VARCHAR(500) NULL |

### PageResponseDto

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| content | content | java.util.List&lt;T&gt; | PageResponseDto.of(content,...)의 List<T> 인자 그대로 |
| page | page | com.minierp.backend.global.response.PageResponseDto.Page | Page.of(page,size,total,content.size()) |

### PageResponseDto.Page

| 키 | 구성요소 | 타입 | 실제 근거 |
| --- | --- | --- | --- |
| number | number | int | page 인자(int) |
| size | size | int | size 인자(int) |
| totalElements | totalElements | long | total 인자(long) |
| totalPages | totalPages | long | long pages = (total + size - 1) / size |
| first | first | boolean | page == 0(boolean) |
| last | last | boolean | page + 1 >= pages(boolean) |
| numberOfElements | numberOfElements | int | content.size()의 int |



## 조건부 키와 변환 위치

- 양식 목록은 allowLineEdit·두 COUNT, 상세는 options·steps를 가진다. 별도 record 두 개로 나눴다.
- 결재함 drafter는 id 없이 name/title/department만, 문서의 drafter는 id를 포함한다. 결재함 currentApprover는 name/title만 있거나 null이다.
- 품의 amount, 휴가 handover, 원장 document는 null 키를 유지한다. 값 있는 경로는 추가 probe로 확인했다.
- from은 그 행만 읽는다. 연도·잠금 여부·인수자 조회 결과·양식 step label처럼 외부 계산이 필요한 값은 서비스가 먼저 구해 전달한다.
- 상세: 문서→actor→읽기 권한 COUNT→결재선→기안자→이력→본문 순서 유지. 양식 shortName은 findSteps보다 앞이다.
- 부여 실행 변환은 LeaveAdminService에서 run이 돌아온 뒤 한다. LeaveGrantService의 실패 기록 try/catch 안에 넣지 않았다.

## 이름과 메서드 분할

| 기존 | 변경 |
| --- | --- |
| approval.service.DocumentService#create | approval.service.DocumentService#createDocument |
| approval.service.DocumentService#update | approval.service.DocumentService#updateDocument |
| approval.service.DocumentService#delete | approval.service.DocumentService#deleteDocument |
| approval.service.DocumentService#detail | approval.service.DocumentService#getDocument |
| approval.service.DocumentService#submit | approval.service.DocumentService#submitDocument |
| approval.service.DocumentService#act | approval.service.DocumentService#decideDocument |
| approval.service.DocumentService#withdraw | approval.service.DocumentService#withdrawDocument |
| approval.service.DocumentService#resubmit | approval.service.DocumentService#resubmitDocument |
| approval.service.ApprovalLineResolver#form | approval.service.ApprovalLineResolver#getForm |
| approval.service.ApprovalLineResolver#person | approval.service.ApprovalLineResolver#getPerson |
| approval.service.ApprovalLineResolver#resolve | approval.service.ApprovalLineResolver#resolveLine |
| approval.service.ApprovalLineResolver#preview | approval.service.ApprovalLineResolver#getLinePreview |
| approval.service.FormService#list | approval.service.FormService#getForms |
| approval.service.FormService#detail | approval.service.FormService#getForm |
| approval.service.FormService#patch | approval.service.FormService#updateFormOptions |
| approval.service.FormService#steps | approval.service.FormService#updateFormSteps |
| approval.service.InboxService#list | approval.service.InboxService#getInboxDocuments |
| approval.service.InboxService#counts | approval.service.InboxService#getInboxCounts |
| leave.service.LeaveQueryService#balance | leave.service.LeaveQueryService#getBalance |
| leave.service.LeaveQueryService#ledger(java.lang.Integer,java.lang.String) | leave.service.LeaveQueryService#getLedgerEntries(java.lang.Integer,java.lang.String) |
| leave.service.LeaveQueryService#ledgerFor | leave.service.LeaveQueryService#getUserLedgerEntries |
| leave.service.LeaveQueryService#preview | leave.service.LeaveQueryService#getDaysPreview |
| leave.service.LeaveAdminService#balances | leave.service.LeaveAdminService#getBalances |
| leave.service.LeaveAdminService#ledger | leave.service.LeaveAdminService#getUserLedgerEntries |
| leave.service.LeaveAdminService#adjust | leave.service.LeaveAdminService#createAdjustment |
| leave.service.LeaveAdminService#grant | leave.service.LeaveAdminService#createGrantRun |
| leave.service.LeaveAdminService#runs | leave.service.LeaveAdminService#getGrantRuns |
| approval.service.ApprovalLineResolver#publicPerson | approval.dto.ApprovalPersonResponseDto#from |
| approval.controller.DocumentController#preview(@jakarta.validation.Valid approval.controller.DocumentController.Preview) | approval.controller.DocumentController#getLinePreview(@jakarta.validation.Valid approval.controller.DocumentController.Preview) |
| approval.controller.DocumentController#create(@jakarta.validation.Valid approval.dto.DocumentRequest) | approval.controller.DocumentController#createDocument(@jakarta.validation.Valid approval.dto.DocumentRequest) |
| approval.controller.DocumentController#update(long,@jakarta.validation.Valid approval.dto.DocumentRequest) | approval.controller.DocumentController#updateDocument(long,@jakarta.validation.Valid approval.dto.DocumentRequest) |
| approval.controller.DocumentController#delete(long) | approval.controller.DocumentController#deleteDocument(long) |
| approval.controller.DocumentController#detail(long) | approval.controller.DocumentController#getDocument(long) |
| approval.controller.DocumentController#submit(long) | approval.controller.DocumentController#submitDocument(long) |
| approval.controller.DocumentController#approve(long,approval.controller.DocumentController.Comment) | approval.controller.DocumentController#approveDocument(long,approval.controller.DocumentController.Comment) |
| approval.controller.DocumentController#reject(long,approval.controller.DocumentController.Comment) | approval.controller.DocumentController#rejectDocument(long,approval.controller.DocumentController.Comment) |
| approval.controller.DocumentController#withdraw(long) | approval.controller.DocumentController#withdrawDocument(long) |
| approval.controller.DocumentController#resubmit(long) | approval.controller.DocumentController#resubmitDocument(long) |
| approval.controller.FormController#list() | approval.controller.FormController#getForms() |
| approval.controller.FormController#detail(java.lang.String) | approval.controller.FormController#getForm(java.lang.String) |
| approval.controller.FormController#patch(java.lang.String,approval.service.FormService.Options) | approval.controller.FormController#updateFormOptions(java.lang.String,approval.service.FormService.Options) |
| approval.controller.FormController#steps(java.lang.String,java.util.List<approval.service.FormService.Step>) | approval.controller.FormController#updateFormSteps(java.lang.String,java.util.List<approval.service.FormService.Step>) |
| approval.controller.FormController#preview(java.lang.String,@jakarta.validation.Valid approval.controller.FormController.Preview) | approval.controller.FormController#getFormLinePreview(java.lang.String,@jakarta.validation.Valid approval.controller.FormController.Preview) |
| approval.controller.InboxController#list(java.lang.String,java.lang.String,java.lang.String,int,int) | approval.controller.InboxController#getInboxDocuments(java.lang.String,java.lang.String,java.lang.String,int,int) |
| approval.controller.InboxController#counts() | approval.controller.InboxController#getInboxCounts() |
| leave.controller.LeaveAdminController#balances(java.lang.Integer,java.lang.Long,java.lang.String,int,int) | leave.controller.LeaveAdminController#getBalances(java.lang.Integer,java.lang.Long,java.lang.String,int,int) |
| leave.controller.LeaveAdminController#ledger(long,java.lang.Integer) | leave.controller.LeaveAdminController#getUserLedgerEntries(long,java.lang.Integer) |
| leave.controller.LeaveAdminController#adjust(@jakarta.validation.Valid leave.controller.LeaveAdminController.Adjustment) | leave.controller.LeaveAdminController#createAdjustment(@jakarta.validation.Valid leave.controller.LeaveAdminController.Adjustment) |
| leave.controller.LeaveAdminController#grant(@jakarta.validation.Valid leave.controller.LeaveAdminController.Run) | leave.controller.LeaveAdminController#createGrantRun(@jakarta.validation.Valid leave.controller.LeaveAdminController.Run) |
| leave.controller.LeaveAdminController#runs(int) | leave.controller.LeaveAdminController#getGrantRuns(int) |
| leave.controller.LeavePolicyController#policy() | leave.controller.LeavePolicyController#getPolicies() |
| leave.controller.LeaveQueryController#balance(java.lang.Integer) | leave.controller.LeaveQueryController#getBalance(java.lang.Integer) |
| leave.controller.LeaveQueryController#ledger(java.lang.Integer,java.lang.String) | leave.controller.LeaveQueryController#getLedgerEntries(java.lang.Integer,java.lang.String) |
| leave.controller.LeaveQueryController#preview(com.fasterxml.jackson.databind.JsonNode) | leave.controller.LeaveQueryController#getDaysPreview(com.fasterxml.jackson.databind.JsonNode) |

| 기존 메서드/줄 | 현재 메서드/줄 | 나눈 private 책임 |
| --- | --- | --- |
| resolve / 121 | resolveLine / 103 | resolveSteps 25: 입력 방식별 단계 행 준비 (묶음 3 준비에서 내부 행 map 복원, manualStepRow 제거) |
| submit / 40 | submitDocument / 31 | notifySubmission 18: 제출 알림 블록 |
| act / 46 | decideDocument / 33 | completeDecision 22: 다음 단계/최종 승인 블록 |
| steps / 37 | updateFormSteps / 21 | resolveStepValues 23: 양식 단계의 역할/사원 값 확인 |
| InboxRepository.search / 46 | search / 17 | InboxService.inboxItem 11, progress 22: 응답 계산 이동 |
| adjust / 17 | createAdjustment / 13 | validateAdjustment 7: 기존 검증 조건 하나를 그대로 추출 |

줄 수는 javac MethodTree의 시작부터 끝까지의 물리 줄이며, 선언·메서드 어노테이션·본문 안 주석을 포함한다. resolveLine의 선택·fallback·skip·실패 처리는 순서와 제어 경계가 연결돼 있어 한 흐름으로 남겼다. 길이 숫자만 줄이려고 순서를 재배치하지 않았다.

## 남긴 Map·내부 구조

- ApprovalLineResolver.getForm/getPerson: 권한·존재 검사와 결재선 SQL 값으로 사용하는 행.
- Resolved.person: 결재선 스냅샷 저장의 id/name/title/department 값 원천. response DTO로 대체하지 않는다.
- FormService.StepValues(Object form,...): SQL 값용 private record.
- 수동 결재선 step 행: 응답이 아닌 내부 행이므로 묶음 3 준비에서 원래 SqlStore.map(...)으로 복원했다. manualStepRow/LinkedHashMap 전개는 제거했고, approval 비 repository map 예외 1곳으로 센다.
- LeaveFormHandler.raw: 처리기 내부 행. available/days/raw 공개 이름을 보존했다.
- LeaveGrantService.run/runToday: 배치 행 Map 유지. 변환 실패가 FAILED 기록 경로를 새로 타지 않도록 서비스 밖에서 변환한다.
- InboxRepository.search: 테스트가 부르는 Map 반환과 메서드·매개변수 타입/순서를 보존. total/content/lines만 반환하고 목록이 비면 결재선 조회를 건너뛴다.
- AttendanceFixFormHandler: 이번에는 body/json만 수정했다. 나머지 이름·분할은 묶음 3에서 하므로 파일을 두 번 수정하는 예외다.
- HomeService: leave.getBalance(null) 호출 한 줄만 변경했다.

## 측정 전후

| 도메인 | ApiResponse<?> | public Map/Object(본문 별도) | 비 repository map/Map.of | 한 글자 이름 |
| --- | --- | --- | --- | --- |
| approval | 19 → 0 | 17 → 2 | 19 → 1 (내부 행 예외) | 69 → 0 |
| leave | 9 → 0 | 12 → 3 | 7 → 0 | 23 → 0 |
| overtime | 0 → 0 | 0 → 0 | 0 → 0 | 7 → 0 |

grep 제시값 30/10과 달리 응답 factory는 attribution으로 SqlStore.map/Map.of 호출만 세어 시작 19/7이다. Stream.map과 이름이 같은 다른 호출을 포함하지 않는다. 처리기 body 네 개와 인터페이스는 별도로 record/marker 반환으로 전환했다. overtime의 다른 public Object 반환은 시작 AST에서 없었다.

### 시작 상위 20

| 메서드 | 메서드 줄 |
| --- | --- |
| approval.service.ApprovalLineResolver#resolve(java.util.Map<java.lang.String,java.lang.Object>,long,long,java.util.List<approval.dto.DocumentRequest.ManualStep>) | 121 |
| approval.repository.InboxRepository#search(long,java.lang.String,java.lang.String,java.lang.String,int,int) | 46 |
| approval.service.DocumentService#act(long,boolean,java.lang.String) | 46 |
| approval.service.DocumentService#detail(long) | 43 |
| approval.service.DocumentService#submit(long) | 40 |
| leave.service.LeaveGrantWork#run(java.time.LocalDate) | 40 |
| approval.service.FormService#steps(java.lang.String,java.util.List<approval.service.FormService.Step>) | 37 |
| leave.service.LeaveQueryService#preview(com.fasterxml.jackson.databind.JsonNode) | 29 |
| leave.service.LeaveAdminService#balances(java.lang.Integer,java.lang.Long,java.lang.String,int,int) | 28 |
| approval.service.ApprovalLineResolver#preview(java.lang.String,long,java.util.List<approval.dto.DocumentRequest.ManualStep>) | 25 |
| leave.service.LeaveGrantRules#due(long,java.time.LocalDate,java.time.LocalDate) | 25 |
| approval.service.DocumentService#resubmit(long) | 22 |
| approval.service.DocumentService#storeLines(long,java.util.List<approval.service.ApprovalLineResolver.Resolved>,boolean) | 22 |
| leave.repository.LeaveQueries#findBalances(int,int,long,com.minierp.backend.global.permission.ScopeFilter,java.lang.Long,java.lang.String) | 21 |
| approval.service.FormService#detail(java.lang.String) | 20 |
| approval.repository.DocumentStore#insertLine(long,int,java.lang.String,java.lang.Object,java.lang.Object,java.lang.Object,java.lang.Object,java.lang.String,java.lang.String,java.lang.String,java.lang.String) | 19 |
| approval.service.DocumentService#update(long,approval.dto.DocumentRequest) | 19 |
| approval.service.DocumentService#withdraw(long) | 19 |
| leave.handler.LeaveFormHandler#days(java.lang.String,java.time.LocalDate,java.time.LocalDate) | 19 |
| leave.service.LeaveQueryService#ledger(long,java.lang.Integer,java.lang.String) | 19 |

### 완료 상위 20

| 메서드 | 메서드 줄 |
| --- | --- |
| approval.service.ApprovalLineResolver#resolveLine(java.util.Map<java.lang.String,java.lang.Object>,long,long,java.util.List<approval.dto.DocumentRequest.ManualStep>) | 103 |
| leave.service.LeaveGrantWork#run(java.time.LocalDate) | 40 |
| approval.service.DocumentService#getDocument(long) | 34 |
| approval.service.DocumentService#decideDocument(long,boolean,java.lang.String) | 33 |
| approval.service.DocumentService#submitDocument(long) | 31 |
| leave.service.LeaveQueryService#getDaysPreview(com.fasterxml.jackson.databind.JsonNode) | 28 |
| leave.service.LeaveAdminService#getBalances(java.lang.Integer,java.lang.Long,java.lang.String,int,int) | 27 |
| leave.service.LeaveGrantRules#due(long,java.time.LocalDate,java.time.LocalDate) | 25 |
| approval.service.ApprovalLineResolver#getLinePreview(java.lang.String,long,java.util.List<approval.dto.DocumentRequest.ManualStep>) | 23 |
| approval.service.ApprovalLineResolver#resolveSteps(java.util.Map<java.lang.String,java.lang.Object>,java.util.List<approval.dto.DocumentRequest.ManualStep>) | 23 |
| approval.service.DocumentService#storeLines(long,java.util.List<approval.service.ApprovalLineResolver.Resolved>,boolean) | 23 |
| approval.service.FormService#resolveStepValues(java.util.Map<java.lang.String,java.lang.Object>,java.util.List<approval.service.FormService.Step>) | 23 |
| approval.service.DocumentService#completeDecision(java.util.Map<java.lang.String,java.lang.Object>,java.util.Optional<java.util.Map<java.lang.String,java.lang.Object>>,approval.handler.ApprovalFormHandler,boolean,long) | 22 |
| approval.service.DocumentService#resubmitDocument(long) | 22 |
| approval.service.InboxService#progress(java.util.Map<java.lang.String,java.lang.Object>,java.util.List<java.util.Map<java.lang.String,java.lang.Object>>) | 22 |
| approval.service.DocumentService#updateDocument(long,approval.dto.DocumentRequest) | 21 |
| approval.service.FormService#updateFormSteps(java.lang.String,java.util.List<approval.service.FormService.Step>) | 21 |
| leave.repository.LeaveQueries#findBalances(int,int,long,com.minierp.backend.global.permission.ScopeFilter,java.lang.Long,java.lang.String) | 21 |
| approval.service.DocumentService#withdrawDocument(long) | 20 |
| approval.repository.DocumentStore#insertLine(long,int,java.lang.String,java.lang.Object,java.lang.Object,java.lang.Object,java.lang.Object,java.lang.String,java.lang.String,java.lang.String,java.lang.String) | 19 |

### 남은 두 글자 이름

| 도메인 | 이름 | 위치 | 메서드 |
| --- | --- | --- | --- |
| approval | id | approval/controller/DocumentController.java:52 | updateDocument |
| approval | id | approval/controller/DocumentController.java:58 | deleteDocument |
| approval | id | approval/controller/DocumentController.java:64 | getDocument |
| approval | id | approval/controller/DocumentController.java:69 | submitDocument |
| approval | id | approval/controller/DocumentController.java:75 | approveDocument |
| approval | id | approval/controller/DocumentController.java:82 | rejectDocument |
| approval | id | approval/controller/DocumentController.java:88 | withdrawDocument |
| approval | id | approval/controller/DocumentController.java:94 | resubmitDocument |
| approval | id | approval/general/GeneralFormHandler.java:29 | body |
| approval | id | approval/general/GeneralFormHandler.java:37 | deleteBody |
| approval | id | approval/repository/ApprovalLineQueries.java:19 | findPerson |
| approval | id | approval/repository/ApprovalLineQueries.java:26 | findSteps |
| approval | id | approval/repository/ApprovalLineQueries.java:36 | findDepartmentName |
| approval | id | approval/repository/ApprovalLineQueries.java:40 | findDepartmentHead |
| approval | id | approval/repository/ApprovalLineQueries.java:45 | findParent |
| approval | id | approval/repository/DocumentQueries.java:15 | findDocument |
| approval | id | approval/repository/DocumentQueries.java:21 | countReadableDocument |
| approval | id | approval/repository/DocumentQueries.java:29 | findLines |
| approval | id | approval/repository/DocumentQueries.java:33 | countApprover |
| approval | id | approval/repository/DocumentQueries.java:39 | findHistory |
| approval | id | approval/repository/DocumentStore.java:16 | lockUser |
| approval | id | approval/repository/DocumentStore.java:20 | lockDocument |
| approval | id | approval/repository/DocumentStore.java:26 | insertLine |
| approval | id | approval/repository/DocumentStore.java:61 | deleteLines |
| approval | id | approval/repository/DocumentStore.java:65 | updateDraft |
| approval | id | approval/repository/DocumentStore.java:71 | deleteDocument |
| approval | id | approval/repository/DocumentStore.java:91 | insertHistory |
| approval | id | approval/repository/DocumentStore.java:98 | updateTransition |
| approval | id | approval/repository/DocumentStore.java:109 | updateSubmission |
| approval | no | approval/repository/DocumentStore.java:109 | updateSubmission |
| approval | id | approval/repository/DocumentStore.java:114 | updateAction |
| approval | id | approval/repository/DocumentStore.java:120 | updatePendingLine |
| approval | id | approval/repository/FormQueries.java:23 | findSteps |
| approval | id | approval/repository/FormQueries.java:33 | findUser |
| approval | id | approval/repository/FormStore.java:15 | updateOptions |
| approval | id | approval/repository/FormStore.java:21 | lockForm |
| approval | id | approval/repository/FormStore.java:25 | deleteSteps |
| approval | id | approval/repository/GeneralRequestStore.java:15 | upsertBody |
| approval | id | approval/repository/GeneralRequestStore.java:21 | findBody |
| approval | id | approval/repository/GeneralRequestStore.java:32 | deleteBody |
| approval | id | approval/service/ApprovalLineResolver.java:21 | getPerson |
| approval | id | approval/service/DocumentService.java:40 | raw |
| approval | id | approval/service/DocumentService.java:62 | canRead |
| approval | id | approval/service/DocumentService.java:66 | lockUser |
| approval | id | approval/service/DocumentService.java:70 | lockDocument |
| approval | id | approval/service/DocumentService.java:75 | lines |
| approval | id | approval/service/DocumentService.java:79 | manual |
| approval | id | approval/service/DocumentService.java:86 | storeLines |
| approval | id | approval/service/DocumentService.java:137 | createDocument |
| approval | id | approval/service/DocumentService.java:146 | updateDocument |
| approval | id | approval/service/DocumentService.java:168 | deleteDocument |
| approval | id | approval/service/DocumentService.java:186 | history |
| approval | id | approval/service/DocumentService.java:198 | submitDocument |
| approval | id | approval/service/DocumentService.java:230 | notifySubmission |
| approval | id | approval/service/DocumentService.java:249 | decideDocument |
| approval | id | approval/service/DocumentService.java:287 | completeDecision |
| approval | id | approval/service/DocumentService.java:306 | withdrawDocument |
| approval | id | approval/service/DocumentService.java:327 | resubmitDocument |
| approval | id | approval/service/DocumentService.java:349 | getDocument |
| leave | id | leave/handler/LeaveFormHandler.java:49 | raw |
| leave | id | leave/handler/LeaveFormHandler.java:53 | body |
| leave | id | leave/handler/LeaveFormHandler.java:66 | deleteBody |
| leave | id | leave/handler/LeaveFormHandler.java:135 | leaveMemo |
| leave | id | leave/repository/LeaveQueries.java:37 | countLegacyGrants |
| leave | id | leave/repository/LeaveQueries.java:43 | findGrantKeys |
| leave | id | leave/repository/LeaveQueries.java:47 | findHandoverUser |
| leave | id | leave/repository/LeaveQueries.java:51 | findRequest |
| leave | id | leave/repository/LeaveQueries.java:56 | findHandoverName |
| leave | id | leave/repository/LeaveQueries.java:66 | countOverlap |
| leave | id | leave/repository/LeaveQueries.java:74 | findReservations |
| leave | id | leave/repository/LeaveQueries.java:108 | findAdjustmentBalance |
| leave | id | leave/repository/LeaveStore.java:17 | lockUser |
| leave | id | leave/repository/LeaveStore.java:27 | lockGrantUser |
| leave | id | leave/repository/LeaveStore.java:32 | insertGrant |
| leave | id | leave/repository/LeaveStore.java:39 | upsertBody |
| leave | id | leave/repository/LeaveStore.java:60 | deleteBody |
| leave | id | leave/repository/LeaveStore.java:69 | insertLedger |
| leave | id | leave/repository/LeaveStore.java:77 | updateUsedDays |
| leave | id | leave/service/LeaveAdminService.java:81 | createAdjustment |
| leave | id | leave/service/LeaveGrantWork.java:28 | run |
| overtime | id | overtime/handler/OvertimeFormHandler.java:35 | body |
| overtime | id | overtime/handler/OvertimeFormHandler.java:43 | deleteBody |
| overtime | id | overtime/repository/OvertimeStore.java:16 | upsertBody |
| overtime | id | overtime/repository/OvertimeStore.java:24 | findBody |
| overtime | id | overtime/repository/OvertimeStore.java:36 | deleteBody |
| overtime | id | overtime/repository/OvertimeStore.java:40 | countValidTime |

남은 한 글자 이름은 approval·leave·overtime 모두 0개다. `83c80d3`에서 보류했던 InboxRepository의 q 3곳은 사용자 확인 뒤 `24ac286`에서 query로 바꿨다. 최종 이름 측정은 group2-query-rename-names.json이며, group2-leave-names.json은 보완 전 기록으로 남긴다.

## 재현 명령

JAVA_HOME은 Java 17을 사용한다. BASE/CANDIDATE는 작업 트리 밖 git archive 사본이고, 먼저 각 backend에서 test-compile한다. CP는 기존 기록의 classpath.txt이다. 기록 파일은 커밋하지 않는다.

```sh
./mvnw -q -Duser.timezone=UTC test-compile
./mvnw -Duser.timezone=UTC -Drefactoring.record=/private/tmp/group2-recording test
java -cp "$(cat /private/tmp/group2-recording/classpath.txt)" \
  com.minierp.backend.support.refactoring.SnapshotComparator \
  src/test/resources/refactoring-baseline /private/tmp/group2-recording
./mvnw -Duser.timezone=UTC test
python3 docs/refactoring/R-04-sql-literal-check.py \
  --before "$BASE" --after "$CANDIDATE" --classpath "$CP" --output /private/tmp/sql.json
python3 docs/refactoring/R-04-response-check.py \
  --before "$BASE" --after "$CANDIDATE" --classpath "$CP" \
  --domain approval --domain leave --domain overtime \
  --mapping docs/refactoring/R-04-evidence/group2-response-map.json --output /private/tmp/response.json
python3 docs/refactoring/R-04-names.py --root "$CANDIDATE" --classpath "$CP" --output /private/tmp/names.json
python3 docs/refactoring/R-02-format-measure.py --root "$CANDIDATE" --output /private/tmp/format.json
```

이름 대응표는 group2-renames.json, 필드별 원천 대응은 group2-response-map.json이다. R-04-group2-verify-tools.py는 이미 컴파일한 시작 사본과 후보를 받아 외부 사본에서 변조 검증을 재현한다. `--help`에 필수 경로가 있다.

## 추가 확인 — 결재함 검색 매개변수 이름

사용자는 “매개변수를 그대로”의 범위가 Java 이름이 아니라 테스트가 부르는 타입·순서·개수라고 확정했다. `selectionSql`, `pageSql`, `search`의 String 매개변수 이름 3개와 참조 7개만 q→query로 바꿨다. 메서드 이름·반환 타입·매개변수 시그니처는 javac 대조에서 동일하다. `search`의 Map 반환도 유지했다.

SQL의 `:q`, `ESCAPE '!'`, 인자 map의 문자열 키 `"q"`, 와일드카드 이스케이프 식은 그대로다. `counts`의 기존 지역변수 query를 포함한 메서드 전체는 바이트 비교로 같다. 토큰 비교에서는 이 10개 식별자만 달랐고 모든 문자열 리터럴은 원문 그대로였다.

| 검증 | 결과 |
| --- | --- |
| 원본 83c80d3 / 외부 후보 test-compile | exit 0 / exit 0 |
| 최종 문서 포함 사본 test-compile (UTC) | exit 0 · 10.47초 |
| 작업 트리 test-compile (UTC) | exit 0 · 8.81초 |
| 83c80d3 대비 SQL·문자열 | differences=0 |
| 묶음 2 기준 c531e1f 대비 SQL·문자열 | differences=0, 기존 결재함 51곳 분류 유지 |
| 실행 순서·응답 구조 | 각각 differences=0 |
| 트랜잭션·SQL 위치·미사용 import | 83개 동일 / repository 밖 0 / 미사용 0 |
| 한 글자 이름 | approval 3→0, leave 0→0, overtime 0→0 |
| 기록 ON 전체 UTC | 231/0/0/0 · 463.83초 · differences=0 |
| 기록 OFF 전체 UTC | 231/0/0/0 · 458.55초 · 기록 출력 0 |
| 별도 응답 probe | 2/0/0/0 · differences=0 |

순서 경고 10개는 모두 기존 14개 목록 안이다. 실제 프레임 목록은 group2-query-rename-on.json에 있다. ON/OFF는 변경 커밋 `24ac2868df90a5e9226679564e699499a55c0686`의 작업 트리에서 순차 실행했다. 중단 당시 백그라운드 검증이 계속 진행해 완료되었으며, 재개 뒤 로그·종료 상태·소스 해시를 확인했다. 이전 코드의 검증 결과를 이번 보완의 증거로 대신 쓰지 않았다.

처음 정적 대조 실행은 비교 원본 사본에 컴파일 출력이 없어 도구의 사전 조건에서 멈췄다. 저장소 적용 전 원본 사본을 컴파일해 해소했다. 이후 정적 검사와 probe를 통과한 후보만 저장소에 적용했다.

최종 보고서·증거 추가는 docs/refactoring 안으로 한정한다. 기록 기준선·기록기·비교 규칙·테스트 소스·SQL·설정은 변경하지 않았다. 동일 소스 해시는 group2-query-rename-checks.json의 sourceHashes로 확인할 수 있다. 증거 파일은 차이·판정·출현 분류·해시를 남기고 변경 없는 전체 호출 추적은 중복 저장하지 않았다. 이름 증거의 범위는 이번 세 도메인이다.

## 묶음 3 준비에 반영한 리뷰 보완

resolveSteps의 규칙/수동 단계 공통 람다 이름을 step으로, 휴가 예약 원장 행 이름을 reservation으로 고쳤다. InboxService 항목 id는 number(document, "id")로 통일했다. 수동 단계 행은 명세 11차에 따라 map(...)으로 복원했다. 이번 보완의 커밋과 검증은 R-04-group3-attendance-home-project.md에 이어서 기록한다.

## 발견한 버그·확인하지 못한 것

- 기존 버그: `GET /api/v1/approval-inbox?box=mine&page=x`는 500이다. GlobalExceptionHandler가 MethodArgumentTypeMismatchException을 400으로 변환하지 않는다. 사용자 독립 리뷰에서 시작 커밋 c531e1f에서도 동일함을 확인했다. 이번에는 고치지 않는다.

새 기능이나 버그 수정은 하지 않았다. 실제 운영 서버·개발 DB/Redis는 호출하지 않았으며 Testcontainers만 사용했다. 임의의 모든 데이터에 대한 형식적 동등성 증명은 아니다. 정적 대조, 엄격한 기존 기록, 실패/동시성 전체 테스트, 기준선의 빈 경로를 보완한 probe가 검증 범위다. 네트워크 시간이나 운영 성능 변화는 측정하지 않았다.
