/**
 * API 클라이언트 — fetch 래퍼
 * JWT 토큰을 자동으로 Authorization 헤더에 포함
 */

export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'
export const BASE_URL = API_URL.replace(/\/api$/, '')

class ApiClient {
  _isRedirecting = false

  getToken() {
    return localStorage.getItem('fm_token')
  }

  setToken(token) {
    localStorage.setItem('fm_token', token)
  }

  removeToken() {
    localStorage.removeItem('fm_token')
  }

  async request(path, options = {}) {
    const token = this.getToken()
    const headers = {
      'Content-Type': 'application/json',
      ...options.headers,
    }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const response = await fetch(`${API_URL}${path}`, {
      ...options,
      headers,
      signal: options.signal || AbortSignal.timeout(30000),
    })

    // 401이면 토큰 만료 → Zustand 상태 정리 후 리다이렉트 (경쟁 조건 방지)
    if (response.status === 401) {
      this._handle401()
      return null
    }

    if (!response.ok) {
      const error = await response.json().catch(() => ({ error: '요청 실패' }))
      throw new Error(error.error || `HTTP ${response.status}`)
    }

    // 204 No Content 또는 빈 응답은 json 없음
    const contentLength = response.headers.get('content-length')
    if (response.status === 204 || contentLength === '0') return null
    return response.json()
  }

  get(path) {
    return this.request(path)
  }

  post(path, body) {
    return this.request(path, {
      method: 'POST',
      body: JSON.stringify(body),
    })
  }

  put(path, body) {
    return this.request(path, {
      method: 'PUT',
      body: JSON.stringify(body),
    })
  }

  patch(path, body) {
    return this.request(path, {
      method: 'PATCH',
      body: JSON.stringify(body),
    })
  }

  delete(path, body) {
    const options = { method: 'DELETE' }
    if (body) options.body = JSON.stringify(body)
    return this.request(path, options)
  }

  /** 파일 업로드 (multipart/form-data) — Content-Type 헤더를 설정하지 않음 (브라우저가 boundary 자동 설정) */
  async upload(path, formData) {
    const token = this.getToken()
    const headers = {}
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const response = await fetch(`${API_URL}${path}`, {
      method: 'POST',
      headers,
      body: formData,
      signal: AbortSignal.timeout(60000),
    })

    if (response.status === 401) {
      this._handle401()
      return null
    }

    if (!response.ok) {
      const error = await response.json().catch(() => ({ error: '업로드 실패' }))
      throw new Error(error.error || `HTTP ${response.status}`)
    }

    return response.json()
  }

  /** Blob 다운로드 (ZIP 등) — returns { blob, headers } */
  async downloadBlob(path, body) {
    const token = this.getToken()
    const headers = { 'Content-Type': 'application/json' }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const response = await fetch(`${API_URL}${path}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(120000),
    })

    if (response.status === 401) {
      this._handle401()
      return null
    }

    if (!response.ok) {
      const error = await response.json().catch(() => ({ error: '다운로드 실패' }))
      throw new Error(error.error || `HTTP ${response.status}`)
    }

    const blob = await response.blob()
    const exportId = response.headers.get('X-Export-Id')
    return { blob, exportId }
  }

  /** 파일 업로드 PUT (multipart/form-data) */
  async uploadPut(path, formData) {
    const token = this.getToken()
    const headers = {}
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const response = await fetch(`${API_URL}${path}`, {
      method: 'PUT',
      headers,
      body: formData,
      signal: AbortSignal.timeout(60000),
    })

    if (response.status === 401) {
      this._handle401()
      return null
    }

    if (!response.ok) {
      const error = await response.json().catch(() => ({ error: '업로드 실패' }))
      throw new Error(error.error || `HTTP ${response.status}`)
    }

    return response.json()
  }

  /** 401 공통 처리 — 경쟁 조건 방지 (isRedirecting 플래그) */
  _handle401() {
    if (this._isRedirecting) return
    this._isRedirecting = true
    this.removeToken()
    import('../store/authStore.js').then(mod => {
      mod.default.getState().logout()
    }).catch(() => {})
    // 세션 만료 이벤트 발행 — SessionExpiredModal이 수신하여 사용자에게 안내
    window.dispatchEvent(new CustomEvent('session-expired'))
  }
}

const api = new ApiClient()
export default api
