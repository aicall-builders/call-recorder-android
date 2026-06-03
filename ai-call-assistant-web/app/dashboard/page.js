'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { logout, watchAuthState } from '@/lib/firebase';
import { storeApi, callApi, calendarApi } from '@/lib/api';
import { startCalendarConnect } from '@/lib/calendarOAuth';
import Logo from '../../app/components/Logo';

const CALENDAR_PROVIDERS = [
  { id: 'google', label: 'Google' },
  { id: 'naver', label: 'Naver' },
  { id: 'kakao', label: 'Kakao' },
];

const CATEGORY_LABELS = {
  reservation: '예약',
  order: '주문',
  cancel_refund: '취소/환불',
  complaint: '불만',
  hours_location: '문의',
  price: '가격',
  ingredients_allergy: '알레르기',
  catering_bulk: '단체',
  positive: '칭찬',
  other: '기타',
};

function parseInfo(call) {
  let info = call?.extracted_info;
  if (typeof info === 'string') {
    try { info = JSON.parse(info); } catch { info = {}; }
  }
  return info && typeof info === 'object' ? info : {};
}

function isReservation(call) {
  const info = parseInfo(call);
  const code = info.category_code || call.category;
  return code === 'reservation' || call.category === '예약' || Boolean(info.date && info.time);
}

