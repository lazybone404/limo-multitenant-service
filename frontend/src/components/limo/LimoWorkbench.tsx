import { useState, useEffect, useCallback, useRef } from 'react'
import ReactMarkdown from 'react-markdown'
import { Sparkles, Loader2, FileText, Pencil, Trash2, Check, X, Search, Upload } from 'lucide-react'
import { useStore } from '../../store'
import { createTask, getTask, myNotes, updateNote, deleteNote, uploadTask, type LimoNote } from '../../api/limo'

/** Limo 核心工作台 —— 提交任务 → 轮询 → 笔记列表 → 预览/编辑（走 Java，复用 C 端 Toast/视觉） */
export default function LimoWorkbench() {
  const addToast = useStore(s => s.addToast)
  const [type, setType] = useState('document')
  const [url, setUrl] = useState('')
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [phase, setPhase] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [drag, setDrag] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)
  const [style, setStyle] = useState('detailed')
  const [opts, setOpts] = useState<Record<string, boolean>>({ summary: true })
  const [notes, setNotes] = useState<LimoNote[]>([])
  const [sel, setSel] = useState<LimoNote | null>(null)
  const [edit, setEdit] = useState(false)
  const [draft, setDraft] = useState('')

  const load = useCallback(async () => {
    try { setNotes(await myNotes()) } catch (e) { addToast((e as Error).message, 'error') }
  }, [addToast])
  useEffect(() => { load() }, [load])

  const submit = async () => {
    setBusy(true); setPhase('提交中…')
    try {
      const body: any = { type }
      if (style) body.style = style
      if (opts) body.options = opts
      if (url.trim()) body.url = url.trim()
      else if (text.trim()) body.text = text.trim()
      else { addToast('请输入链接或文本', 'error'); setBusy(false); setPhase(''); return }
      const d = await createTask(body)
      let status = d.status
      for (let i = 0; i < 40 && status !== 'DONE' && status !== 'FAILED'; i++) {
        await new Promise(r => setTimeout(r, 1500))
        status = (await getTask(d.taskId)).status
        setPhase(status === 'PROCESSING' ? 'AI 生成中…' : '排队中…')
      }
      if (status === 'DONE') { setText(''); await load(); addToast('笔记已生成', 'success') }
      else if (status === 'FAILED') addToast('任务处理失败', 'error')
      else addToast('任务仍在处理中，稍后刷新查看', 'info')
    } catch (e) { addToast((e as Error).message, 'error') } finally { setBusy(false); setPhase('') }
  }

  const open = (n: LimoNote) => { setSel(n); setEdit(false); setDraft(n.content || '') }

  const onDrop = (e: React.DragEvent) => { e.preventDefault(); setDrag(false); const f = e.dataTransfer.files[0]; if (f) setFile(f) }
  const submitFile = async () => {
    if (!file) { addToast('请拖入或选择文件', 'error'); return }
    setBusy(true); setPhase('上传中…')
    try {
      const d = await uploadTask(file, type)
      let status = d.status
      for (let i = 0; i < 40 && status !== 'DONE' && status !== 'FAILED'; i++) {
        await new Promise(r => setTimeout(r, 1500))
        status = (await getTask(d.taskId)).status
        setPhase(status === 'PROCESSING' ? 'AI 生成中…' : '排队中…')
      }
      if (status === 'DONE') { setFile(null); await load(); addToast('文件已转成笔记', 'success') }
      else if (status === 'FAILED') addToast('文件处理失败', 'error')
      else addToast('任务仍在处理中', 'info')
    } catch (e) { addToast((e as Error).message, 'error') } finally { setBusy(false); setPhase('') }
  }
  const save = async () => {
    try { const u = await updateNote(sel!.id, { content: draft }); setSel({ ...sel!, content: u.content }); setEdit(false); load(); addToast('已保存', 'success') }
    catch (e) { addToast((e as Error).message, 'error') }
  }
  const remove = async () => {
    try { await deleteNote(sel!.id); setSel(null); load(); addToast('已删除', 'success') }
    catch (e) { addToast((e as Error).message, 'error') }
  }

  return (
    <div className="flex flex-1 overflow-hidden">
      {/* 左：来源输入 */}
      <aside className="w-72 shrink-0 border-r border-border-default p-4 flex flex-col gap-3 overflow-auto">
        <div className="flex items-center gap-2">
          <span className="font-medium text-sm">来源</span>
          <select value={type} onChange={e => setType(e.target.value)}
            className="px-2 py-1 rounded-lg bg-elevated border border-border-default text-xs outline-none focus:border-accent transition-colors">
            <option value="document">文档</option>
            <option value="video">视频</option>
          </select>
        </div>
          {/* 按类型定制：风格（文档/视频不同风格集）+ 选项 */}
        <div className="text-[11px] text-muted">类型定制</div>
        <select value={style} onChange={e => setStyle(e.target.value)}
          className="w-full px-2 py-1.5 rounded-lg bg-elevated border border-border-default text-xs outline-none focus:border-accent transition-colors">
          <option value="detailed">详细模式</option>
          <option value="concise">精简模式</option>
          <option value="academic">学术风格</option>
          <option value="tutorial">教程笔记</option>
          {type === 'document' ? <option value="outline">大纲式（文档）</option> : <option value="meeting">会议纪要（视频）</option>}
        </select>
        <div className="flex gap-3 text-xs text-dimmed">
          <label className="flex items-center gap-1 cursor-pointer"><input type="checkbox" checked={!!opts.summary} onChange={e => setOpts({ ...opts, summary: e.target.checked })} className="accent-accent" /> 摘要</label>
          <label className="flex items-center gap-1 cursor-pointer"><input type="checkbox" checked={!!opts.toc} onChange={e => setOpts({ ...opts, toc: e.target.checked })} className="accent-accent" /> 目录</label>
        </div>
      <input value={url} onChange={e => setUrl(e.target.value)} placeholder="链接"
          className="w-full px-3 py-2 rounded-lg bg-elevated border border-border-default text-sm outline-none focus:border-accent transition-colors" />
        <textarea value={text} onChange={e => setText(e.target.value)} rows={6} placeholder="或粘贴文本"
          className="w-full px-3 py-2 rounded-lg bg-elevated border border-border-default text-sm resize-y outline-none focus:border-accent transition-colors" />
        <div onDragOver={e => { e.preventDefault(); setDrag(true) }} onDragLeave={() => setDrag(false)} onDrop={onDrop}
          onClick={() => fileInput.current?.click()}
          className={`border-2 border-dashed rounded-lg p-4 text-center cursor-pointer text-xs transition-colors ${drag ? 'border-accent bg-accent/10' : 'border-border-default hover:border-accent'}`}>
          <input ref={fileInput} type="file" className="hidden" onChange={e => { const f = e.target.files?.[0]; if (f) setFile(f) }} />
          <Upload size={16} className={`mx-auto mb-1 ${file ? 'text-accent' : 'text-muted'}`} />
          {file ? <div className="text-foreground truncate">{file.name}</div> : <div className="text-muted">拖拽文件到此处，或点击选择</div>}
        </div>
        <button onClick={submit} disabled={busy}
          className="flex items-center justify-center gap-1.5 px-4 py-2 rounded-lg bg-accent text-white hover:bg-accent-hover active:scale-[0.98] disabled:opacity-50 text-sm transition-all">
          {busy ? <><Loader2 size={15} className="animate-spin" />{phase || '处理中…'}</> : <><Sparkles size={15} />转成笔记</>}
        </button>
        {file && (
          <button onClick={submitFile} disabled={busy}
            className="flex items-center justify-center gap-1.5 px-4 py-2 rounded-lg border border-accent text-accent hover:bg-accent/10 transition-colors text-sm">
            <Upload size={15} />上传 {file.name} 转笔记
          </button>
        )}
      </aside>

      {/* 中：笔记列表 */}
      <div className="w-64 shrink-0 border-r border-border-default overflow-auto">
        <div className="px-4 py-3 text-xs text-muted">我的笔记 · {notes.length}</div>
        {!notes.length && (
          <div className="text-dimmed text-xs text-center py-8">
            <FileText size={20} className="mx-auto mb-2 opacity-30" />
            暂无笔记，从左边转入一篇吧
          </div>
        )}
        {notes.map(n => (
          <div key={n.id} onClick={() => open(n)}
            className={`px-3 py-2 cursor-pointer border-b border-border-light transition-colors ${sel?.id === n.id ? 'bg-accent/10' : 'hover:bg-surface-hover'}`}>
            <div className="text-sm truncate">{n.title || '无标题'}</div>
            <div className="text-[11px] text-muted truncate mt-0.5">{n.preview || '—'}</div>
            <div className="text-[10px] text-muted mt-1">{n.tokens}t · {n.points}积 · v{n.version}</div>
          </div>
        ))}
      </div>

      {/* 右：预览 / 编辑 */}
      <main className="flex-1 min-w-0 overflow-auto p-6">
        {!sel ? (
          <div className="h-full flex items-center justify-center text-muted text-sm">
            <div className="text-center">
              <div className="text-5xl mb-3 opacity-20"><Search size={40} className="mx-auto" /></div>
              <div>左侧选一篇笔记预览，或转入一篇</div>
            </div>
          </div>
        ) : edit ? (
          <div className="space-y-2">
            <textarea value={draft} onChange={e => setDraft(e.target.value)} rows={22}
              className="w-full px-3 py-2 rounded-lg bg-elevated border border-border-default font-mono text-sm outline-none focus:border-accent resize-y transition-colors"
              placeholder="# 笔记标题\n\n## 要点\n- ..." />
            <div className="flex gap-2 items-center">
              <button onClick={save} className="flex items-center gap-1 px-4 py-1.5 rounded-lg bg-accent text-white text-sm hover:bg-accent-hover transition-colors"><Check size={14} />保存</button>
              <button onClick={() => { setEdit(false); setDraft(sel.content || '') }} className="flex items-center gap-1 px-4 py-1.5 rounded-lg border border-border-default text-sm hover:bg-surface-hover transition-colors"><X size={14} />取消</button>
              <button onClick={remove} className="ml-auto flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm text-error hover:bg-error/10 transition-colors"><Trash2 size={14} />删除</button>
            </div>
          </div>
        ) : (
          <div className="max-w-3xl mx-auto">
            <div className="flex items-center gap-2 mb-3 text-xs text-dimmed">
              <h1 className="text-lg font-semibold text-foreground mr-auto">{sel.title || '无标题笔记'}</h1>
              <span>{sel.type} · v{sel.version} · {sel.tokens}t · {sel.points}积</span>
              <button onClick={() => setEdit(true)} className="flex items-center gap-1 hover:text-accent transition-colors"><Pencil size={13} />编辑</button>
              <button onClick={remove} className="hover:text-error transition-colors"><Trash2 size={13} />删除</button>
            </div>
            <div className="markdown-body"><ReactMarkdown>{sel.content || ''}</ReactMarkdown></div>
          </div>
        )}
      </main>
    </div>
  )
}
