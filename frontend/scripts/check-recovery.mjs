import assert from 'node:assert/strict';
import { installRecovery } from '../src/api/mock/recovery.js';
import { isValidPassword } from '../src/lib/password.js';

const routes = new Map();
const user = { name: 'Fixture', email: 'fixture@example.test', loginId: 'fixture', password: 'Before123!', tokenVersion: 0 };
const fail = (status, code, message) => { throw Object.assign(new Error(message), { status, code }); };
installRecovery({
  route: (method, path, handler, options) => {
    assert.equal(method, 'POST');
    assert.equal(options.auth, false);
    routes.set(path, handler);
  },
  db: { users: [user] },
  fail,
  validatePassword: (password) => {
    if (!isValidPassword(password)) fail(400, 'PASSWORD_POLICY_VIOLATION', 'Invalid fixture password');
  },
});
const call = (path, body) => routes.get(`/auth/${path}`)({ body });
const request = (email = user.email) => call('password/reset/request', { email });
const verify = (verificationCode = '123456') => call('password/reset/verify', { email: user.email, verificationCode });
const confirm = (resetProof, password = 'After123!') => call('password/reset/confirm', {
  resetProof, newPassword: password, newPasswordConfirm: password,
});
const rejects = (fn, code) => assert.throws(fn, (error) => error.code === code);
let now = Date.now();
const originalNow = Date.now;
Date.now = () => now;
try {
  assert.deepEqual(call('find-id/request', { name: user.name, email: user.email }),
    call('find-id/request', { name: 'Absent', email: 'absent@example.test' }));
  assert.deepEqual(request(), request('absent@example.test'));
  rejects(() => verify('000000'), 'OTP_INVALID');
  request(); // A resend must call the API and replace the stored code/expiry.
  const first = verify().resetProof;
  rejects(() => verify(), 'OTP_EXPIRED');
  rejects(() => confirm(first, 'short'), 'PASSWORD_POLICY_VIOLATION');
  rejects(() => confirm(first, 'Before123!'), 'PASSWORD_POLICY_VIOLATION');
  confirm(first);
  assert.equal(user.password, 'After123!');
  assert.equal(user.tokenVersion, 1);
  rejects(() => confirm(first, 'Another123!'), 'RESET_PROOF_INVALID');
  request();
  now += 5 * 60000;
  rejects(() => verify(), 'OTP_EXPIRED');
  request();
  const expired = verify().resetProof;
  now += 15 * 60000;
  rejects(() => confirm(expired, 'Another123!'), 'RESET_PROOF_INVALID');
  for (let index = 0; index < 5; index += 1) request();
  rejects(() => request(), 'TOO_MANY_REQUESTS');
  console.log('RECOVERY_MOCK public_routes=4 neutral_responses=PASS invalid_code=PASS resend=PASS');
  console.log('RECOVERY_MOCK policy=PASS proof_single_use=PASS code_expiry=PASS proof_expiry=PASS rate_limit=PASS');
} finally {
  Date.now = originalNow;
}
