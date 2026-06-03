import { Suspense } from 'react';
import OAuthCallbackClient from '../OAuthCallbackClient';

export default function Page() {
  return (
    <Suspense fallback={<main className="min-h-screen flex items-center justify-center">로딩 중...</main>}>
      <OAuthCallbackClient provider="naver" />
    </Suspense>
  );
}