function formatDateTime(value) {
  if (!value) return '-';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function formatDuration(sec) {
  if (!sec) return '-';
  const n = Number(sec);
  const m = Math.floor(n / 60);
  const s = n % 60;
  return m > 0 ? `${m}분 ${s}초` : `${s}초`;
}

function formatMenu(menu) {
  if (!menu) return '';
  if (Array.isArray(menu)) {
    return menu.map((item) => {
      if (typeof item === 'object' && item) {
        return [item.name, item.qty ? `${item.qty}` : null].filter(Boolean).join(' ');
      }
      return String(item);
    }).join(', ');
  }
  return String(menu);
}

export default function DashboardPage() {
  const router = useRouter();
  const fileInputRef = useRef(null);

  const [authLoading, setAuthLoading] = useState(true);
  const [user, setUser] = useState(null);
  const [nickname, setNickname] = useState('사장님');
  const [stores, setStores] = useState([]);
  const [calls, setCalls] = useState([]);
  const [connections, setConnections] = useState([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const calendarStatus = params.get('calendar');
    const calendarError = params.get('calendar_error');
    if (calendarStatus === 'connected') setMessage('캘린더 연결 완료');
    if (calendarError) setError(`캘린더 연결 실패: ${calendarError}`);
  }, []);

  useEffect(() => {
    const unsubscribe = watchAuthState(async (firebaseUser) => {
      if (!firebaseUser) {
        setAuthLoading(false);
        router.replace('/login');
        return;
      }
      setUser(firebaseUser);
      setNickname(localStorage.getItem('user_nickname') || '사장님');
      setAuthLoading(false);
      await loadAll();
    });
    return () => unsubscribe();
  }, [router]);

  async function loadAll() {
    setLoading(true);
    setError('');
    try {
      const [storesRes, callsRes, calRes] = await Promise.all([
        storeApi.list(),
        callApi.list({ limit: 200 }),
        calendarApi.listConnections().catch(() => ({ data: { connections: [] } })),
      ]);
      setStores(storesRes.data?.stores || []);
      setCalls(callsRes.data?.calls || []);
      setConnections(calRes.data?.connections || []);
    } catch (err) {
      console.error(err);
      setError(err.response?.data?.error || err.response?.data?.message || err.message || '데이터를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }

  async function handleLogout() {
    await logout();
    router.replace('/');
  }

  async function handleConnect(provider) {
    setError('');
    try {
      await startCalendarConnect(provider);
    } catch (err) {
      setError(err.response?.data?.error || err.message || '캘린더 연결을 시작하지 못했습니다.');
    }
  }

  async function handleSetDefault(provider) {
    try {
      await calendarApi.setDefault(provider);
      await loadAll();
    } catch (err) {
      setError(err.response?.data?.error || err.message || '기본 캘린더 설정 실패');
    }
  }

  async function handleDisconnect(provider) {
    if (!confirm(`${provider} 캘린더 연결을 해제할까요?`)) return;
    try {
      await calendarApi.disconnect(provider);
      await loadAll();
    } catch (err) {
      setError(err.response?.data?.error || err.message || '캘린더 연결 해제 실패');
    }
  }

  async function handleCreateCalendarEvent(callId, provider = null) {
    setError('');
    setMessage('');
    try {
      const res = await callApi.createCalendarEvent(callId, provider);
      setMessage(`${res.data?.provider || provider || '기본'} 캘린더에 일정 등록 완료`);
    } catch (err) {
      console.error(err);
      setError(err.response?.data?.error || err.message || '캘린더 일정 등록 실패');
    }
  }

  async function handleDelete(callId) {
    if (!confirm('이 통화를 삭제할까요?')) return;
    try {
      await callApi.delete(callId);
      setCalls((prev) => prev.filter((c) => c.id !== callId));
    } catch (err) {
      setError(err.response?.data?.error || err.message || '삭제 실패');
    }
  }

  async function handleFileSelect(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!stores.length) {
      setError('먼저 가게를 등록해주세요.');
      e.target.value = '';
      return;
    }
    const ext = '.' + file.name.split('.').pop().toLowerCase();
    const allowedExtensions = ['.mp3', '.m4a', '.wav', '.ogg', '.mp4'];
    if (!allowedExtensions.includes(ext)) {
      setError('지원하지 않는 파일 형식입니다. m4a, mp3, wav, ogg, mp4만 가능해요.');
      e.target.value = '';
      return;
    }

    const MIME_BY_EXT = { m4a: 'audio/mp4', mp4: 'audio/mp4', mp3: 'audio/mpeg', wav: 'audio/wav', ogg: 'audio/ogg' };
    const fileFormat = ext.slice(1);
    const mimeType = MIME_BY_EXT[fileFormat] || file.type || 'audio/mp4';

    setUploading(true);
    setError('');
    setMessage('');
    try {
      const uploadRes = await callApi.requestUpload({
        storeId: stores[0].id,
        fileName: file.name,
        fileFormat,
        mimeType,
      });
      const { call_id, upload_url, upload_headers } = uploadRes.data;
      await callApi.uploadToS3(upload_url, file, upload_headers || { 'Content-Type': mimeType });
      await callApi.startProcessing(call_id);
      setMessage(`업로드 완료: ${file.name}. AI 분석 중입니다.`);
      await loadAll();
    } catch (err) {
      console.error(err);
      setError(err.response?.data?.error || err.response?.data?.message || err.message || '업로드 실패');
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  }

  const defaultProvider = connections.find((c) => c.is_default)?.provider || connections[0]?.provider || null;
  const stats = useMemo(() => ({
    total: calls.length,
    summarized: calls.filter((c) => c.status === 'summarized').length,
    reservations: calls.filter(isReservation).length,
  }), [calls]);

  if (authLoading) {
    return <main className="min-h-screen flex items-center justify-center bg-surface-page text-sm text-ink-tertiary">로딩 중...</main>;
  }

  return (
    <main className="min-h-screen bg-surface-page">
      <header className="sticky top-0 z-10 bg-surface-page/90 backdrop-blur border-b border-line">
        <div className="max-w-[1000px] mx-auto px-6 py-3 flex items-center gap-3">
          <Link href="/dashboard" className="flex items-center gap-2 text-brand-blue font-bold text-[15px]">
            <Logo size={22} /> AI 통화 비서
          </Link>
          <div className="flex-1" />
          <Link href="/stores/new" className="text-[13px] border border-line rounded-[10px] px-3 py-2 hover:bg-white">가게 등록</Link>
          <button onClick={handleLogout} className="text-[13px] border border-line rounded-[10px] px-3 py-2 hover:bg-white">로그아웃</button>
        </div>
      </header>

      <div className="max-w-[1000px] mx-auto px-6 pt-7 pb-16">
        <section className="mb-6">
          <h1 className="text-[26px] font-bold text-ink-primary mb-1">안녕하세요, {nickname}님</h1>
          <p className="text-sm text-ink-secondary">소셜 로그인과 캘린더 연동 상태를 여기서 확인합니다.</p>
        </section>

        <section className="grid grid-cols-3 gap-3 mb-6">
          <StatCard label="전체 통화" value={stats.total} />
          <StatCard label="요약 완료" value={stats.summarized} />
          <StatCard label="예약 카드" value={stats.reservations} />
        </section>

        <section className="bg-white border border-line rounded-[16px] p-5 mb-5">
          <div className="flex items-center justify-between gap-3 mb-4">
            <div>
              <h2 className="text-[17px] font-bold text-ink-primary">캘린더 연동</h2>
              <p className="text-[13px] text-ink-secondary">Google / Naver / Kakao 캘린더를 연결한 뒤 예약 카드를 일정으로 등록합니다.</p>
            </div>
            <button onClick={loadAll} disabled={loading} className="text-[12px] border border-line rounded-[8px] px-3 py-2">새로고침</button>
          </div>
          <div className="grid md:grid-cols-3 gap-3">
            {CALENDAR_PROVIDERS.map((p) => {
              const conn = connections.find((c) => c.provider === p.id);
              return (
                <div key={p.id} className="border border-line rounded-[12px] p-4">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-bold text-ink-primary">{p.label}</span>
                    {conn ? <span className="text-[11px] text-emerald-700 bg-emerald-50 border border-emerald-100 rounded-full px-2 py-1">연결됨</span> : <span className="text-[11px] text-gray-600 bg-gray-50 border border-gray-100 rounded-full px-2 py-1">미연결</span>}
                  </div>
                  {conn && <p className="text-[12px] text-ink-tertiary mb-3 truncate">{conn.provider_email || conn.provider_nickname || '-'}</p>}
                  <div className="flex flex-wrap gap-2">
                    {!conn ? (
                      <button onClick={() => handleConnect(p.id)} className="text-[12px] bg-brand-blue text-white rounded-[8px] px-3 py-2">연결</button>
                    ) : (
                      <>
                        <button onClick={() => handleSetDefault(p.id)} disabled={conn.is_default} className="text-[12px] border border-line rounded-[8px] px-3 py-2 disabled:opacity-50">{conn.is_default ? '기본' : '기본 설정'}</button>
                        <button onClick={() => handleDisconnect(p.id)} className="text-[12px] border border-red-200 text-red-600 rounded-[8px] px-3 py-2">해제</button>
                      </>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </section>

        <section className="bg-white border border-line rounded-[16px] p-5 mb-5">
          <input ref={fileInputRef} type="file" accept="audio/*,.m4a,.mp3,.wav,.ogg,.mp4" onChange={handleFileSelect} disabled={uploading} className="hidden" />
          <button onClick={() => fileInputRef.current?.click()} disabled={uploading || !stores.length} className="w-full border-2 border-dashed border-line rounded-[12px] p-5 text-left hover:border-brand-blue disabled:opacity-50">
            <div className="font-bold text-ink-primary">{uploading ? '업로드 중...' : '통화 녹음 파일 업로드'}</div>
            <div className="text-[13px] text-ink-tertiary">m4a, mp3, wav, ogg, mp4</div>
          </button>
        </section>

        {error && <div className="mb-4 px-4 py-3 bg-red-50 border border-red-200 rounded-[10px] text-[13px] text-red-800 break-all">{error}</div>}
        {message && <div className="mb-4 px-4 py-3 bg-green-50 border border-green-200 rounded-[10px] text-[13px] text-green-800">{message}</div>}

        <section className="flex flex-col gap-3">
          {loading && !calls.length ? (
            <div className="text-center py-12 text-sm text-ink-tertiary">불러오는 중...</div>
          ) : calls.length === 0 ? (
            <div className="text-center py-16 px-5 bg-white rounded-[14px] border border-dashed border-line">
              <div className="text-3xl mb-3">📭</div>
              <h3 className="text-[16px] font-bold text-ink-primary mb-1">아직 통화가 없어요</h3>
              <p className="text-[13px] text-ink-secondary">녹음 파일을 업로드하거나 앱에서 자동 동기화를 시작하세요.</p>
            </div>
          ) : calls.map((call) => (
            <CallCard
              key={call.id}
              call={call}
              connections={connections}
              defaultProvider={defaultProvider}
              onCreateCalendarEvent={handleCreateCalendarEvent}
              onDelete={handleDelete}
            />
          ))}
        </section>
      </div>
    </main>
  );
}

function StatCard({ label, value }) {
  return (
    <div className="bg-white border border-line rounded-[14px] p-4">
      <div className="text-[26px] font-extrabold text-ink-primary">{value}<span className="text-[13px] ml-1 text-ink-tertiary">건</span></div>
      <div className="text-[12px] text-ink-secondary">{label}</div>
    </div>
  );
}

function CallCard({ call, connections, defaultProvider, onCreateCalendarEvent, onDelete }) {
  const info = parseInfo(call);
  const category = info.category_code || call.category || 'other';
  const label = CATEGORY_LABELS[category] || CATEGORY_LABELS.other;
  const reservation = isReservation(call);
  const menuText = formatMenu(info.menu || info.items);

  return (
    <article className="bg-white border border-line rounded-[14px] p-5 hover:shadow-[0_4px_12px_rgba(59,130,246,0.08)] transition-all">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="inline-flex items-center text-[11px] font-bold px-2 py-[3px] rounded-md border bg-blue-50 text-blue-700 border-blue-100">{label}</span>
          <span className="text-[12px] text-ink-tertiary">{formatDateTime(call.created_at)}</span>
          <span className="text-[12px] text-ink-tertiary">· {formatDuration(call.duration)}</span>
        </div>
        <div className="flex gap-2">
          <Link href={`/calls/${call.id}`} className="text-[12px] border border-line rounded-[8px] px-3 py-2 hover:bg-surface-page">상세</Link>
          <button onClick={() => onDelete(call.id)} className="text-[12px] border border-red-200 text-red-600 rounded-[8px] px-3 py-2">삭제</button>
        </div>
      </div>

      <div className="text-[20px] font-bold text-ink-primary mb-3 tabular-nums">{call.caller_number || '발신번호 없음'}</div>

      <div className="grid sm:grid-cols-2 gap-x-6 gap-y-1.5 text-[13px] mb-3">
        {info.customer_name && <Info label="성명" value={info.customer_name} />}
        {info.date && <Info label="날짜" value={info.date} />}
        {info.time && <Info label="시간" value={info.time} />}
        {info.party_size && <Info label="인원" value={`${info.party_size}명`} />}
        {menuText && <Info label="메뉴/항목" value={menuText} />}
        {info.special_notes && <Info label="특이사항" value={info.special_notes} />}
      </div>

      {call.summary && (
        <div className="bg-emerald-50 border border-emerald-100 rounded-[10px] px-3.5 py-3 mb-3">
          <div className="text-[11px] font-bold text-emerald-700 mb-1">AI 요약</div>
          <div className="text-[13px] text-emerald-900 leading-relaxed">{call.summary}</div>
        </div>
      )}

      {reservation && (
        <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-line/60">
          <span className="text-[12px] text-ink-secondary">캘린더 등록:</span>
          <button
            onClick={() => onCreateCalendarEvent(call.id, defaultProvider)}
            disabled={!connections.length}
            className="text-[12px] bg-brand-blue text-white rounded-[8px] px-3 py-2 disabled:opacity-50"
          >
            기본 캘린더
          </button>
          {connections.map((conn) => (
            <button key={conn.provider} onClick={() => onCreateCalendarEvent(call.id, conn.provider)} className="text-[12px] border border-line rounded-[8px] px-3 py-2 hover:bg-surface-page">
              {conn.provider}
            </button>
          ))}
          {!connections.length && <span className="text-[12px] text-red-600">먼저 캘린더를 연결하세요.</span>}
        </div>
      )}
    </article>
  );
}

function Info({ label, value }) {
  return (
    <div className="flex gap-2">
      <span className="w-20 flex-none text-ink-tertiary">{label}</span>
      <span className="font-medium text-ink-primary break-all">{value}</span>
    </div>
  );
}
