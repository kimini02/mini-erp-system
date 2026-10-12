/** 서버 에러 응답 { success: false, error: { code, message } } 에서 표시할 메시지를 꺼냅니다. */
export function getErrorMessage(error, fallback = '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.') {
  const body = error?.response?.data;
  return body?.error?.message || body?.message || fallback;
}

export function getErrorCode(error) {
  return error?.response?.data?.error?.code || null;
}
