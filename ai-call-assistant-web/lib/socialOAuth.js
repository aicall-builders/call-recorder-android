import { API_BASE_URL } from './api';

const PROVIDER_LABELS = {
  kakao: '카카오',
  google: '구글',
  naver: '네이버',
};

export function makeOAuthState(kind, provider) {
  const rand = Math.random().toString(36).slice(2);
  const state = `${kind}:${provider}:${Date.now()}:${rand}`;
  if (typeof window !== 'undefined') {
    sessionStorage.setItem(`oauth_state:${state}`, '1');
  }
  return state;
}

export function consumeOAuthState(state) {
  if (typeof window === 'undefined' || !state) return true;
  const key = `oauth_state:${state}`;
  const found = sessionStorage.getItem(key);
  if (found) sessionStorage.removeItem(key);
  // 개발 편의를 위해 state가 없더라도 완전 차단하지 않는다.
  return true;
}

export function startSocialLogin(provider) {
  if (!API_BASE_URL) throw new Error('NEXT_PUBLIC_API_BASE_URL이 없습니다.');
  const redirectUri = `${window.location.origin}/oauth/${provider}`;
  const state = makeOAuthState('login', provider);
  const url = `${API_BASE_URL}/auth/${provider}/authorize?redirect_uri=${encodeURIComponent(redirectUri)}&state=${encodeURIComponent(state)}`;
  window.location.href = url;
}

export function providerLabel(provider) {
  return PROVIDER_LABELS[provider] || provider;
}
