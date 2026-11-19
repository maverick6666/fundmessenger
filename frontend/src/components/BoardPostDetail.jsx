import { useEffect, useState, useCallback, useRef, useMemo } from 'react'
import { createPortal } from 'react-dom'
import { useOutletContext } from 'react-router-dom'
import { useEditor, EditorContent } from '@tiptap/react'
import { Share2 } from 'lucide-react'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import Placeholder from '@tiptap/extension-placeholder'
import { Table, TableRow, TableCell, TableHeader } from '@tiptap/extension-table'
import { Markdown } from 'tiptap-markdown'
import ChartCodeBlock from './ChartNode'
import { Document, Page, pdfjs } from 'react-pdf'
import 'react-pdf/dist/Page/AnnotationLayer.css'
import 'react-pdf/dist/Page/TextLayer.css'
import api from '../lib/api'
import { formatTime } from '../lib/format'
import AiBadge from './AiBadge'
import BoardCommentSection from './BoardCommentSection'
import ConfirmModal from './ConfirmModal'
import handleApiError from '../lib/handleApiError'
import { PostDetailSkeleton, PostSkeleton } from './SkeletonRow'
import { API_URL, BASE_URL } from '../lib/api'
import { useGuardedSearchParams, useUnsavedChanges, useUnsavedChangesGuard } from './UnsavedChangesProvider'
import useAiJobStore from '../store/aiJobStore'
import useMessagingStore from '../store/messagingStore'
import { normalizeChartMarkdown } from '../lib/chartMarkdown'
import { parseEditorDocument, serializeEditorDocument } from '../lib/editorDocument'

pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString()

const PDF_OPTIONS = {
  cMapUrl: `https://unpkg.com/pdfjs-dist@${pdfjs.version}/cmaps/`,
  cMapPacked: true,
  standardFontDataUrl: `https://unpkg.com/pdfjs-dist@${pdfjs.version}/standard_fonts/`,
}

const API_BASE = BASE_URL

