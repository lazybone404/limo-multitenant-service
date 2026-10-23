import { useState } from 'react'
import { login, register } from '../../api/limo'

/** 登录 / 注册门 —— 未登录进 C 端界面前显示 */
export default function AuthGate() {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [u, setU] = useState('')
  const [p, setP] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    setErr(''); setBusy(true)
    try {
      const d = mode === 'login' ? await login(u, p) : await register(u, p)
      localStorage.setItem('limo_token', d.token)
      localStorage.setItem('limo_user', JSON.stringify({ username: d.username, points: d.points, planCode: d.planCode }))
      window.location.reload()
    } catch (e) { setErr((e as Error).message || '操作失败') } finally { setBusy(false) }
  }

  return (
    <div className="h-screen flex items-center justify-center bg-surface text-foreground">
      <div className="card-enter w-96 p-6 rounded-2xl glass border border-border-default">
        <h1 className="text-xl font-bold text-accent">LiMo</h1>
        <p className="text-sm text-dimmed mt-1 mb-4">文档 / 视频 → 结构化笔记</p>
        <div className="flex gap-2 mb-4 text-sm">
          <button className={`${mode === 'login' ? 'text-accent font-semibold' : 'text-dimmed'}`} onClick={() => { setMode('login'); setErr('') }}>登录</button>
          <button className={`${mode === 'register' ? 'text-accent font-semibold' : 'text-dimmed'}`} onClick={() => { setMode('register'); setErr('') }}>注册</button>
        </div>
        <input value={u} onChange={e => setU(e.target.value)} placeholder="用户名"
          className="w-full px-3 py-2 mb-2 rounded-lg bg-elevated border border-border-default text-sm outline-none focus:border-accent" />
        <input value={p} onChange={e => setP(e.target.value)} type="password" placeholder="密码"
          className="w-full px-3 py-2 mb-2 rounded-lg bg-elevated border border-border-default text-sm outline-none focus:border-accent" />
        {err && <p className="text-error text-sm mb-2">{err}</p>}
        <button onClick={submit} disabled={busy}
          className="w-full py-2 rounded-lg bg-accent text-white hover:bg-accent-hover disabled:opacity-50 text-sm">
          {busy ? '处理中…' : (mode === 'login' ? '登录' : '注册')}
        </button>
      </div>
    </div>
  )
}
