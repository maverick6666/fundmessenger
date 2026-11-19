import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useSearchParams, useOutletContext, useNavigate } from 'react-router-dom'
import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import Placeholder from '@tiptap/extension-placeholder'
import { Table, TableRow, TableCell, TableHeader } from '@tiptap/extension-table'
import { Markdown } from 'tiptap-markdown'
import ChartCodeBlock from '../components/ChartNode'
import api from '../lib/api'
import useToastStore from '../store/toastStore'
import ConfirmModal from '../components/ConfirmModal'
import handleApiError from '../lib/handleApiError'
import { BASE_URL } from '../lib/api'
import { normalizeChartMarkdown } from '../lib/chartMarkdown'
import { parseEditorDocument, serializeEditorDocument } from '../lib/editorDocument'

const API_BASE = BASE_URL

export default function PostWritePage() {
  const { boardId: paramBoardId, postId } = useParams()
  const [searchParams] = useSearchParams()
  const { clubInfo } = useOutletContext()
  const navigate = useNavigate()
  const addToast = useToastStore(s => s.addToast)

  const isEdit = !!postId
  const [title, setTitle] = useState('')
  const [saving, setSaving] = useState(false)
  const [loadingPost, setLoadingPost] = useState(isEdit)

  // 게시판 선택 (글쓰기 시, M:N pill 다중선택)
  const [boards, setBoards] = useState([])
  const [selectedBoardIds, setSelectedBoardIds] = useState(() => paramBoardId ? [Number(paramBoardId)] : [])

  // M:N 태그 선택
  const [categories, setCategories] = useState([])
  const [selectedCategoryIds, setSelectedCategoryIds] = useState([])

  // M:N 팀 선택 (0개 = 무소속)
  const [myTeams, setMyTeams] = useState([])
  const [selectedTeamIds, setSelectedTeamIds] = useState([])

  const [alertModal, setAlertModal] = useState({ open: false, message: '' })

  // 슬래시 커맨드
  const [slashOpen, setSlashOpen] = useState(false)
  const [slashPos, setSlashPos] = useState(null)
  const [slashFilter, setSlashFilter] = useState('')
  const [slashIndex, setSlashIndex] = useState(0)
  const slashRef = useRef(null)
  const editorRef = useRef(null)

  // 게시판 + 카테고리 목록 조회
  useEffect(() => {
    if (!clubInfo?.clubId) return
    Promise.all([
      api.get(`/clubs/${clubInfo.clubId}/boards`),
      api.get(`/clubs/${clubInfo.clubId}/semi-categories`)
    ]).then(([boardData, catData]) => {
      const editableBoards = (boardData || []).filter((board) => board.boardType !== 'AI')
      const editableCategories = (catData || []).filter((category) => category.name !== 'AI')
      setBoards(editableBoards)
      setCategories(editableCategories)
      if (!paramBoardId && editableBoards.length) {
        const writable = editableBoards.find(b => b.canWrite)
        if (writable) setSelectedBoardIds([writable.id])
      }
    }).catch(e => handleApiError(e, '게시판 정보 조회'))

    // 팀 목록: 사용자 소속 팀
    const teams = clubInfo.myTeams || []
    setMyTeams(teams)
    if (!isEdit && teams.length === 1) {
      setSelectedTeamIds([teams[0].teamId])
    }
  }, [clubInfo?.clubId])

  // 쓰기 권한 없으면 리다이렉트
  useEffect(() => {
    if (!boards.length) return
    const hasWritable = boards.some(b => b.canWrite)
    if (!hasWritable && !isEdit) {
      navigate('/club/boards', { replace: true })
    }
  }, [boards])

  // 이미지 업로드
  const uploadImage = useCallback(async (file) => {
    const formData = new FormData()
    formData.append('file', file)
    const data = await api.upload('/files/images', formData)
    return `${API_BASE}${data.url}`
  }, [])

  // 슬래시 커맨드 항목
  const slashCommands = [
    { label: '제목 2', icon: 'H2', action: (e) => e.chain().focus().toggleHeading({ level: 2 }).run() },
    { label: '제목 3', icon: 'H3', action: (e) => e.chain().focus().toggleHeading({ level: 3 }).run() },
    { label: '글머리 기호', icon: '•', action: (e) => e.chain().focus().toggleBulletList().run() },
    { label: '번호 목록', icon: '1.', action: (e) => e.chain().focus().toggleOrderedList().run() },
    { label: '이미지', icon: '🖼', action: () => handleInsertImage() },
    { label: '인용', icon: '❝', action: (e) => e.chain().focus().toggleBlockquote().run() },
    { label: '코드 블록', icon: '</>', action: (e) => e.chain().focus().toggleCodeBlock().run() },
    { label: '구분선', icon: '—', action: (e) => e.chain().focus().setHorizontalRule().run() },
  ]

  const filteredSlashCommands = slashCommands.filter(cmd =>
    cmd.label.toLowerCase().includes(slashFilter.toLowerCase())
  )

  const executeSlashCommand = useCallback((cmd) => {
    const ed = editorRef.current
    if (!ed || !slashPos) return
    // 슬래시 텍스트 삭제
    const from = slashPos
    const to = ed.state.selection.from
    ed.chain().focus().deleteRange({ from, to }).run()
    // 커맨드 실행
    cmd.action(ed)
    setSlashOpen(false)
    setSlashFilter('')
    setSlashPos(null)
  }, [slashPos])

  const editor = useEditor({
    extensions: [
      StarterKit.configure({ codeBlock: false }),
      ChartCodeBlock,
      Image.configure({ inline: false, allowBase64: false }),
      Placeholder.configure({ placeholder: '내용을 입력하세요... ( / 로 블록 삽입)' }),
      Table.configure({ resizable: false }),
      TableRow,
      TableCell,
      TableHeader,
      Markdown.configure({
        html: true,
        transformPastedText: true,
        transformCopiedText: true,
      }),
    ],
    content: '',
    editorProps: {
      handleKeyDown: (view, event) => {
        if (slashOpen) {
          if (event.key === 'ArrowDown') {
            event.preventDefault()
            setSlashIndex(prev => Math.min(prev + 1, filteredSlashCommands.length - 1))
            return true
          }
          if (event.key === 'ArrowUp') {
            event.preventDefault()
            setSlashIndex(prev => Math.max(prev - 1, 0))
            return true
          }
          if (event.key === 'Enter') {
            event.preventDefault()
            if (filteredSlashCommands[slashIndex]) {
              executeSlashCommand(filteredSlashCommands[slashIndex])
            }
            return true
          }
          if (event.key === 'Escape') {
            event.preventDefault()
            setSlashOpen(false)
            setSlashFilter('')
            setSlashPos(null)
            return true
          }
        }
        return false
      },
      handleDrop: (view, event, slice, moved) => {
        if (!moved && event.dataTransfer?.files?.length) {
          const file = event.dataTransfer.files[0]
          if (file.type.startsWith('image/')) {
            event.preventDefault()
            uploadImage(file).then(url => {
              const { schema } = view.state
              const node = schema.nodes.image.create({ src: url })
              const transaction = view.state.tr.replaceSelectionWith(node)
              view.dispatch(transaction)
            }).catch(err => setAlertModal({ open: true, message: err.message }))
            return true
          }
        }
        return false
      },
      handlePaste: (view, event) => {
        const items = event.clipboardData?.items
        if (items) {
          for (const item of items) {
            if (item.type.startsWith('image/')) {
              event.preventDefault()
              const file = item.getAsFile()
              if (file) {
                uploadImage(file).then(url => {
                  const { schema } = view.state
                  const node = schema.nodes.image.create({ src: url })
                  const transaction = view.state.tr.replaceSelectionWith(node)
                  view.dispatch(transaction)
                }).catch(err => setAlertModal({ open: true, message: err.message }))
              }
              return true
            }
          }
        }
        return false
      },
    },
    onUpdate: ({ editor: ed }) => {
      // 슬래시 커맨드 감지
      const { from } = ed.state.selection
      const textBefore = ed.state.doc.textBetween(Math.max(0, from - 20), from, '\n')
      const slashMatch = textBefore.match(/\/([^\s/]*)$/)

      if (slashMatch) {
        if (!slashOpen) {
          setSlashPos(from - slashMatch[0].length)
          setSlashOpen(true)
        }
        setSlashFilter(slashMatch[1])
        setSlashIndex(0)
      } else if (slashOpen) {
        setSlashOpen(false)
        setSlashFilter('')
        setSlashPos(null)
      }
    },
  })
  editorRef.current = editor

  // 수정 모드: 기존 글 로드
  useEffect(() => {
    if (isEdit && editor && paramBoardId && clubInfo?.clubId) {
      api.get(`/clubs/${clubInfo.clubId}/posts/${postId}`)
        .then(data => {
          setTitle(data.title)
          setSelectedBoardIds(data.boards?.map(b => b.id) || [Number(paramBoardId)])
          setSelectedCategoryIds(data.semiCategories?.map(c => c.id) || [])
          setSelectedTeamIds(data.teams?.map(t => t.teamId) || [])
          editor.commands.setContent(parseEditorDocument(data.contentJson, normalizeChartMarkdown(data.content)))
        })
        .catch(e => handleApiError(e, '게시글 불러오기'))
        .finally(() => setLoadingPost(false))
    }
  }, [isEdit, editor, clubInfo?.clubId])

  const handleInsertImage = () => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/jpeg,image/png,image/gif,image/webp'
    input.onchange = async (e) => {
      const file = e.target.files?.[0]
      if (!file) return
      try {
        const url = await uploadImage(file)
        editor.chain().focus().setImage({ src: url }).run()
      } catch (err) {
        setAlertModal({ open: true, message: err.message })
      }
    }
    input.click()
  }

  const toggleBoard = (boardId) => {
    setSelectedBoardIds(prev =>
      prev.includes(boardId) ? prev.filter(id => id !== boardId) : [...prev, boardId]
    )
  }

  const toggleCategory = (catId) => {
    setSelectedCategoryIds(prev =>
      prev.includes(catId) ? prev.filter(id => id !== catId) : [...prev, catId]
    )
  }

  const toggleTeam = (teamId) => {
    setSelectedTeamIds(prev =>
      prev.includes(teamId) ? prev.filter(id => id !== teamId) : [...prev, teamId]
    )
  }

  const handleSave = async () => {
    if (!title.trim()) return setAlertModal({ open: true, message: '제목을 입력하세요' })
    if (!editor || editor.isEmpty) return setAlertModal({ open: true, message: '내용을 입력하세요' })
    if (selectedBoardIds.length === 0) return setAlertModal({ open: true, message: '게시판을 선택하세요' })
    if (selectedCategoryIds.length === 0) return setAlertModal({ open: true, message: '태그를 최소 1개 선택하세요' })

    setSaving(true)
    try {
      // markdown으로 저장
      const markdownContent = normalizeChartMarkdown(editor.storage.markdown.getMarkdown())
      const body = {
        title: title.trim(),
        content: markdownContent,
        contentJson: serializeEditorDocument(editor),
        boardIds: selectedBoardIds,
        semiCategoryIds: selectedCategoryIds,
        teamIds: selectedTeamIds,
      }

      if (isEdit) {
        await api.put(`/clubs/${clubInfo.clubId}/posts/${postId}`, body)
        addToast({ type: 'SUCCESS', title: '글이 수정되었습니다' })
        navigate(`/club/boards?board=${selectedBoardIds[0]}&post=${postId}`, { replace: true })
      } else {
        const created = await api.post(`/clubs/${clubInfo.clubId}/posts`, body)
        addToast({ type: 'SUCCESS', title: '글이 발행되었습니다' })
        navigate(`/club/boards?board=${selectedBoardIds[0]}&post=${created.id}`, { replace: true })
      }
    } catch (err) {
      setAlertModal({ open: true, message: err.message })
    } finally {
      setSaving(false)
    }
  }

  // 플로팅 툴바 (텍스트 선택 시)
  const [bubbleStyle, setBubbleStyle] = useState(null)
  useEffect(() => {
    if (!editor) return
    const handleSelectionUpdate = () => {
      const { from, to } = editor.state.selection
      if (from === to || !editor.view.hasFocus()) {
        setBubbleStyle(null)
        return
      }
      const coords = editor.view.coordsAtPos(from)
      const endCoords = editor.view.coordsAtPos(to)
      const editorRect = editor.view.dom.closest('.editor-wrapper')?.getBoundingClientRect()
      if (editorRect) {
        const midX = (coords.left + endCoords.left) / 2 - editorRect.left
        setBubbleStyle({ top: coords.top - editorRect.top - 44, left: Math.max(0, midX - 120) })
      }
    }
    const handleBlur = () => setBubbleStyle(null)
    editor.on('selectionUpdate', handleSelectionUpdate)
    editor.on('blur', handleBlur)
    return () => {
      editor.off('selectionUpdate', handleSelectionUpdate)
      editor.off('blur', handleBlur)
    }
  }, [editor])

  // 슬래시 메뉴 위치 계산
  useEffect(() => {
    if (slashOpen && editor) {
      const coords = editor.view.coordsAtPos(editor.state.selection.from)
      if (slashRef.current) {
        const editorRect = editor.view.dom.closest('.editor-wrapper')?.getBoundingClientRect()
        if (editorRect) {
          slashRef.current.style.top = `${coords.bottom - editorRect.top + 4}px`
          slashRef.current.style.left = `${coords.left - editorRect.left}px`
        }
      }
    }
  }, [slashOpen, slashFilter, editor])

  if (loadingPost) {
    return (
      <div className="py-12 text-center">
        <div className="animate-spin w-6 h-6 border-3 border-blue-500 border-t-transparent rounded-full mx-auto" />
      </div>
    )
  }

  return (
    <div className="space-y-4 max-w-4xl mx-auto p-6 animate-fade-in-up">
      {/* 뒤로가기 */}
      <button
        onClick={() => navigate('/club/boards')}
        className="text-xs text-slate-400 hover:text-slate-600 cursor-pointer"
      >
        &larr; 게시판으로
      </button>

      {/* 제목 */}
      <div>
        <input
          type="text"
          value={title}
          onChange={e => setTitle(e.target.value)}
          placeholder="제목을 입력하세요"
          className={`w-full px-5 py-4 bg-white rounded-2xl border text-lg font-bold text-slate-900 focus:outline-none focus:ring-2 ${
            title.length > 200 ? 'border-red-300 focus:ring-red-400' : 'border-slate-100 focus:ring-blue-500/20 focus:border-blue-500'
          }`}
        />
        {title.length > 200 && (
          <p className="text-xs text-red-500 mt-1 px-1">{title.length}/200자 — 최대 길이를 초과했습니다</p>
        )}
      </div>

      {/* 게시판 선택 + 태그 선택 */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4 space-y-3">
        <div className="flex items-start gap-2">
          <span className="text-xs text-slate-500 shrink-0 w-14 mt-1">게시판:</span>
          <div className="flex flex-wrap gap-2">
            {boards.filter(b => b.canWrite).map(b => (
              <button
                key={b.id}
                onClick={() => !isEdit && toggleBoard(b.id)}
                disabled={isEdit}
                className={`px-3 py-1 text-xs rounded-full border transition-colors ${isEdit ? 'opacity-60' : 'cursor-pointer'} ${
                  selectedBoardIds.includes(b.id)
                    ? 'bg-indigo-500 text-white border-indigo-500'
                    : 'bg-white text-slate-600 border-slate-200 hover:border-indigo-300'
                }`}
              >
                {b.name}
              </button>
            ))}
          </div>
        </div>
        {selectedBoardIds.length === 0 && (
          <p className="text-xs text-red-400 ml-16">게시판을 최소 1개 선택해야 합니다</p>
        )}

        {categories.length > 0 && (
          <div className="flex items-start gap-2">
            <span className="text-xs text-slate-500 shrink-0 w-14 mt-1">태그:</span>
            <div className="flex flex-wrap gap-2">
              {categories.map(cat => (
                <button
                  key={cat.id}
                  onClick={() => toggleCategory(cat.id)}
                  className={`px-3 py-1 text-xs rounded-full border transition-colors cursor-pointer ${
                    selectedCategoryIds.includes(cat.id)
                      ? 'bg-primary text-white border-blue-500'
                      : 'bg-white text-slate-600 border-slate-200 hover:border-blue-300'
                  }`}
                >
                  {cat.name}
                </button>
              ))}
            </div>
          </div>
        )}
        {categories.length > 0 && selectedCategoryIds.length === 0 && (
          <p className="text-xs text-red-400 ml-16">태그를 최소 1개 선택해야 합니다</p>
        )}

        {myTeams.length > 0 && (
          <div className="flex items-start gap-2">
            <span className="text-xs text-slate-500 shrink-0 w-14 mt-1">팀:</span>
            <div className="flex flex-wrap gap-2">
              {myTeams.map(t => (
                <button
                  key={t.teamId}
                  onClick={() => toggleTeam(t.teamId)}
                  className={`px-3 py-1 text-xs rounded-full border transition-colors cursor-pointer ${
                    selectedTeamIds.includes(t.teamId)
                      ? 'bg-emerald-500 text-white border-emerald-500'
                      : 'bg-white text-slate-600 border-slate-200 hover:border-emerald-300'
                  }`}
                >
                  {t.teamName}
                </button>
              ))}
              {selectedTeamIds.length === 0 && (
                <span className="text-xs text-slate-400 mt-0.5">선택하지 않으면 무소속</span>
              )}
            </div>
          </div>
        )}
      </div>

      {/* 에디터 */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm min-h-[400px] relative editor-wrapper">
        {/* 플로팅 툴바 — 텍스트 선택 시 */}
        {editor && bubbleStyle && (
          <div
            className="absolute z-50 flex items-center gap-0.5 bg-white rounded-lg shadow-lg border border-slate-200 p-1"
            style={{ top: bubbleStyle.top, left: bubbleStyle.left }}
          >
            <BubbleButton onClick={() => editor.chain().focus().toggleBold().run()} active={editor.isActive('bold')} label="B" bold />
            <BubbleButton onClick={() => editor.chain().focus().toggleItalic().run()} active={editor.isActive('italic')} label="I" italic />
            <BubbleButton onClick={() => editor.chain().focus().toggleStrike().run()} active={editor.isActive('strike')} label="S" strike />
            <BubbleButton onClick={() => editor.chain().focus().toggleCode().run()} active={editor.isActive('code')} label="<>" />
            <div className="w-px h-5 bg-slate-200 mx-0.5" />
            <BubbleButton onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()} active={editor.isActive('heading', { level: 2 })} label="H2" />
            <BubbleButton onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()} active={editor.isActive('heading', { level: 3 })} label="H3" />
            <div className="w-px h-5 bg-slate-200 mx-0.5" />
            <BubbleButton onClick={() => editor.chain().focus().toggleBulletList().run()} active={editor.isActive('bulletList')} label="*" />
            <BubbleButton onClick={() => editor.chain().focus().toggleOrderedList().run()} active={editor.isActive('orderedList')} label="1." />
            <BubbleButton onClick={() => editor.chain().focus().toggleBlockquote().run()} active={editor.isActive('blockquote')} label=">" />
          </div>
        )}

        <EditorContent editor={editor} />

        {/* 슬래시 커맨드 메뉴 */}
        {slashOpen && filteredSlashCommands.length > 0 && (
          <div
            ref={slashRef}
            className="absolute z-50 bg-white rounded-lg shadow-lg border border-slate-200 py-1 w-52"
          >
            {filteredSlashCommands.map((cmd, i) => (
              <button
                key={cmd.label}
                onMouseDown={(e) => {
                  e.preventDefault()
                  executeSlashCommand(cmd)
                }}
                className={`w-full text-left px-3 py-2 text-sm flex items-center gap-3 ${
                  i === slashIndex ? 'bg-blue-50 text-blue-700' : 'text-slate-700 hover:bg-slate-50'
                }`}
              >
                <span className="w-6 text-center text-base shrink-0">{cmd.icon}</span>
                <span>{cmd.label}</span>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* 저장 버튼 */}
      <div className="flex justify-end gap-3">
        <button
          onClick={() => navigate('/club/boards')}
          className="px-6 py-2.5 text-sm text-slate-500 hover:text-slate-700 cursor-pointer"
        >
          취소
        </button>
        <button
          onClick={handleSave}
          disabled={saving || title.length > 200}
          className="px-6 py-2.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover disabled:opacity-50 transition-colors cursor-pointer"
        >
          {saving ? '저장 중...' : isEdit ? '수정' : '게시'}
        </button>
      </div>

      {alertModal.open && (
        <ConfirmModal title="알림" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
      )}
    </div>
  )
}

function BubbleButton({ onClick, active, label, bold, italic, strike }) {
  return (
    <button
      onMouseDown={(e) => { e.preventDefault(); onClick() }}
      className={`px-2 py-1 text-xs rounded transition-colors ${
        active ? 'bg-blue-100 text-blue-700' : 'text-slate-600 hover:bg-slate-100'
      } ${bold ? 'font-bold' : ''} ${italic ? 'italic' : ''} ${strike ? 'line-through' : ''}`}
    >
      {label}
    </button>
  )
}
