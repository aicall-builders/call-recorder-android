import axios from 'axios';

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL;

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use(async (config) => {
  if (typeof window !== 'undefined') {
    try {
      const { auth } = await import('./firebase');
      const user = auth.currentUser;
      if (user) {
        const token = await user.getIdToken();
        config.headers.Authorization = `Bearer ${token}`;
      } else {
        const token = localStorage.getItem('firebase_id_token');
        if (token) config.headers.Authorization = `Bearer ${token}`;
      }
    } catch {
      const token = localStorage.getItem('firebase_id_token');
      if (token) config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && typeof window !== 'undefined') {
      localStorage.removeItem('firebase_id_token');
      localStorage.removeItem('firebase_uid');
      if (!window.location.pathname.startsWith('/login') && !window.location.pathname.startsWith('/oauth')) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export const authApi = {
  socialLogin: ({ provider, authorizationCode, providerAccessToken, redirectUri, state }) =>
    api.post(`/auth/${provider}`, {
      provider,
      authorization_code: authorizationCode,
      provider_access_token: providerAccessToken,
      redirect_uri: redirectUri,
      state,
    }),
  verify: () => api.post('/auth/verify'),
  logout: () => api.post('/auth/logout'),
};

export const storeApi = {
  list: () => api.get('/stores'),
  create: (name, industry = 'food') => api.post('/stores', { name, industry }),
};

export const callApi = {
  requestUpload: ({ storeId, fileName, fileFormat = 'm4a', mimeType = 'audio/mp4', callerNumber = null, duration = null }) =>
    api.post('/calls/upload', {
      store_id: storeId,
      file_name: fileName,
      file_format: fileFormat,
      mime_type: mimeType,
      caller_number: callerNumber,
      duration,
    }),

  uploadToS3: (uploadUrl, file, uploadHeaders = null) =>
    axios.put(uploadUrl, file, {
      headers: uploadHeaders || { 'Content-Type': file.type || 'audio/mp4' },
    }),

  startProcessing: (callId) => api.post(`/calls/${callId}/process`),
  list: ({ storeId = null, status = null, limit = 20, offset = 0 } = {}) =>
    api.get('/calls', { params: { store_id: storeId, status, limit, offset } }),
  get: (callId) => api.get(`/calls/${callId}`),
  updateCategory: (callId, callerCategory) => api.patch(`/calls/${callId}`, { caller_category: callerCategory }),
  getAudio: (callId) => api.get(`/calls/${callId}/audio`),
  delete: (callId) => api.delete(`/calls/${callId}`),
  createCalendarEvent: (callId, provider = null) => api.post(`/calls/${callId}/calendar-events`, { provider }),
};

export const summaryApi = {
  get: (callId) => api.get(`/summaries/${callId}`),
};

export const calendarApi = {
  listConnections: () => api.get('/calendar/connections'),
  getAuthorizeUrl: (provider, redirectUri, state) =>
    api.get(`/calendar/connections/${provider}/authorize`, { params: { redirect_uri: redirectUri, state } }),
  completeOAuth: ({ provider, authorizationCode, redirectUri, state }) =>
    api.post('/calendar/connections/oauth-code', {
      provider,
      authorization_code: authorizationCode,
      redirect_uri: redirectUri,
      state,
    }),
  setDefault: (provider) => api.patch('/calendar/connections/default', { provider }),
  disconnect: (provider) => api.delete(`/calendar/connections/${provider}`),
};

export default api;
