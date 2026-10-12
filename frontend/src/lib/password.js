// 서버 비밀번호 정책과 같은 규칙 (AuthService.validatePasswordPolicy, 8~20자)
export const PASSWORD_RULES = [
  { id: 'length', label: '8~20자', test: (v) => v.length >= 8 && v.length <= 20 },
  { id: 'upper', label: '영문 대문자', test: (v) => /[A-Z]/.test(v) },
  { id: 'lower', label: '영문 소문자', test: (v) => /[a-z]/.test(v) },
  { id: 'digit', label: '숫자', test: (v) => /\d/.test(v) },
  { id: 'special', label: '특수문자', test: (v) => /[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(v) },
];

export const isValidPassword = (value) => PASSWORD_RULES.every((rule) => rule.test(value));
