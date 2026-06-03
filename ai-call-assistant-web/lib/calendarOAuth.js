import { calendarApi } from './api';
import { makeOAuthState } from './socialOAuth';

export async function startCalendarConnect(provider) {
  const redirectUri = `${window.location.origin}/oauth/${provider}`;
  const state = makeOAuthState('calendar', provider);
  const res = await calendarApi.getAuthorizeUrl(provider, redirectUri, state);
  const authorizeUrl = res.data?.authorize_url;
  if (!authorizeUrl) throw new Error('캘린더 OAuth URL을 받지 못했습니다.');
  window.location.href = authorizeUrl;
}
