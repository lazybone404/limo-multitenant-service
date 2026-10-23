import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import 'github-markdown-css/github-markdown-light.css'
import './index.css'
import App from './App.tsx'
import LimoApp from './limo/LimoApp'

const LIMO_MODE = true  // 方案A：Limo 独立入口（登录门+工作台+个人中心）；false = 原始 C 端 App（口子）

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {LIMO_MODE ? <LimoApp /> : <App />}
  </StrictMode>,
)
