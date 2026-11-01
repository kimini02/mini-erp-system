import { Check } from 'lucide-react';
import Logo from './Logo';

const FEATURES = [
  '연차·특근 신청과 결재를 한 화면에서',
  '프로젝트와 업무 진행 상황 공유',
  '출퇴근 기록과 팀 일정 캘린더',
];

/**
 * 로그인 전 화면 공통 레이아웃
 * 좌측 소개 패널(넓은 화면) + 우측 폼
 */
export default function AuthLayout({ title, description, children, footer }) {
  return (
    <div className="flex min-h-screen bg-white">
      <aside className="relative hidden w-[44%] max-w-[560px] flex-col justify-between bg-blue-600 p-12 text-white lg:flex">
        <Logo inverse />
        <div>
          <h2 className="text-[28px] font-semibold leading-snug tracking-tight">
            우리 팀의 업무와 근태를
            <br />
            하나의 공간에서.
          </h2>
          <ul className="mt-8 space-y-3">
            {FEATURES.map((text) => (
              <li key={text} className="flex items-center gap-3 text-[15px] text-blue-50">
                <span className="flex h-5 w-5 items-center justify-center rounded-full bg-white/15">
                  <Check size={12} strokeWidth={3} />
                </span>
                {text}
              </li>
            ))}
          </ul>
        </div>
        <p className="text-[13px] text-blue-200">© 2026 WorkFlow. 사내 업무 관리 그룹웨어</p>
      </aside>

      <main className="flex flex-1 items-center justify-center px-6 py-12">
        <div className="w-full max-w-[400px]">
          <Logo className="mb-10 lg:hidden" />
          {title && (
            <div className="mb-8">
              <h1 className="text-2xl font-semibold tracking-tight text-gray-900">{title}</h1>
              {description && <p className="mt-2 text-sm text-gray-500">{description}</p>}
            </div>
          )}
          {children}
          {footer && (
            <div className="mt-8 border-t border-gray-100 pt-6 text-center text-[13px] text-gray-500">{footer}</div>
          )}
        </div>
      </main>
    </div>
  );
}

/** 하단 링크 묶음: 아이디 찾기 · 비밀번호 찾기 등 */
export function AuthLinks({ links }) {
  return (
    <div className="flex items-center justify-center gap-3">
      {links.map((link, i) => (
        <span key={link.label} className="flex items-center gap-3">
          {i > 0 && <span className="h-3 w-px bg-gray-200" />}
          <button type="button" onClick={link.onClick} className="text-gray-500 hover:text-gray-900">
            {link.label}
          </button>
        </span>
      ))}
    </div>
  );
}
