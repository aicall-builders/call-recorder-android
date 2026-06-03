'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { startSocialLogin } from '@/lib/socialOAuth';
import Logo from '../components/Logo';

const SOCIAL_LOGIN_PROVIDERS = [
  { id: 'kakao', label: '카카오로 시작하기', bg: '#FEE500', color: 'rgba(0,0,0,0.85)' },
  { id: 'google', label: 'Google로 시작하기', bg: '#FFFFFF', color: '#1f2937', border: true },
  { id: 'naver', label: '네이버로 시작하기', bg: '#03C75A', color: '#FFFFFF' },
];

export default function LoginPage() {
  const [error, setError] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    setError(params.get('error') || '');
  }, []);

  const handleLogin = (provider) => {
    try {
      startSocialLogin(provider);
    } catch (err) {
      alert(err.message || '로그인을 시작할 수 없습니다.');
    }
  };

  return (
    <main className="min-h-screen flex flex-col px-6 pt-6 pb-8 lg:px-8 bg-surface-page">
      <div className="flex items-center">
        <Link href="/" className="inline-flex items-center gap-1.5 text-sm font-medium text-ink-secondary hover:text-ink-primary px-3 py-2 rounded-[10px] hover:bg-surface-card transition-all">
          ← 홈으로
        </Link>
      </div>

      <div className="flex-1 flex items-center justify-center py-10">
        <div className="w-full max-w-[420px] bg-surface-card rounded-[24px] px-8 py-10 border border-line/60 shadow-card">
          <div className="mx-auto mb-6 w-[88px] h-[88px] flex items-center justify-center bg-surface-page rounded-[22px] border border-line/60">
            <Logo size={52} />
          </div>
          <div className="text-center">
            <h1 className="text-[22px] font-bold text-ink-primary tracking-tight mb-2">로그인하고 시작하기</h1>
            <p className="text-[14px] text-ink-secondary leading-relaxed">사용 중인 계정으로 로그인하세요.<br />캘린더 연동은 로그인 후 별도로 연결합니다.</p>
          </div>

          {error && (
            <div className="mt-4 px-3.5 py-3 bg-red-50 border border-red-200 rounded-[10px] text-[13px] text-red-800 break-all">
              로그인 실패: {error}
            </div>
          )}

          <div className="mt-8 flex flex-col gap-3">
            {SOCIAL_LOGIN_PROVIDERS.map((p) => (
              <button
                key={p.id}
                onClick={() => handleLogin(p.id)}
                className={`w-full py-4 px-5 rounded-[14px] font-semibold text-[15px] inline-flex items-center justify-center gap-2 transition-all hover:translate-y-[-1px] ${p.border ? 'border border-line' : ''}`}
                style={{ background: p.bg, color: p.color }}
              >
                {p.label}
              </button>
            ))}
          </div>

          <p className="mt-5 text-center text-[12px] text-ink-tertiary leading-[1.5]">
            로그인 시 이용약관 및 개인정보 처리방침에 동의하는 것으로 간주됩니다.
          </p>
        </div>
      </div>
    </main>
  );
}
