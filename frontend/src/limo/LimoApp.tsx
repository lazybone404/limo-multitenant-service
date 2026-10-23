import { useState } from 'react'
import { Toast } from '../components/ui/Toast'
import AuthGate from '../components/limo/AuthGate'
import AccountCenter from '../components/limo/AccountCenter'
import LimoWorkbench from '../components/limo/LimoWorkbench'

/**
 * 方案 A：Limo 独立入口。
 * 登录门 + 顶栏个人中心 + 核心工作台(LimoWorkbench)，全部走 Java /api。
 * 不掺 C 端 App 逻辑（避免 C 端 init/面板的 unused 死代码与报错）；C 端 App 保留作口子（LIMO_MODE=false）。
 */
export default function LimoApp() {
  const [user] = useState(() => JSON.parse(localStorage.getItem('limo_user') || '{}'))
  const [showAccount, setShowAccount] = useState(false)
  const token = localStorage.getItem('limo_token')

  if (!token) return <><AuthGate /><Toast /></>

  return (
    <>
      <div className="h-screen flex flex-col overflow-hidden">
        <header className="glass border-b border-border-light h-12 flex items-center px-4 shrink-0 z-10">
          <span className="font-semibold text-[15px]">LiMo</span>
          <div className="flex-1" />
          <button onClick={() => setShowAccount(true)}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg hover:bg-surface-hover text-muted hover:text-dimmed transition-colors">
            <span className="w-6 h-6 rounded-full bg-accent/15 flex items-center justify-center text-accent text-[11px] font-semibold">{(user.username || 'U').slice(0, 1).toUpperCase()}</span>
            <span className="text-[12px]">{user.points ?? 0} 积分</span>
          </button>
        </header>
        <LimoWorkbench />
      </div>
      {showAccount && <AccountCenter onClose={() => setShowAccount(false)} />}
      <Toast />
    </>
  )
}
