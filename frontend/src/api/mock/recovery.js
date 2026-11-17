/** Public recovery API fixtures. Loaded only by the mock adapter; never sends mail. */
export function installRecovery({ route, db, fail, validatePassword }) {
  const codes = new Map();
  const proofs = new Map();
  const requests = new Map();
  const byEmail = (email) => db.users.find((user) => user.email === email);
  const publicRoute = (path, handler) => route('POST', path, handler, { auth: false });

  publicRoute('/auth/find-id/request', () => ({
    message: '입력하신 정보와 일치하는 계정이 있으면 이메일로 아이디를 보냈습니다.',
  }));

  publicRoute('/auth/password/reset/request', ({ body }) => {
    const email = body.email;
    const now = Date.now();
    const request = requests.get(email);
    const rate = request && request.expires > now ? request : { count: 0, expires: now + 60000 };
    rate.count += 1;
    requests.set(email, rate);
    if (rate.count > 5) fail(429, 'TOO_MANY_REQUESTS', '너무 많은 요청입니다. 잠시 후 다시 시도해주세요.');
    if (byEmail(email)) codes.set(email, { code: '123456', expires: now + 5 * 60000 });
    return { message: '인증번호가 발송되었습니다' };
  });

  publicRoute('/auth/password/reset/verify', ({ body }) => {
    if (!byEmail(body.email)) fail(400, 'INVALID_RESET_REQUEST', '유효하지 않은 비밀번호 재설정 요청입니다.');
    const code = codes.get(body.email);
    if (!code || code.expires <= Date.now()) fail(401, 'OTP_EXPIRED', '인증번호가 만료되었습니다.');
    if (body.verificationCode !== code.code) fail(401, 'OTP_INVALID', '유효하지 않은 인증번호입니다.');
    codes.delete(body.email);
    const resetProof = crypto.randomUUID();
    proofs.set(resetProof, { email: body.email, expires: Date.now() + 15 * 60000 });
    return { resetProof, message: '인증번호가 확인되었습니다' };
  });

  publicRoute('/auth/password/reset/confirm', ({ body }) => {
    if (body.newPassword !== body.newPasswordConfirm) {
      fail(400, 'PASSWORD_POLICY_VIOLATION', '비밀번호 확인이 일치하지 않습니다.');
    }
    validatePassword(body.newPassword);
    const proof = proofs.get(body.resetProof);
    if (!proof || proof.expires <= Date.now()) fail(401, 'RESET_PROOF_INVALID', '유효하지 않은 재설정 인증입니다.');
    const user = byEmail(proof.email);
    if (user.password === body.newPassword) {
      fail(400, 'PASSWORD_POLICY_VIOLATION', '이전과 다른 비밀번호를 입력해주세요.');
    }
    user.password = body.newPassword;
    user.mustChangePw = false;
    user.tokenVersion += 1;
    proofs.delete(body.resetProof);
    return { message: '비밀번호가 변경되었습니다. 다시 로그인해주세요' };
  });
}
