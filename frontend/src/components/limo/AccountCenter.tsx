import { useState } from 'react'
import { subscribe } from '../../api/limo'
import { useStore } from '../../store'

const PLANS = [
  { code: 'free', name: '免费版', price: 0, grants: 10, noteLimit: 30 },
  { code: 'monthly', name: '月度会员', price: 19.9, grants: 500, noteLimit: 500 },
  { code: 'monthly_auto', name: '连续包月', price: 19.9, grants: 500, noteLimit: 500 },
  { code: 'yearly', name: '年度会员', price: 199, grants: 5000, noteLimit: 3000 },
  { code: 'yearly_auto', name: '连续包年', price: 199, grants: 5000, noteLimit: 3000 },
]

/** 个人中心 —— 登录 / 套餐 / 积分 / 配额 / 订阅 / 退出 */
export default function AccountCenter({ onClose }: { onClose: () => void }) {
  const addToast = useStore(s => s.addToast)
  const [user, setUser] = useState(() => JSON.parse(localStorage.getItem('limo_user') || '{}'))
  const [msg, setMsg] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState('')

  const buy = async (code: string) => {
    setBusy(code); setMsg(''); setErr('')
    try {
      const d = await subscribe(code)
      const next = { ...user, points: d.points, planCode: d.planCode }
      localStorage.setItem('limo_user', JSON.stringify(next))
      setUser(next)
      addToast(`已订阅 ${d.planName}，积分 ${d.points}，笔记上限 ${d.noteLimit}`, 'success')
    } catch (e) { setErr((e as Error).message); addToast((e as Error).message, 'error') } finally { setBusy('') }
  }
  const logout = () => {
    localStorage.removeItem('limo_token')
    localStorage.removeItem('limo_user')
    window.location.reload()
  }

  return (
    <div className="fixed inset-0 z-[10001] flex items-center justify-center bg-black/30" onClick={onClose}>
      <div className="w-[560px] max-h-[85vh] overflow-auto rounded-2xl glass border border-border-default p-6" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold">个人中心</h2>
          <button onClick={onClose} className="text-muted hover:text-dimmed">✕</button>
        </div>

        <div className="flex flex-wrap gap-3 text-sm mb-5">
          <span className="px-3 py-1.5 rounded-xl bg-elevated border border-border-light">账户：{user.username || '—'}</span>
          <span className="px-3 py-1.5 rounded-xl bg-accent/15 text-accent border border-accent/20">{user.points ?? 0} 积分</span>
          <span className="px-3 py-1.5 rounded-xl bg-elevated border border-border-light">套餐：{user.planCode || 'free'}</span>
        </div>

        <div className="text-xs text-muted mb-2">订阅套餐（送积分 + 笔记上限）</div>
        <div className="grid grid-cols-2 gap-3">
          {PLANS.map(p => (
            <div key={p.code} className={`rounded-xl border p-4 ${user.planCode === p.code ? 'border-accent ring-1 ring-accent' : 'border-border-default'}`}>
              <div className="flex items-center justify-between">
                <span className="font-medium">{p.name}</span>
                <span className="text-accent font-semibold">{p.price === 0 ? '免费' : '¥' + p.price}</span>
              </div>
              <div className="text-xs text-dimmed my-2">
                <div>送 {p.grants} 积分 / 周期</div>
                <div>笔记上限 {p.noteLimit} 篇</div>
              </div>
              <button onClick={() => buy(p.code)} disabled={user.planCode === p.code || busy === p.code}
                className="w-full py-1.5 rounded-lg bg-accent text-white hover:bg-accent-hover disabled:opacity-40 text-xs">
                {user.planCode === p.code ? '当前' : (busy === p.code ? '订阅中…' : '订阅')}
              </button>
            </div>
          ))}
        </div>

        {msg && <p className="text-success text-sm mt-3">{msg}</p>}
        {err && <p className="text-error text-sm mt-3">{err}</p>}

        <button onClick={logout} className="mt-5 w-full py-2 rounded-lg border border-border-default text-sm hover:bg-surface-hover">退出登录</button>
      </div>
    </div>
  )
}