export default function BoardPostDetail({
  postId,
  onBack,
  isNewPost,
  initialBoardId,
  initialAiTransfer = null,
  selection = null,
}) {
  const { clubInfo } = useOutletContext()
  const [searchParams, setSearchParams] = useGuardedSearchParams()
  const { confirmIfDirty, runWithoutPrompt } = useUnsavedChanges()
  const consumeEditorTransfer = useAiJobStore((s) => s.consumeEditorTransfer)
  const openContentShare = useMessagingStore((s) => s.openContentShare)
  const [post, setPost] = useState(null)
  const [loading, setLoading] = useState(!isNewPost)
  const [deleteConfirm, setDeleteConfirm] = useState(false)
  const [showPdf, setShowPdf] = useState(false)
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })
  const [relatedPosts, setRelatedPosts] = useState([])
  const normalizeEditorContent = useCallback((content) => normalizeChartMarkdown(content || ''), [])
  const parseStoredDocument = useCallback(
    (contentJson, fallbackMarkdown = '') => parseEditorDocument(contentJson, normalizeEditorContent(fallbackMarkdown)),
    [normalizeEditorContent],
  )

  // PDF 뷰어 상태 (DriveFileDetail과 동일)
  const [numPages, setNumPages] = useState(null)
  const [currentPage, setCurrentPage] = useState(1)
  const [zoomMultiplier, setZoomMultiplier] = useState(1)
  const [pageSizes, setPageSizes] = useState([])
  const [viewerSize, setViewerSize] = useState({ width: 0, height: 0 })
  const [pdfError, setPdfError] = useState(null)
  const [thumbnailsOpen, setThumbnailsOpen] = useState(false)
  const pdfViewerRef = useRef(null)
  const thumbListRef = useRef(null)
  const wheelCooldown = useRef(false)

  // 편집 모드 상태
  const [isEditing, setIsEditing] = useState(!!isNewPost)
  const [editTitle, setEditTitle] = useState(() => initialAiTransfer?.title || '')
  const [editorInitialContent, setEditorInitialContent] = useState(() => parseStoredDocument(null, initialAiTransfer?.markdown || ''))
  const [isMarkdownMode, setIsMarkdownMode] = useState(false)
  const [markdownDraft, setMarkdownDraft] = useState(() => normalizeEditorContent(initialAiTransfer?.markdown || ''))
  const [saving, setSaving] = useState(false)
  const unsavedConfirm = null
  useUnsavedChangesGuard(isEditing && !saving)

  // 라우트 이동 차단 (편집 중)
  // 브라우저 탭 닫기/새로고침 차단
  // 게시판 + 태그 선택 (편집 모드)
  const [boards, setBoards] = useState([])
  const [selectedBoardIds, setSelectedBoardIds] = useState(() => {
    if (initialBoardId) return [initialBoardId]
    if (initialAiTransfer?.boardId) return [initialAiTransfer.boardId]
    return []
  })
  const [categories, setCategories] = useState([])
  const [selectedCategoryIds, setSelectedCategoryIds] = useState(() => initialAiTransfer?.semiCategoryIds || [])
  const aiSeedAppliedRef = useRef(false)

  // 슬래시 커맨드
  const [slashOpen, setSlashOpen] = useState(false)
  const [slashPos, setSlashPos] = useState(null)
  const [slashFilter, setSlashFilter] = useState('')
  const [slashIndex, setSlashIndex] = useState(0)
  const [slashMenuStyle, setSlashMenuStyle] = useState(null)
  const editorRef = useRef(null)

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
    const from = slashPos
    const to = ed.state.selection.from
    ed.chain().focus().deleteRange({ from, to }).run()
    cmd.action(ed)
    setSlashOpen(false)
    setSlashFilter('')
    setSlashPos(null)
  }, [slashPos])

  const updateSlashMenuPosition = useCallback(() => {
    const currentEditor = editorRef.current
    if (!slashOpen || !currentEditor) {
      setSlashMenuStyle(null)
      return
    }

    const coords = currentEditor.view.coordsAtPos(currentEditor.state.selection.from)
    const menuWidth = 208
    const rowHeight = 40
    const verticalPadding = 8
    const estimatedHeight = Math.min(filteredSlashCommands.length, 6) * rowHeight + verticalPadding * 2

    let left = Math.max(16, Math.min(coords.left, window.innerWidth - menuWidth - 16))
    let top = coords.bottom + 8

    if (top + estimatedHeight > window.innerHeight - 16) {
      top = Math.max(16, coords.top - estimatedHeight - 8)
    }

    setSlashMenuStyle({
      position: 'fixed',
      top: `${top}px`,
      left: `${left}px`,
      width: `${menuWidth}px`,
      maxHeight: `${Math.max(120, Math.min(window.innerHeight - 32, estimatedHeight))}px`,
    })
  }, [filteredSlashCommands.length, slashOpen])

  // TipTap 에디터
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
      Markdown.configure({ html: true, transformPastedText: true, transformCopiedText: true }),
    ],
    content: editorInitialContent,
    editable: isNewPost,
    editorProps: {
      attributes: { class: 'prose prose-sm max-w-none focus:outline-none' },
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

  // 게시판 + 카테고리 목록 (편집 모드용)
  useEffect(() => {
    if (!clubInfo?.clubId || !isEditing) return
    Promise.all([
      api.get(`/clubs/${clubInfo.clubId}/boards`),
      api.get(`/clubs/${clubInfo.clubId}/semi-categories`)
    ]).then(([boardData, catData]) => {
      const editableBoards = (boardData || []).filter((board) => board.boardType !== 'AI')
      const editableCategories = (catData || []).filter((category) => category.name !== 'AI')
      setBoards(editableBoards)
      setCategories(editableCategories)
      // 새 글 + 게시판 미선택 시 첫 번째 쓰기 가능 게시판 선택
      if (isNewPost && selectedBoardIds.length === 0 && editableBoards.length) {
        const writable = editableBoards.find(b => b.canWrite)
        if (writable) setSelectedBoardIds([writable.id])
      }
    }).catch(e => handleApiError(e, '게시판 정보 조회'))
  }, [clubInfo?.clubId, isEditing])

  // 글 상세 조회 (뷰 모드)
  useEffect(() => {
    if (!clubInfo?.clubId || !postId || isNewPost) return
    setLoading(true)
    setShowPdf(false)
    setCurrentPage(1)
    setNumPages(null)
    setPageSizes([])
    setZoomMultiplier(1)
    setPdfError(null)
    setThumbnailsOpen(false)

    api.get(`/clubs/${clubInfo.clubId}/posts/${postId}`)
      .then(detail => {
        setPost(detail)
        const initialContent = parseStoredDocument(detail.contentJson, detail.content)
        setEditorInitialContent(initialContent)
        setMarkdownDraft(normalizeEditorContent(detail.content))
        if (editor && detail.content) {
          editor.commands.setContent(initialContent)
        }
        // selection이 있으면 사이드바 필터 기준, 없으면 글의 첫 번째 게시판 기준
        let relUrl
        if (selection?.type === 'board') {
          relUrl = `/clubs/${clubInfo.clubId}/boards/${selection.id}/posts?size=20`
        } else if (selection?.type === 'team') {
          relUrl = `/clubs/${clubInfo.clubId}/posts?teamId=${selection.id}&size=20`
        } else if (selection?.type === 'tag') {
          relUrl = `/clubs/${clubInfo.clubId}/posts?tagId=${selection.id}&size=20`
        } else {
          const firstBoardId = detail.boards?.[0]?.id
          if (!firstBoardId) return { content: [] }
          relUrl = `/clubs/${clubInfo.clubId}/boards/${firstBoardId}/posts?size=20`
        }
        return api.get(relUrl)
      })
      .then(data => {
        setRelatedPosts(data.content || [])
      })
      .catch(e => handleApiError(e, '게시글 상세 조회'))
      .finally(() => setLoading(false))
  }, [clubInfo?.clubId, editor, normalizeEditorContent, parseStoredDocument, postId, selection?.type, selection?.id])

  // editor 로드 후 content 설정
  useEffect(() => {
    if (editor && post?.content && !isEditing) {
      const initialContent = parseStoredDocument(post.contentJson, post.content)
      setEditorInitialContent(initialContent)
      setMarkdownDraft(normalizeEditorContent(post.content))
      editor.commands.setContent(initialContent)
    }
  }, [editor, isEditing, normalizeEditorContent, parseStoredDocument, post?.content, post?.contentJson])

  useEffect(() => {
    if (!isNewPost || !editor || !initialAiTransfer?.markdown || aiSeedAppliedRef.current) return

    aiSeedAppliedRef.current = true
    const normalizedContent = normalizeEditorContent(initialAiTransfer.markdown)
    setEditorInitialContent(normalizedContent)
    setMarkdownDraft(normalizedContent)
    editor.commands.setContent(normalizedContent)
  }, [editor, initialAiTransfer, isNewPost, normalizeEditorContent])

  // AI 미리보기 -> 에디터 전달 payload 적용
  useEffect(() => {
    if (!isNewPost || !editor || aiSeedAppliedRef.current) return

    const hasAiTransfer = searchParams.get('aiTransfer') === '1'
    const legacyAiContent = searchParams.get('aiContent')
    const legacyAiDriveFileId = searchParams.get('aiDriveFileId')

    if (!hasAiTransfer && !legacyAiContent) return

    const transfer = hasAiTransfer
      ? consumeEditorTransfer()
      : {
          markdown: legacyAiContent,
          driveFileId: legacyAiDriveFileId ? Number(legacyAiDriveFileId) : null,
          semiCategoryIds: [],
          boardId: initialBoardId,
        }

    aiSeedAppliedRef.current = true

    if (transfer?.markdown) {
      if (transfer.title) {
        setEditTitle(transfer.title)
      }
      const normalizedContent = normalizeEditorContent(transfer.markdown)
      setEditorInitialContent(normalizedContent)
      setMarkdownDraft(normalizedContent)
      editor.commands.setContent(normalizedContent)
    }

    if (transfer?.boardId && !initialBoardId) {
      setSelectedBoardIds((prev) => (prev.length > 0 ? prev : [transfer.boardId]))
    }

    if (Array.isArray(transfer?.semiCategoryIds) && transfer.semiCategoryIds.length > 0) {
      setSelectedCategoryIds(transfer.semiCategoryIds)
    }

    runWithoutPrompt(() =>
      setSearchParams((prev) => {
        const params = Object.fromEntries(prev)
        delete params.aiTransfer
        delete params.aiContent
        delete params.aiDriveFileId
        return params
      })
    )
  }, [consumeEditorTransfer, editor, initialBoardId, isNewPost, normalizeEditorContent, runWithoutPrompt, searchParams, setSearchParams])

  // 추천 토글
  const handleToggleLike = async () => {
    if (!clubInfo?.clubId || !post?.id) return
    try {
      const result = await api.post(`/clubs/${clubInfo.clubId}/posts/${post.id}/like`)
      setPost(prev => ({ ...prev, liked: result.liked, likeCount: result.likeCount }))
    } catch (err) {
      handleApiError(err, '추천 토글')
    }
  }

  const isMyPost = post?.authorMembershipId === clubInfo?.membershipId
  const isAdmin = clubInfo?.isExecutive
  const canEdit = isMyPost || isAdmin

  const runWithUnsavedChangesPrompt = useCallback((action) => {
    confirmIfDirty(action)
  }, [confirmIfDirty])

  const applyMarkdownDraftToEditor = useCallback(() => {
    if (!editor) return
    const normalizedMarkdown = normalizeEditorContent(markdownDraft)
    editor.commands.setContent(normalizedMarkdown)
    setEditorInitialContent(normalizedMarkdown)
    setMarkdownDraft(normalizedMarkdown)
    setIsMarkdownMode(false)
  }, [editor, markdownDraft, normalizeEditorContent])

  // 편집 모드 진입
  const enterEditMode = () => {
    if (!editor || !post) return
    setEditTitle(post.title)
    setSelectedBoardIds(post.boards?.map(b => b.id) || [])
    setSelectedCategoryIds(post.semiCategories?.map(c => c.id) || [])
    setMarkdownDraft(normalizeEditorContent(editor.storage.markdown.getMarkdown()))
    setIsMarkdownMode(false)
    setIsEditing(true)
    editor.setEditable(true)
  }

  // 편집 취소
  const cancelEdit = () => {
    runWithUnsavedChangesPrompt(() => {
      if (isNewPost) {
        onBack()
        return
      }
      setIsEditing(false)
      setIsMarkdownMode(false)
      setMarkdownDraft(normalizeEditorContent(editor?.storage?.markdown?.getMarkdown?.() || post?.content))
      setSlashOpen(false)
      if (editor && post?.content) {
        editor.setEditable(false)
        editor.commands.setContent(parseStoredDocument(post.contentJson, post.content))
      }
    })
  }

  // 저장 (게시/수정)
  const handleSave = async () => {
    if (!editTitle.trim()) return setAlertModal({ open: true, message: '제목을 입력하세요' })
    if (!editor) return setAlertModal({ open: true, message: '내용을 입력하세요' })
    if (isMarkdownMode) {
      const normalizedMarkdown = normalizeEditorContent(markdownDraft)
      if (!normalizedMarkdown.trim()) {
        return setAlertModal({ open: true, message: '내용을 입력하세요' })
      }
      editor.commands.setContent(normalizedMarkdown)
      setEditorInitialContent(normalizedMarkdown)
      setMarkdownDraft(normalizedMarkdown)
      setIsMarkdownMode(false)
    } else if (editor.isEmpty) {
      return setAlertModal({ open: true, message: '내용을 입력하세요' })
    }
    if (selectedBoardIds.length === 0) return setAlertModal({ open: true, message: '게시판을 최소 1개 선택하세요' })
    if (selectedCategoryIds.length === 0) return setAlertModal({ open: true, message: '태그를 최소 1개 선택하세요' })

    setSaving(true)
    try {
      const markdownContent = normalizeEditorContent(editor.storage.markdown.getMarkdown())
      const contentJson = serializeEditorDocument(editor)
      const body = {
        title: editTitle.trim(),
        content: markdownContent,
        contentJson,
        aiJobId: isNewPost ? (initialAiTransfer?.jobId ?? null) : null,
        boardIds: selectedBoardIds,
        semiCategoryIds: selectedCategoryIds,
      }

      if (isNewPost) {
        const created = await api.post(`/clubs/${clubInfo.clubId}/posts`, body)
        // 새 글 생성 후 해당 글 상세로 전환
        runWithoutPrompt(() =>
          setSearchParams(prev => {
            const params = Object.fromEntries(prev)
            params.post = created.id
            params.board = selectedBoardIds[0]
            delete params.writing
            return params
          })
        )
      } else {
        await api.put(`/clubs/${clubInfo.clubId}/posts/${post.id}`, body)
        // 수정 후 뷰 모드 전환 — 글 다시 로드
        runWithoutPrompt(() =>
          setSearchParams(prev => {
            const params = Object.fromEntries(prev)
            params.post = post.id
            params.board = selectedBoardIds[0] || params.board
            params.rev = Date.now().toString()
            delete params.writing
            return params
          })
        )
      }
    } catch (err) {
      setAlertModal({ open: true, message: err.message })
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    try {
      await api.delete(`/clubs/${clubInfo.clubId}/posts/${post.id}`)
      onBack()
    } catch (e) {
      setAlertModal({ open: true, message: e.message })
    }
  }

  const handleRelatedClick = (relatedPostId) => {
    runWithUnsavedChangesPrompt(() => {
      setSearchParams(prev => {
        const params = Object.fromEntries(prev)
        params.post = relatedPostId
        return params
      })
    })
  }

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

  const toggleCategory = (catId) => {
    setSelectedCategoryIds(prev =>
      prev.includes(catId) ? prev.filter(id => id !== catId) : [...prev, catId]
    )
  }

  // ── PDF 뷰어 로직 ──
  const pdfUrl = post?.linkedDriveFileUrl
    || (post?.linkedDriveFileStoredName ? `${BASE_URL}/uploads/${post.linkedDriveFileStoredName}` : null)

  // 휠 이벤트 → 브라우저 스크롤 차단
  useEffect(() => {
    const el = pdfViewerRef.current
    if (!el || !showPdf) return
    const prevent = (e) => { e.preventDefault() }
    el.addEventListener('wheel', prevent, { passive: false })
    return () => el.removeEventListener('wheel', prevent)
  }, [showPdf])

  // ResizeObserver — 뷰포트 크기 추적
  useEffect(() => {
    if (!pdfViewerRef.current || !showPdf) return
    const observer = new ResizeObserver(entries => {
      const entry = entries[0]
      if (entry) setViewerSize({ width: entry.contentRect.width, height: entry.contentRect.height })
    })
    observer.observe(pdfViewerRef.current)
    return () => observer.disconnect()
  }, [showPdf])

  // 썸네일 자동 스크롤
  useEffect(() => {
    if (!thumbnailsOpen || !thumbListRef.current) return
    const activeThumb = thumbListRef.current.querySelector(`[data-page="${currentPage}"]`)
    if (activeThumb) activeThumb.scrollIntoView({ block: 'nearest' })
  }, [currentPage, thumbnailsOpen])

  const onDocumentLoadSuccess = async (pdf) => {
    setCurrentPage(1)
    setNumPages(pdf.numPages)
    const sizes = []
    for (let i = 1; i <= pdf.numPages; i++) {
      const page = await pdf.getPage(i)
      const vp = page.getViewport({ scale: 1 })
      sizes.push({ width: vp.width, height: vp.height })
    }
    setPageSizes(sizes)
  }

  const prevPage = () => setCurrentPage(p => Math.max(1, p - 1))
  const nextPage = () => setCurrentPage(p => Math.min(numPages || 1, p + 1))

  const handleViewerWheel = (e) => {
    if (wheelCooldown.current) return
    if (zoomMultiplier > 1.05) return
    if (e.deltaY > 0) nextPage()
    else if (e.deltaY < 0) prevPage()
    wheelCooldown.current = true
    setTimeout(() => { wheelCooldown.current = false }, 200)
  }

  const zoomIn = () => setZoomMultiplier(z => Math.min(z + 0.15, 4))
  const zoomOut = () => setZoomMultiplier(z => Math.max(z - 0.15, 0.3))
  const zoomReset = () => setZoomMultiplier(1)

  const currentPageSize = pageSizes[currentPage - 1] || { width: 595, height: 842 }
  const pdfBaseWidth = currentPageSize.width
  const pdfBaseHeight = currentPageSize.height
  const pdfPadding = 40
  let fitScale = 1
  if (viewerSize.width > 0 && viewerSize.height > 0) {
    fitScale = pdfBaseWidth >= pdfBaseHeight
      ? (viewerSize.width - pdfPadding) / pdfBaseWidth
      : (viewerSize.height - pdfPadding) / pdfBaseHeight
  }
  const renderWidth = Math.max(pdfBaseWidth * fitScale * zoomMultiplier, 200)

  // PDF 비율에 따른 동적 뷰포트 높이 (가로 PDF → 낮게, 세로 PDF → 높게)
  const pdfAspectRatio = pdfBaseHeight / pdfBaseWidth
  const pdfViewerVh = pageSizes.length > 0
    ? Math.min(Math.max(Math.round(pdfAspectRatio * 60), 40), 80)
    : 65

  // 플로팅 툴바 (텍스트 선택 시)
  const [bubbleStyle, setBubbleStyle] = useState(null)
  useEffect(() => {
    if (!editor) return
    const handleSelectionUpdate = () => {
      if (!editor.isEditable) { setBubbleStyle(null); return }
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

  // 슬래시 메뉴 위치 계산 (브라우저 viewport 기준)
  useEffect(() => {
    updateSlashMenuPosition()
  }, [slashFilter, slashOpen, updateSlashMenuPosition])

  useEffect(() => {
    if (!slashOpen) return undefined

    const handleWindowChange = () => updateSlashMenuPosition()
    window.addEventListener('resize', handleWindowChange)
    window.addEventListener('scroll', handleWindowChange, true)

    return () => {
      window.removeEventListener('resize', handleWindowChange)
      window.removeEventListener('scroll', handleWindowChange, true)
    }
  }, [slashOpen, updateSlashMenuPosition])

  if (loading) return <PostDetailSkeleton />
  if (!isNewPost && !post) return <div className="p-6 text-sm text-slate-400 bg-white rounded-2xl border border-slate-200 shadow-sm">글을 찾을 수 없습니다</div>

  // ── 통합 레이아웃 (편집/뷰 모드 공유) ──
  return (
    <div className="flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
      {/* 상단 액션 */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-slate-200">
        {isEditing ? (
          <button onClick={cancelEdit} className="text-sm text-slate-500 hover:text-slate-700 cursor-pointer">
            취소
          </button>
        ) : (
          <button onClick={() => runWithUnsavedChangesPrompt(onBack)} className="text-sm text-slate-500 hover:text-slate-700 cursor-pointer">
            ← 목록
          </button>
        )}
        <div className="flex items-center gap-2">
          {isEditing ? (
            <button
              onClick={handleSave}
              disabled={saving || editTitle.length > 200}
              className="px-4 py-1.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover disabled:opacity-50 transition-colors cursor-pointer"
            >
              {saving ? '저장 중...' : isNewPost ? '게시' : '수정'}
            </button>
          ) : (
            <>
              {post.linkedDriveFileId && post.linkedDriveFileStoredName && (
                <button
                  onClick={() => setShowPdf(prev => !prev)}
                  className={`px-3 py-1.5 text-xs rounded-lg border transition-colors ${
                    showPdf
                      ? 'bg-blue-50 text-blue-600 border-blue-200'
                      : 'bg-slate-100 text-slate-600 border-slate-200 hover:bg-slate-200'
                  }`}
                >
                  {showPdf ? '글 보기' : 'PDF 보기'}
                </button>
              )}
              {post?.id && (
                <button
                  onClick={() => openContentShare({ sharedPostId: post.id, sharedType: 'POST', title: post.title })}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg bg-white text-slate-600 border border-slate-200 hover:bg-slate-50 cursor-pointer"
                >
                  <Share2 className="h-3.5 w-3.5" />
                  채팅으로 보내기
                </button>
              )}
              {canEdit && (
                <>
                  <button
                    onClick={enterEditMode}
                    className="px-3 py-1.5 text-xs rounded-lg bg-slate-50 text-slate-600 border border-slate-200 hover:bg-slate-100 cursor-pointer"
                  >
                    수정
                  </button>
                  <button
                    onClick={() => setDeleteConfirm(true)}
                    className="px-3 py-1.5 text-xs rounded-lg bg-red-50 text-red-600 border border-red-200 hover:bg-red-100 cursor-pointer"
                  >
                    삭제
                  </button>
                </>
              )}
            </>
          )}
        </div>
      </div>

      {/* 글 헤더 — 편집 모드: 제목 input + 게시판/태그 선택, 뷰 모드: 정적 표시 */}
      {isEditing ? (
        <>
          <div className="px-6 pt-4 pb-2">
            <input
              type="text"
              value={editTitle}
              onChange={e => setEditTitle(e.target.value)}
              placeholder="제목을 입력하세요"
              className={`w-full text-xl font-bold text-slate-900 border-b pb-3 focus:outline-none ${
                editTitle.length > 200 ? 'border-red-300' : 'border-slate-200 focus:border-blue-400'
              }`}
            />
            {editTitle.length > 200 && (
              <p className="text-xs text-red-500 mt-1">{editTitle.length}/200자 — 최대 길이를 초과했습니다</p>
            )}
          </div>
          <div className="px-6 py-2 space-y-2">
            <div className="flex items-start gap-2">
              <span className="text-xs text-slate-500 shrink-0 w-14 mt-1">게시판:</span>
              <div className="flex flex-wrap gap-2">
                {boards.filter(b => b.canWrite).map(b => (
                  <button
                    key={b.id}
                    onClick={() => setSelectedBoardIds(prev =>
                      prev.includes(b.id) ? prev.filter(id => id !== b.id) : [...prev, b.id]
                    )}
                    className={`px-3 py-1 text-xs rounded-full border transition-colors cursor-pointer ${
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
          </div>
        </>
      ) : (
        <div className="px-6 md:px-8 py-5 md:py-6 border-b border-slate-100">
          <div className="flex items-center gap-2 mb-2">
            {post.semiCategories?.map(cat => (
              <span key={cat.id} className="px-2 py-0.5 text-xs rounded bg-blue-50 text-blue-600">
                {cat.name}
              </span>
            ))}
            <span className="text-xs text-slate-400">{post.boards?.map(b => b.name).join(', ')}</span>
          </div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold text-slate-900">{post.title}</h1>
            <AiBadge aiGenerated={post.aiGenerated} aiEdited={post.aiEdited} />
          </div>
          <div className="mt-2 text-xs text-slate-400 flex items-center gap-2">
            <span>{post.authorName}</span>
            <span>·</span>
            <span>{formatTime(post.createdAt)}</span>
            {post.updatedAt && <span className="text-slate-300">(수정됨)</span>}
            {post.likeCount > 0 && (
              <span className="text-rose-400 flex items-center gap-0.5">
                <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20"><path d="M3.172 5.172a4 4 0 015.656 0L10 6.343l1.172-1.171a4 4 0 115.656 5.656L10 17.657l-6.828-6.829a4 4 0 010-5.656z" /></svg>
                {post.likeCount}
              </span>
            )}
          </div>
        </div>
      )}

      {/* 본문 — 편집 모드: 툴바 + 에디터, 뷰 모드: 정적 본문 or PDF */}
      {isEditing ? (
        <>
          {editor && (
            <div className="flex items-center gap-0.5 px-6 py-2 border-b border-slate-100 bg-slate-50 flex-wrap">
              <ToolbarButton onClick={() => editor.chain().focus().toggleBold().run()} active={editor.isActive('bold')} label="B" bold />
              <ToolbarButton onClick={() => editor.chain().focus().toggleItalic().run()} active={editor.isActive('italic')} label="I" italic />
              <ToolbarButton onClick={() => editor.chain().focus().toggleStrike().run()} active={editor.isActive('strike')} label="S" strike />
              <div className="w-px h-5 bg-slate-200 mx-1" />
              <ToolbarButton onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()} active={editor.isActive('heading', { level: 2 })} label="H2" />
              <ToolbarButton onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()} active={editor.isActive('heading', { level: 3 })} label="H3" />
              <div className="w-px h-5 bg-slate-200 mx-1" />
              <ToolbarButton onClick={() => editor.chain().focus().toggleBulletList().run()} active={editor.isActive('bulletList')} label="•" />
              <ToolbarButton onClick={() => editor.chain().focus().toggleOrderedList().run()} active={editor.isActive('orderedList')} label="1." />
              <ToolbarButton onClick={() => editor.chain().focus().toggleBlockquote().run()} active={editor.isActive('blockquote')} label="❝" />
              <ToolbarButton onClick={() => editor.chain().focus().toggleCodeBlock().run()} active={editor.isActive('codeBlock')} label="</>" />
              <div className="w-px h-5 bg-slate-200 mx-1" />
              <ToolbarButton onClick={handleInsertImage} label="🖼" />
              <div className="ml-auto flex items-center gap-2">
                {isMarkdownMode ? (
                  <>
                    <button
                      onClick={applyMarkdownDraftToEditor}
                      className="px-2.5 py-1 text-xs rounded-md bg-primary text-white hover:bg-primary-hover cursor-pointer"
                    >
                      MD 적용
                    </button>
                    <button
                      onClick={() => {
                        setMarkdownDraft(editor.storage.markdown.getMarkdown())
                        setIsMarkdownMode(false)
                      }}
                      className="px-2.5 py-1 text-xs rounded-md border border-slate-200 text-slate-500 hover:bg-white cursor-pointer"
                    >
                      편집기로
                    </button>
                  </>
                ) : (
                  <button
                    onClick={() => {
                      setMarkdownDraft(editor.storage.markdown.getMarkdown())
                      setIsMarkdownMode(true)
                    }}
                    className="px-2.5 py-1 text-xs rounded-md border border-slate-200 text-slate-500 hover:bg-white cursor-pointer"
                  >
                    MD 원문
                  </button>
                )}
              </div>
            </div>
          )}
          <div className="px-6 py-4 min-h-[200px] relative editor-wrapper">
            {editor && bubbleStyle && !isMarkdownMode && (
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
              </div>
            )}
            {isMarkdownMode ? (
              <textarea
                value={markdownDraft}
                onChange={(e) => setMarkdownDraft(e.target.value)}
                className="w-full min-h-[320px] rounded-xl border border-slate-200 bg-slate-950 text-slate-50 text-sm leading-7 p-4 font-mono focus:outline-none focus:ring-2 focus:ring-blue-500/30"
                placeholder="여기에 markdown 원문을 직접 작성하거나 붙여넣을 수 있습니다."
              />
            ) : (
              <EditorContent editor={editor} />
            )}
          </div>
        </>
      ) : showPdf ? (
        <div className="flex flex-col" style={{ height: `${pdfViewerVh}vh` }}>
          {/* PDF 툴바 */}
          <div className="flex items-center justify-between px-4 py-2 bg-slate-50 border-b border-slate-100 shrink-0">
            <div className="flex items-center gap-2">
              <button
                onClick={() => setThumbnailsOpen(prev => !prev)}
                className={`px-2 py-1 text-xs rounded cursor-pointer ${thumbnailsOpen ? 'bg-blue-100 text-blue-600' : 'text-slate-500 hover:bg-slate-200'}`}
                title="페이지 목록"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M4 6h16M4 10h16M4 14h16M4 18h16" />
                </svg>
              </button>
              <div className="w-px h-4 bg-slate-200" />
              <button onClick={prevPage} disabled={currentPage <= 1}
                className="px-2 py-1 text-xs text-slate-500 hover:bg-slate-200 rounded disabled:opacity-30 cursor-pointer">&lt;</button>
              <span className="text-xs text-slate-600">{currentPage} / {numPages || '?'}</span>
              <button onClick={nextPage} disabled={currentPage >= (numPages || 1)}
                className="px-2 py-1 text-xs text-slate-500 hover:bg-slate-200 rounded disabled:opacity-30 cursor-pointer">&gt;</button>
            </div>
            <div className="flex items-center gap-1">
              <button onClick={zoomOut} className="px-2 py-1 text-xs text-slate-500 hover:bg-slate-200 rounded cursor-pointer">-</button>
              <button onClick={zoomReset} className="px-3 py-1 text-xs text-slate-600 hover:bg-slate-200 rounded cursor-pointer">
                {Math.round(zoomMultiplier * 100)}%
              </button>
              <button onClick={zoomIn} className="px-2 py-1 text-xs text-slate-500 hover:bg-slate-200 rounded cursor-pointer">+</button>
            </div>
          </div>

          {/* 썸네일 + PDF 렌더링 */}
          <div className="flex-1 flex min-h-0">
            {thumbnailsOpen && pdfUrl && numPages && (
              <div ref={thumbListRef} className="w-36 shrink-0 overflow-y-auto bg-slate-50 border-r border-slate-200 p-2 flex flex-col gap-2"
                onWheel={e => e.stopPropagation()}>
                {Array.from({ length: numPages }, (_, i) => i + 1).map(p => (
                  <button
                    key={p}
                    data-page={p}
                    onClick={() => setCurrentPage(p)}
                    className={`rounded overflow-hidden cursor-pointer ring-2 transition-all ${
                      p === currentPage ? 'ring-blue-500' : 'ring-transparent hover:ring-slate-300'
                    }`}
                  >
                    <Document file={pdfUrl} options={PDF_OPTIONS} loading="">
                      <Page pageNumber={p} width={120} renderTextLayer={false} renderAnnotationLayer={false} />
                    </Document>
                    <div className={`text-center text-xs py-0.5 ${p === currentPage ? 'bg-primary text-white' : 'bg-slate-100 text-slate-500'}`}>
                      {p}
                    </div>
                  </button>
                ))}
              </div>
            )}

            <div ref={pdfViewerRef} className="flex-1 overflow-auto bg-slate-100" onWheel={handleViewerWheel}>
              <div className="min-h-full flex items-center justify-center p-4"
                style={{ minHeight: renderWidth > 200 ? Math.round(pdfBaseHeight * fitScale * zoomMultiplier + pdfPadding) : undefined }}>
                {pdfUrl && (
                  <Document
                    file={pdfUrl}
                    options={PDF_OPTIONS}
                    onLoadSuccess={onDocumentLoadSuccess}
                    onLoadError={(err) => setPdfError(err.message)}
                    loading={<div className="p-8 text-center text-sm text-slate-400">PDF 로딩 중...</div>}
                  >
                    <div className="bg-white shadow-sm rounded relative" style={{ width: renderWidth }}>
                      <Page
                        pageNumber={currentPage}
                        width={renderWidth}
                        renderTextLayer={true}
                        renderAnnotationLayer={true}
                      />
                      <div className="absolute bottom-2 right-2 px-2 py-0.5 bg-black/50 text-white text-xs rounded">
                        {currentPage}
                      </div>
                    </div>
                  </Document>
                )}
                {pdfError && (
                  <div className="p-8 text-center text-sm text-red-500">PDF 로드 실패: {pdfError}</div>
                )}
              </div>
            </div>
          </div>

          {post.linkedDriveFileName && (
            <div className="px-4 py-2 bg-slate-50 border-t border-slate-100 shrink-0">
              <p className="text-xs text-slate-500">{post.linkedDriveFileName}</p>
            </div>
          )}
        </div>
      ) : (
        <div className="px-6 md:px-8 py-5 md:py-6 min-h-[200px]">
          <EditorContent editor={editor} />
        </div>
      )}

      {/* 좋아요 버튼 — 본문 아래 정중앙 (글이 있을 때만) */}
      {post && <div className="flex justify-center py-5 border-b border-slate-100">
        <button
          onClick={handleToggleLike}
          className={`flex items-center gap-2 px-6 py-2.5 rounded-full text-sm font-medium transition-all cursor-pointer ${
            post.liked
              ? 'bg-rose-50 text-rose-500 border border-rose-200'
              : 'bg-slate-50 text-slate-500 border border-slate-200 hover:bg-slate-100 hover:text-rose-400'
          }`}
        >
          <svg className="w-5 h-5" fill={post.liked ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth={post.liked ? 0 : 1.5} viewBox="0 0 20 20">
            <path d="M3.172 5.172a4 4 0 015.656 0L10 6.343l1.172-1.171a4 4 0 115.656 5.656L10 17.657l-6.828-6.829a4 4 0 010-5.656z" />
          </svg>
          <span>추천 {post.likeCount || 0}</span>
        </button>
      </div>}

      {/* 댓글 */}
      {post && <BoardCommentSection
        clubId={clubInfo.clubId}
        boardId={post.boards?.[0]?.id}
        postId={post.id}
        membershipId={clubInfo.membershipId}
        isExecutive={clubInfo.isExecutive}
      />}

      {/* 이전글/다음글 + 같은 게시판 글목록 (5개씩 페이지네이션) */}
      {post && relatedPosts.length > 0 && <RelatedPostsSection
        relatedPosts={relatedPosts}
        postId={postId}
        boardNames={selection?.name || post.boards?.map(b => b.name).join(', ') || '게시판'}
        onRelatedClick={handleRelatedClick}
      />}

      {deleteConfirm && post && (
        <ConfirmModal
          title="글 삭제"
          message={`"${post.title}"을(를) 삭제하시겠습니까?`}
          confirmLabel="삭제"
          onConfirm={handleDelete}
          onClose={() => setDeleteConfirm(false)}
          danger
        />
      )}

      {alertModal.open && (
        <ConfirmModal title="알림" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
      )}

      {typeof document !== 'undefined' && slashOpen && filteredSlashCommands.length > 0 && slashMenuStyle && createPortal(
        <div
          className="z-[120] bg-white rounded-lg shadow-lg border border-slate-200 py-1 overflow-y-auto"
          style={slashMenuStyle}
        >
          {filteredSlashCommands.map((cmd, i) => (
            <button
              key={cmd.label}
              onMouseDown={(e) => { e.preventDefault(); executeSlashCommand(cmd) }}
              className={`w-full text-left px-3 py-2 text-sm flex items-center gap-3 ${
                i === slashIndex ? 'bg-blue-50 text-blue-700' : 'text-slate-700 hover:bg-slate-50'
              }`}
            >
              <span className="w-6 text-center text-base shrink-0">{cmd.icon}</span>
              <span>{cmd.label}</span>
            </button>
          ))}
        </div>,
        document.body,
      )}

      {unsavedConfirm && (
        <ConfirmModal
          title="저장되지 않은 변경사항"
          message="작성 중인 내용이 저장되지 않습니다. 이동하시겠습니까?"
          confirmLabel="이동"
          onConfirm={unsavedConfirm.onConfirm}
          onClose={() => setUnsavedConfirm(null)}
          danger
        />
      )}
    </div>
  )
}

function ToolbarButton({ onClick, active, label, bold, italic, strike }) {
  return (
    <button
      onMouseDown={(e) => { e.preventDefault(); onClick() }}
      className={`px-2.5 py-1.5 text-xs rounded transition-colors cursor-pointer ${
        active ? 'bg-blue-100 text-blue-700' : 'text-slate-600 hover:bg-slate-200'
      } ${bold ? 'font-bold' : ''} ${italic ? 'italic' : ''} ${strike ? 'line-through' : ''}`}
    >
      {label}
    </button>
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

const RELATED_PAGE_SIZE = 5

function RelatedPostsSection({ relatedPosts, postId, boardNames, onRelatedClick }) {
  const [relPage, setRelPage] = useState(0)

  const currentIdx = relatedPosts.findIndex(p => p.id === Number(postId))
  const prevPost = currentIdx > 0 ? relatedPosts[currentIdx - 1] : null
  const nextPost = currentIdx >= 0 && currentIdx < relatedPosts.length - 1 ? relatedPosts[currentIdx + 1] : null

  const totalRelPages = Math.ceil(relatedPosts.length / RELATED_PAGE_SIZE)
  const pagedPosts = relatedPosts.slice(relPage * RELATED_PAGE_SIZE, (relPage + 1) * RELATED_PAGE_SIZE)

  // 현재 글이 포함된 페이지로 자동 이동
  useEffect(() => {
    if (currentIdx >= 0) {
      setRelPage(Math.floor(currentIdx / RELATED_PAGE_SIZE))
    }
  }, [currentIdx])

  return (
    <div className="border-t border-slate-200">
      <div className="px-6 py-3 border-b border-slate-100 flex items-center justify-between">
        {prevPost ? (
          <button
            onClick={() => onRelatedClick(prevPost.id)}
            className="flex items-center gap-2 text-sm text-slate-600 hover:text-blue-600 transition-colors min-w-0"
          >
            <span className="text-xs text-slate-400 shrink-0">▲ 이전글</span>
            <span className="truncate">{prevPost.title}</span>
          </button>
        ) : <div />}
        {nextPost ? (
          <button
            onClick={() => onRelatedClick(nextPost.id)}
            className="flex items-center gap-2 text-sm text-slate-600 hover:text-blue-600 transition-colors min-w-0 text-right"
          >
            <span className="truncate">{nextPost.title}</span>
            <span className="text-xs text-slate-400 shrink-0">다음글 ▼</span>
          </button>
        ) : <div />}
      </div>

      <div className="px-6 py-4">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-semibold text-slate-700">{boardNames}</h3>
          {totalRelPages > 1 && (
            <div className="flex items-center gap-1.5">
              <button
                onClick={() => setRelPage(p => Math.max(0, p - 1))}
                disabled={relPage === 0}
                className="w-6 h-6 flex items-center justify-center rounded text-xs text-slate-500 hover:bg-slate-100 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer transition-colors"
              >
                &lt;
              </button>
              <span className="text-xs text-slate-400">{relPage + 1}/{totalRelPages}</span>
              <button
                onClick={() => setRelPage(p => Math.min(totalRelPages - 1, p + 1))}
                disabled={relPage >= totalRelPages - 1}
                className="w-6 h-6 flex items-center justify-center rounded text-xs text-slate-500 hover:bg-slate-100 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer transition-colors"
              >
                &gt;
              </button>
            </div>
          )}
        </div>
        <div className="space-y-0.5">
          {pagedPosts.map(p => {
            const isCurrent = p.id === Number(postId)
            return (
              <button
                key={p.id}
                onClick={() => !isCurrent && onRelatedClick(p.id)}
                className={`w-full text-left px-3 py-2 rounded flex items-center justify-between ${
                  isCurrent
                    ? 'bg-blue-50 border border-blue-200 cursor-default'
                    : 'hover:bg-slate-50 cursor-pointer group'
                }`}
              >
                <span className={`text-sm truncate ${
                  isCurrent ? 'text-blue-700 font-medium' : 'text-slate-700 group-hover:text-blue-600'
                }`}>
                  {p.title}
                </span>
                <span className={`text-xs shrink-0 ml-2 ${
                  isCurrent ? 'text-blue-400' : 'text-slate-400'
                }`}>
                  {formatTime(p.createdAt)}
                </span>
              </button>
            )
          })}
        </div>
      </div>
    </div>
  )
}
