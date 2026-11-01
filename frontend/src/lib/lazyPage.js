import { lazy } from 'react';

const RELOAD_KEY = 'workflow.chunk-reload-at';

const lastReload = () => {
  try {
    return Number(sessionStorage.getItem(RELOAD_KEY) || 0);
  } catch {
    return 0;
  }
};

/**
 * 화면 코드를 처음 열 때 받습니다(React.lazy).
 * 배포로 예전 조각 파일이 사라진 뒤(Nginx 404) 열려 있던 탭이 그 파일을 찾으면, 새 index.html을 받도록 한 번만 새로고침합니다.
 * 10초 안에 다시 실패하면 새로고침을 반복하지 않고 오류를 PageErrorBoundary로 올립니다.
 */
export function lazyPage(load) {
  return lazy(() =>
    load().catch((error) => {
      if (Date.now() - lastReload() < 10_000) throw error;
      try {
        sessionStorage.setItem(RELOAD_KEY, String(Date.now()));
      } catch {
        throw error;
      }
      window.location.reload();
      return new Promise(() => {});
    }),
  );
}
