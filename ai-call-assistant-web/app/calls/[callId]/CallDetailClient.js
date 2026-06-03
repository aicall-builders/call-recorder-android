'use client';

import { useEffect, useState, useMemo, useRef } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { callApi, storeApi } from '@/lib/api';
import { watchAuthState } from '@/lib/firebase';

const CATEGORY_EMOJI = {
  '예약': '📅', '주문': '📦', '취소': '❌', '환불': '💰',
  '불만': '😤', '문의': '❓', '칭찬': '🌟', '기타': '📌',
};

const SENTIMENT_INFO = {
  positive: { label: '긍정', cls: 'bg-green-100 text-green-800', emoji: '😊' },
  neutral:  { label: '중립', cls: 'bg-surface-muted text-ink-secondary', emoji: '😐' },
  negative: { label: '부정', cls: 'bg-red-100 text-red-800', emoji: '😞' },
};


const STATUS_INFO = {
  uploaded:    { label: '업로드 완료', cls: 'bg-status-uploaded-bg text-status-uploaded-text' },
  processing:  { label: '처리 중',     cls: 'bg-status-processing-bg text-status-processing-text' },
  transcribed: { label: '변환 완료',   cls: 'bg-status-transcribed-bg text-status-transcribed-text' },
  summarized:  { label: '요약 완료',   cls: 'bg-green-100 text-green-700' },
  error:       { label: '오류',        cls: 'bg-status-error-bg text-status-error-text' },
};

