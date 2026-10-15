# M1 리뷰 5 — 미등록 부서 코드 이관

## 증상
V2의 CASE에 ELSE가 없어 기존 department_code=04 행이 department_id NULL로 남아 NOT NULL 변경 단계에서 오류 1265가 발생했다.

## 재현 방법
해당 회귀 테스트를 Java 17 + Docker에서 구현 변경 전 실행하고, 변경 후 같은 테스트를 다시 실행한다. 아래 결과에 테스트 이름과 전후 결과를 기록한다.

## 원인
V2의 CASE에 ELSE가 없어 기존 department_code=04 행이 department_id NULL로 남아 NOT NULL 변경 단계에서 오류 1265가 발생했다.

## 검토했지만 버린 방법과 이유
화면에서만 제한하거나 테스트 기대값을 낮추는 방법은 서버의 계약을 보호하지 못하므로 사용하지 않았다. 새 라이브러리 없이 기존 구조를 유지한다.

## 선택한 방법
배포 전 V2 수정 허용에 따라 CASE ELSE 1을 추가하고, 기존 코드가 01/02/03에 속하지 않는 건수를 M1: migrated_unknown_department= 로그로 출력한다. V1은 그대로 유지한다.

## 부작용·남은 위험
V2 파일 변경으로 Flyway 체크섬이 바뀐다. 기존 V2 적용 DB는 validate 불일치가 발생하므로 운영 전 이력을 확인해야 한다. 로컬 MariaDB 3306은 실행 중이지만 kimmini/root 기본 연결 모두 인증 거절되었고 저장소에 실제 로컬 연결 설정이 없어 적용 여부는 미확인이다. DB 재생성·repair·이력 변경은 하지 않았다.

## 결과
- M1DataMigrationTest.migratesPopulatedM0DatabaseWithoutLosingLegacyValues에 04 부서 행을 추가했다.
- 수정 전 1개 오류: department_id row 4, SQL 오류 1265. 수정 후 1개 성공, 실패·오류·건너뜀 0.
- 01/02/03/04 → 2/3/4/1, 기존 아이디·비밀번호·직급·연차 보존, RETIRED와 NULL 입사일 보존, migrated_unknown_department=1을 실제 MariaDB로 검증했다.
