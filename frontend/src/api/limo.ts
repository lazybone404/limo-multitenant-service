// Limo Java 业务端 API 调用（经 vite proxy /api → 8082）
export interface AuthUser { token: string; username: string; points: number; planCode: string }
export interface SubscribeResult { planCode: string; planName: string; points: number; noteLimit: number }
export interface LimoNote { id: number; title: string; preview: string; content: string; type: string; tokens: number; points: number; version: number; sourceId: number; createdAt: string }

async function req<T>(path: string, opts: RequestInit = {}): Promise<T> {
  const t = localStorage.getItem('limo_token')
  const headers: Record<string, string> = { 'Content-Type': 'application/json', ...(opts.headers as any) }
  if (t) headers.Authorization = 'Bearer ' + t
  const r = await fetch('/api' + path, { ...opts, headers })
  let d: any = {}
  try { d = await r.json() } catch { /* not json */ }
  if (d.code !== 200) throw new Error(d.message || '请求失败')
  return d.data as T
}

export const login = (username: string, password: string) =>
  req<AuthUser>('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) })
export const register = (username: string, password: string) =>
  req<AuthUser>('/auth/register', { method: 'POST', body: JSON.stringify({ username, password }) })
export const subscribe = (planCode: string) =>
  req<SubscribeResult>('/subscribe', { method: 'POST', body: JSON.stringify({ planCode }) })
export const myNotes = () => req<LimoNote[]>('/notes', { method: 'GET' })
export const updateNote = (id: number, d: { title?: string; content?: string }) =>
  req<LimoNote>('/notes/' + id, { method: 'PUT', body: JSON.stringify(d) })
export const createTask = (d: { type: string; url?: string; text?: string; style?: string; options?: Record<string, boolean> }) =>
  req<{ taskId: number; status: string }>('/tasks', { method: 'POST', body: JSON.stringify(d) })
export const getTask = (id: number) =>
  req<{ taskId: number; status: string; tokens: number; points: number }>('/tasks/' + id)
export const deleteNote = (id: number) => req<any>('/notes/' + id, { method: 'DELETE' })

// 上传文件转笔记（multipart，走 /tasks/upload）
export const uploadTask = (file: File, type: string): Promise<{ taskId: number; status: string }> => {
  const t = localStorage.getItem('limo_token')
  const fd = new FormData()
  fd.append('file', file); fd.append('type', type)
  return fetch('/api/tasks/upload', { method: 'POST', headers: { Authorization: 'Bearer ' + t }, body: fd })
    .then(async r => { const d = await r.json(); if (d.code !== 200) throw new Error(d.message); return d.data })
}