export default function CallDetailPage() {
  const params = useParams();
  const router = useRouter();
  const [callId, setCallId] = useState(null);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const pathParts = window.location.pathname.split('/').filter(Boolean);
      if (pathParts[0] === 'calls' && pathParts[1] && pathParts[1] !== 'placeholder') {
        setCallId(pathParts[1]);
      } else if (params.callId && params.callId !== 'placeholder') {
        setCallId(params.callId);
      }
    }
  }, [params.callId]);

  const [call, setCall] = useState(null);
  const [stores, setStores] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [copyMsg, setCopyMsg] = useState('');

  // ─── 오디오 플레이어 상태 ───
  const audioRef = useRef(null);
  const [audioUrl, setAudioUrl] = useState(null);
  const [audioLoading, setAudioLoading] = useState(false);
  const [audioError, setAudioError] = useState('');
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [audioDuration, setAudioDuration] = useState(0);

  useEffect(() => {
    if (!callId) return;

    const unsubscribe = watchAuthState(async (user) => {
      if (!user) {
        router.push('/login');
        return;
      }
      await loadData();
    });
    return () => unsubscribe();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [router, callId]);

  const loadData = async () => {
    setLoading(true);
    try {
      const [callRes, storesRes] = await Promise.all([
        callApi.get(callId),
        storeApi.list(),
      ]);
      setCall(callRes.data.call);
      setStores(storesRes.data.stores || []);
    } catch (err) {
      console.error('통화 상세 로딩 실패:', err);
      setError(err.response?.data?.message || '통화 정보를 불러오지 못했습니다');
    } finally {
      setLoading(false);
    }
  };

  // ─── 오디오 URL 가져오기 (재생 버튼 누를 때 lazy load) ───
  const loadAudioUrl = async () => {
    if (audioUrl) return audioUrl;
    setAudioLoading(true);
    setAudioError('');
    try {
      const res = await callApi.getAudio(callId);
      const url = res.data.audio_url || res.data.url;
      if (!url) throw new Error('음성 URL을 받지 못했습니다');
      setAudioUrl(url);
      return url;
    } catch (err) {
      console.error('오디오 URL 로딩 실패:', err);
      setAudioError(err.response?.data?.message || '음성 파일을 불러올 수 없습니다');
      return null;
    } finally {
      setAudioLoading(false);
    }
  };

  // ─── 재생/일시정지 토글 ───
  const togglePlay = async () => {
    if (!audioUrl) {
      // 처음 누르는 경우 — URL 받아오면 useEffect가 자동 재생
      await loadAudioUrl();
      return;
    }
    if (isPlaying) {
      audioRef.current?.pause();
    } else {
      audioRef.current?.play().catch(e => {
        console.error('재생 실패:', e);
        setAudioError('재생할 수 없습니다');
      });
    }
  };

  // ─── audioUrl 세팅된 직후 자동 재생 ───
  useEffect(() => {
    if (audioUrl && audioRef.current) {
      audioRef.current.play().catch(e => {
        console.error('자동 재생 실패:', e);
      });
    }
  }, [audioUrl]);

  // ─── 진행률 바 클릭으로 탐색(seek) ───
  const handleSeek = (e) => {
    if (!audioRef.current || !audioDuration) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    audioRef.current.currentTime = ratio * audioDuration;
  };

  const handleDelete = async () => {
    if (!confirm('이 통화를 삭제하시겠어요? 되돌릴 수 없습니다.')) return;
    try {
      await callApi.delete(callId);
      router.push('/dashboard');
    } catch (err) {
      console.error('삭제 실패:', err);
      alert('삭제에 실패했습니다');
    }
  };

  const formatDateTime = (dateStr) => {
    if (!dateStr) return '-';
    const d = new Date(dateStr);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const isToday = d >= today;
    const time = d.toLocaleString('ko-KR', { hour: '2-digit', minute: '2-digit' });
    if (isToday) return `오늘 ${time}`;
    return d.toLocaleString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  };

  const formatDuration = (sec) => {
    if (!sec && sec !== 0) return '-';
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${m}분 ${s}초`;
  };

  const formatTime = (sec) => {
    if (!sec && sec !== 0) return '0:00';
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  const parseKeywords = (keywords) => {
    if (!keywords) return [];
    if (Array.isArray(keywords)) return keywords;
    try { return JSON.parse(keywords); } catch { return []; }
  };

  const sttLines = useMemo(() => {
    if (!call?.stt_result) return [];
    return call.stt_result.split('\n').map((line, idx) => {
      const match = line.match(/^\[화자([^\]]+)\]:\s*(.*)$/);
      if (match) {
        const speaker = match[1];
        const text = match[2];
        return {
          idx,
          speaker,
          isCustomer: speaker === '1',
          text,
          isMatch: true,
        };
      }
      return { idx, text: line, isMatch: false };
    }).filter(x => x.text.trim());
  }, [call?.stt_result]);

  const handleCopyTranscript = async () => {
    if (!call?.stt_result) return;
    try {
      const text = sttLines
        .map(l => l.isMatch ? `${l.isCustomer ? '손님' : '사장님'}: ${l.text}` : l.text)
        .join('\n');
      await navigator.clipboard.writeText(text);
      setCopyMsg('✓ 복사 완료');
      setTimeout(() => setCopyMsg(''), 2000);
    } catch {
      setCopyMsg('복사 실패');
      setTimeout(() => setCopyMsg(''), 2000);
    }
  };

  if (loading) {
    return (
      <main className="min-h-screen flex items-center justify-center bg-surface-page">
        <div className="text-ink-tertiary text-sm">로딩 중...</div>
      </main>
    );
  }

  if (error || !call) {
    return (
      <main className="min-h-screen flex items-center justify-center bg-surface-page p-4">
        <div className="bg-white rounded-[16px] p-8 max-w-md w-full border border-line">
          <p className="text-red-600 mb-4 text-sm">{error || '통화를 찾을 수 없습니다'}</p>
          <button
            onClick={() => router.back()}
            className="text-sm text-ink-secondary hover:text-ink-primary"
          >
            ← 뒤로 가기
          </button>
        </div>
      </main>
    );
  }

  const status = STATUS_INFO[call.status] || { label: call.status, cls: 'bg-surface-muted text-ink-secondary' };
  const sentimentInfo = call.sentiment ? SENTIMENT_INFO[call.sentiment] : null;
  const categoryEmoji = call.category ? (CATEGORY_EMOJI[call.category] || '📌') : null;
  const keywords = parseKeywords(call.keywords);
  const displayNumber = call.caller_number || '발신번호 없음';
  const progressPct = audioDuration > 0 ? (currentTime / audioDuration) * 100 : 0;

  return (
    <main className="min-h-screen bg-surface-page">
      <div className="sticky top-0 z-10 backdrop-blur-md bg-surface-page/85 border-b border-line">
        <div className="max-w-[720px] mx-auto px-5 py-3 flex items-center gap-3">
          <button
            onClick={() => router.back()}
            className="inline-flex items-center gap-1 text-ink-secondary hover:text-ink-primary px-3 py-2 hover:bg-white rounded-[10px] transition-all"
            title="뒤로"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M19 12H5M12 19l-7-7 7-7"/>
            </svg>
          </button>
          <div className="flex-1 text-center text-[14px] font-semibold text-ink-primary tracking-tight">
            통화 상세
          </div>
          <button
            onClick={handleDelete}
            className="w-9 h-9 inline-flex items-center justify-center text-ink-tertiary hover:bg-red-50 hover:text-red-600 rounded-[10px] transition-all"
            title="삭제"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
            </svg>
          </button>
        </div>
      </div>

      <div className="max-w-[720px] mx-auto px-5 pt-6 pb-16">
        {/* ───────── 발신자 카드 ───────── */}
        <section className="bg-white rounded-[16px] p-5 border border-line mb-4 shadow-card animate-fade-up">
          <div className="flex items-center gap-3.5 mb-3.5">
            <div className="flex-none w-12 h-12 bg-brand-blue-light text-brand-blue rounded-full flex items-center justify-center text-[22px]">
              👤
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-[16px] font-bold text-ink-primary tracking-tight mb-0.5">
                {displayNumber}
              </div>
              <div className="text-[13px] text-ink-secondary">
                통화 녹음
              </div>
            </div>
            <span className={`text-[11px] font-bold px-2.5 py-1 rounded-full inline-flex items-center gap-1 ${status.cls}`}>
              {call.status === 'summarized' && (
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              )}
              {status.label}
            </span>
          </div>
          <div className="flex flex-wrap gap-x-4 gap-y-2 pt-3 border-t border-line">
            <div className="inline-flex items-center gap-1.5 text-[12.5px] text-ink-secondary">
              <svg className="text-ink-tertiary" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>
              {formatDateTime(call.created_at)}
            </div>
            <div className="inline-flex items-center gap-1.5 text-[12.5px] text-ink-secondary">
              <svg className="text-ink-tertiary" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                <line x1="12" y1="19" x2="12" y2="22"/>
              </svg>
              {formatDuration(call.duration)}
            </div>
          </div>

          <div className="flex flex-wrap gap-1.5 mt-3">
            {call.category && (
              <span className="text-[11px] font-semibold px-2.5 py-1 rounded-full inline-flex items-center gap-1 bg-brand-blue-light text-brand-blue-dark">
                {categoryEmoji} {call.category}
              </span>
            )}
            {sentimentInfo && (
              <span className={`text-[11px] font-semibold px-2.5 py-1 rounded-full inline-flex items-center gap-1 ${sentimentInfo.cls}`}>
                {sentimentInfo.emoji} {sentimentInfo.label}
              </span>
            )}
            {call.action_required === 1 && (
              <span className="text-[11px] font-semibold px-2.5 py-1 rounded-full inline-flex items-center gap-1 bg-status-processing-bg text-status-processing-text">
                ⚠️ 조치 필요
              </span>
            )}
          </div>
        </section>

        {/* ───────── 오디오 플레이어 (활성화) ───────── */}
        <section className="bg-white rounded-[16px] p-5 border border-line mb-4 animate-fade-up anim-delay-100">
          {audioUrl && (
            <audio
              ref={audioRef}
              src={audioUrl}
              preload="metadata"
              onLoadedMetadata={(e) => setAudioDuration(e.currentTarget.duration || 0)}
              onTimeUpdate={(e) => setCurrentTime(e.currentTarget.currentTime || 0)}
              onPlay={() => setIsPlaying(true)}
              onPause={() => setIsPlaying(false)}
              onEnded={() => { setIsPlaying(false); setCurrentTime(0); }}
              onError={() => setAudioError('음성 재생 중 오류가 발생했습니다')}
            />
          )}

          <div className="flex items-center gap-3.5">
            <button
              onClick={togglePlay}
              disabled={audioLoading}
              className="flex-none w-11 h-11 bg-brand-blue text-white rounded-full flex items-center justify-center hover:bg-brand-blue-dark transition-all disabled:opacity-50 disabled:cursor-not-allowed"
              title={isPlaying ? '일시정지' : '재생'}
            >
              {audioLoading ? (
                <svg className="animate-spin" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                  <path d="M21 12a9 9 0 1 1-6.219-8.56" />
                </svg>
              ) : isPlaying ? (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                  <rect x="6" y="4" width="4" height="16" rx="1"/>
                  <rect x="14" y="4" width="4" height="16" rx="1"/>
                </svg>
              ) : (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                  <polygon points="5 3 19 12 5 21 5 3"/>
                </svg>
              )}
            </button>

            <div
              className="flex-1 h-9 flex items-center cursor-pointer"
              onClick={handleSeek}
              role="slider"
              aria-label="재생 진행률"
              aria-valuenow={Math.round(progressPct)}
              aria-valuemin={0}
              aria-valuemax={100}
            >
              <div className="w-full h-1.5 bg-surface-muted rounded-full overflow-hidden relative">
                <div
                  className="absolute top-0 left-0 h-full bg-brand-blue rounded-full transition-[width] duration-100"
                  style={{ width: `${progressPct}%` }}
                />
              </div>
            </div>
          </div>

          <div className="flex justify-between mt-2 text-[11px] text-ink-tertiary font-mono">
            <span>{formatTime(currentTime)}</span>
            <span>{audioDuration > 0 ? formatTime(audioDuration) : formatDuration(call.duration)}</span>
          </div>

          {audioError && (
            <div className="mt-3 text-[11px] text-red-600 bg-red-50 px-3 py-2 rounded-[8px]">
              {audioError}
            </div>
          )}
        </section>

        {/* ───────── AI 요약 ───────── */}
        {call.summary && (
          <section className="bg-white rounded-[16px] border border-line mb-4 overflow-hidden animate-fade-up anim-delay-200">
            <div className="px-5 pt-4 pb-3 flex items-center justify-between">
              <h2 className="text-[14px] font-bold text-ink-primary tracking-tight inline-flex items-center gap-1.5">
                <span style={{ background: 'linear-gradient(135deg,#3B82F6,#1E40AF)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', backgroundClip: 'text' }}>✨</span>
                AI 요약
              </h2>
            </div>
            <div className="px-5 pb-5">
              <div className="bg-brand-blue-light rounded-[12px] px-4 py-3.5 text-[14px] text-ink-primary leading-[1.65]">
                {call.summary}
              </div>
              {keywords.length > 0 && (
                <div className="flex flex-wrap gap-1.5 mt-3">
                  {keywords.slice(0, 6).map((kw, idx) => (
                    <span
                      key={idx}
                      className="bg-brand-blue-light text-brand-blue-dark text-[12px] font-semibold px-3 py-1.5 rounded-full"
                    >
                      {kw}
                    </span>
                  ))}
                </div>
              )}
            </div>
          </section>
        )}

        {/* ───────── 통화 원문 (STT) ───────── */}
        {call.stt_result && (
          <section className="bg-white rounded-[16px] border border-line mb-4 overflow-hidden animate-fade-up anim-delay-300">
            <div className="px-5 pt-4 pb-3 flex items-center justify-between">
              <h2 className="text-[14px] font-bold text-ink-primary tracking-tight inline-flex items-center gap-1.5">
                💬 통화 원문
              </h2>
              <button
                onClick={handleCopyTranscript}
                className="text-[12px] text-ink-tertiary hover:text-ink-secondary inline-flex items-center gap-1 px-2 py-1 rounded-[8px] hover:bg-surface-muted transition-all"
              >
                {copyMsg ? (
                  <span className="text-brand-blue">{copyMsg}</span>
                ) : (
                  <>
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                    </svg>
                    복사
                  </>
                )}
              </button>
            </div>
            <div className="px-5 pb-5">
              <div className="bg-surface-page rounded-[12px] p-4 max-h-[480px] overflow-y-auto">
                {sttLines.map((line) => {
                  if (!line.isMatch) {
                    return (
                      <p key={line.idx} className="text-[13px] text-ink-secondary mb-1">
                        {line.text}
                      </p>
                    );
                  }
                  return (
                    <div
                      key={line.idx}
                      className={`flex flex-col mb-3.5 ${line.isCustomer ? 'items-start' : 'items-end'}`}
                    >
                      <div className={`text-[11px] text-ink-tertiary mb-1 font-mono ${line.isCustomer ? '' : 'text-right'}`}>
                        {line.isCustomer ? '👤 손님' : '🏪 사장님'}
                      </div>
                      <div
                        className={`max-w-[85%] px-3.5 py-2.5 text-[13px] leading-[1.55] rounded-[12px] border ${
                          line.isCustomer
                            ? 'bg-white border-line text-ink-primary'
                            : 'bg-brand-blue border-brand-blue text-white'
                        }`}
                      >
                        {line.text}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </section>
        )}

        <details className="bg-white rounded-[12px] border border-line mt-6">
          <summary className="cursor-pointer px-4 py-3 text-[12px] text-ink-tertiary font-mono select-none list-none">
            🔍 메타 정보
          </summary>
          <div className="px-4 pb-3 text-[11px] font-mono text-ink-secondary">
            <div className="flex justify-between py-1.5 border-t border-line">
              <span>통화 ID</span><span>{call.id?.slice(0, 8)}...</span>
            </div>
            <div className="flex justify-between py-1.5 border-t border-line">
              <span>상태</span><span>{call.status}</span>
            </div>
            <div className="flex justify-between py-1.5 border-t border-line">
              <span>읽음</span><span>{call.is_read === 1 ? '✓' : '안 읽음'}</span>
            </div>
          </div>
        </details>
      </div>
    </main>
  );
}