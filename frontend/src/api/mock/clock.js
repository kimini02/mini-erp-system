// 목 모드 전용 고정 시계: localStorage의 __MOCK_NOW__(ISO 시각)가 있으면 그 시각으로 멈춥니다.
// 리팩토링 전후 화면 비교를 날짜와 상관없이 같은 목 데이터로 하기 위한 장치입니다(배포 빌드에서는 빠짐).
// 다른 모듈보다 먼저 실행되어야 해서 main.jsx의 첫 import입니다.
if (import.meta.env.MODE === 'mock') {
  const fixed = localStorage.getItem('__MOCK_NOW__');
  if (fixed) {
    const RealDate = Date;
    const fixedMs = new RealDate(fixed).getTime();
    class FixedDate extends RealDate {
      constructor(...args) {
        if (args.length === 0) super(fixedMs);
        else super(...args);
      }
    }
    // 클래스 정적 메서드 대신 대입: Tailwind가 소스의 단어를 클래스로 읽어 배포 CSS가 바뀌지 않게 합니다.
    FixedDate.now = () => fixedMs;
    globalThis.Date = FixedDate;
  }
}
