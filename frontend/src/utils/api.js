const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

async function apiCall(endpoint, options = {}) {
  const token = sessionStorage.getItem('docusense_token');
  
  const headers = { ...options.headers };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = headers['Content-Type'] || 'application/json';
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers
  });

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      sessionStorage.clear();
      window.dispatchEvent(new Event('auth_session_expired'));
    }
    const errText = await response.text();
    throw new Error(errText || `Request failed with status ${response.status}`);
  }

  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return response.json();
  }
  return response.text();
}

export const api = {
  login: async (username, password) => {
    return apiCall('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password })
    });
  },

  register: async (username, password, department, role) => {
    return apiCall('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, password, department, role })
    });
  },

  uploadDocument: async (formData) => {
    return apiCall('/api/documents/upload', {
      method: 'POST',
      body: formData
    });
  },

  deleteDocument: async (id) => {
    return apiCall(`/api/documents/${id}`, {
      method: 'DELETE'
    });
  },

  listDocuments: async () => {
    return apiCall('/api/documents', {
      method: 'GET'
    });
  },

  chat: async (query, history) => {
    return apiCall('/api/search/chat', {
      method: 'POST',
      body: JSON.stringify({ query, history })
    });
  },

  submitFeedback: async (query, answer, rating) => {
    return apiCall('/api/feedback', {
      method: 'POST',
      body: JSON.stringify({ query, answer, rating })
    });
  },

  getObservabilityStats: async () => {
    return apiCall('/api/observability/stats', {
      method: 'GET'
    });
  },

  getObservabilityLogs: async (page = 0, size = 10, username = '') => {
    const params = new URLSearchParams({ page, size });
    if (username) {
      params.append('username', username);
    }
    return apiCall(`/api/observability/logs?${params.toString()}`, {
      method: 'GET'
    });
  },

  getObservabilityFeedbacks: async (page = 0, size = 10) => {
    const params = new URLSearchParams({ page, size });
    return apiCall(`/api/observability/feedbacks?${params.toString()}`, {
      method: 'GET'
    });
  }
};
