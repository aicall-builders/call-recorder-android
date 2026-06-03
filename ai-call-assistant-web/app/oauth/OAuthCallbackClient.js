'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { authApi, calendarApi } from '@/lib/api';
import { loginWithFirebaseCustomToken } from '@/lib/firebase';
import { consumeOAuthState, providerLabel } from '@/lib/socialOAuth';

export default function OAuthCallbackClient({ provider }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const handledRef = useRef(false);
  const [message, setMessage] = useState(`${providerLabel(provider)} 처리 중...`);

  useEffect(() => {
    if (handledRef.current) return;
    handledRef.current = true;

    const code = searchParams.get('code');
    const state = searchParams.get('state') || '';
    const error = searchParams.get('error');

    if (error) {
      router.replace(`/login?error=${encodeURIComponent(error)}`);
      return;
    }
    if (!code) {
      router.replace('/login?error=missing_code');
      return;
    }

    const kind = state.startsWith('calendar:') ? 'calendar' : 'login';
    consumeOAuthState(state);

    async function run() {
      try {
        const redirectUri = `${window.location.origin}/oauth/${provider}`;
        if (kind === 'calendar') {
          setMessage(`${providerLabel(provider)} 캘린더 연결 중...`);
          await calendarApi.completeOAuth({
            provider,
            authorizationCode: code,
            redirectUri,
            state,
          });
          router.replace('/dashboard?calendar=connected');
          return;
        }

        setMessage(`${providerLabel(provider)} 로그인 중...`);
        const res = await authApi.socialLogin({
          provider,
          authorizationCode: code,
          redirectUri,
          state,
        });
        const customToken = res.data?.firebase_custom_token || res.data?.custom_token;
        if (!customToken) throw new Error('Firebase custom token이 응답에 없습니다.');
        await loginWithFirebaseCustomToken(customToken);
        const user = res.data?.user;
        if (user?.nickname) localStorage.setItem('user_nickname', user.nickname);
        router.replace('/dashboard');
      } catch (err) {
        console.error('OAuth callback failed:', err);
        const detail = err.response?.data?.error || err.message || 'oauth_failed';
        if (kind === 'calendar') {
          router.replace(`/dashboard?calendar_error=${encodeURIComponent(detail)}`);
        } else {
          router.replace(`/login?error=${encodeURIComponent(detail)}`);
        }
      }
    }

    run();
  }, [provider, router, searchParams]);

  return (
    <main className="min-h-screen flex items-center justify-center bg-surface-page">
      <div className="text-center bg-white border border-line rounded-[18px] px-8 py-7 shadow-card">
        <div className="inline-block w-8 h-8 border-4 border-brand-blue border-t-transparent rounded-full animate-spin mb-4" />
        <p className="text-ink-secondary text-sm">{message}</p>
      </div>
    </main>
  );
}
