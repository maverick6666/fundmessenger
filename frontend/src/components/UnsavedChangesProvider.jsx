import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import ConfirmModal from './ConfirmModal'

const UnsavedChangesContext = createContext(null)

let nextGuardId = 1

export function UnsavedChangesProvider({ children }) {
  const [dirtyEntries, setDirtyEntries] = useState({})
  const [pendingAction, setPendingAction] = useState(null)
  const bypassPromptRef = useRef(false)

  const hasUnsavedChanges = Object.keys(dirtyEntries).length > 0

  const setDirty = useCallback((id, isDirty) => {
    setDirtyEntries(prev => {
      if (isDirty) {
        if (prev[id]) return prev
        return { ...prev, [id]: true }
      }

      if (!prev[id]) return prev
      const next = { ...prev }
      delete next[id]
      return next
    })
  }, [])

  const clearDirty = useCallback((id) => {
    setDirtyEntries(prev => {
      if (!prev[id]) return prev
      const next = { ...prev }
      delete next[id]
      return next
    })
  }, [])

  const runWithoutPrompt = useCallback((action) => {
    const previous = bypassPromptRef.current
    bypassPromptRef.current = true

    try {
      return action()
    } finally {
      queueMicrotask(() => {
        bypassPromptRef.current = previous
      })
    }
  }, [])

  const confirmIfDirty = useCallback((action) => {
    if (bypassPromptRef.current || !hasUnsavedChanges) {
      return runWithoutPrompt(action)
    }

    setPendingAction(() => action)
    return undefined
  }, [hasUnsavedChanges, runWithoutPrompt])

  const handleConfirm = useCallback(() => {
    const action = pendingAction
    setPendingAction(null)
    if (action) runWithoutPrompt(action)
  }, [pendingAction, runWithoutPrompt])

  const handleClose = useCallback(() => {
    setPendingAction(null)
  }, [])

  useEffect(() => {
    if (!hasUnsavedChanges) return undefined

    const handler = (event) => {
      event.preventDefault()
      event.returnValue = ''
    }

    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [hasUnsavedChanges])

  const value = useMemo(() => ({
    hasUnsavedChanges,
    confirmIfDirty,
    setDirty,
    clearDirty,
    runWithoutPrompt,
  }), [hasUnsavedChanges, confirmIfDirty, setDirty, clearDirty, runWithoutPrompt])

  return (
    <UnsavedChangesContext.Provider value={value}>
      {children}
      {pendingAction && (
        <ConfirmModal
          title="저장되지 않은 변경사항"
          message="작성 중인 내용이 저장되지 않습니다. 이동하시겠습니까?"
          confirmLabel="이동"
          onConfirm={handleConfirm}
          onClose={handleClose}
          danger
        />
      )}
    </UnsavedChangesContext.Provider>
  )
}

export function useUnsavedChanges() {
  const context = useContext(UnsavedChangesContext)
  if (!context) {
    throw new Error('useUnsavedChanges must be used within UnsavedChangesProvider')
  }

  return context
}

export function useUnsavedChangesGuard(isDirty) {
  const { setDirty, clearDirty } = useUnsavedChanges()
  const idRef = useRef(null)

  if (!idRef.current) {
    idRef.current = `unsaved-guard-${nextGuardId++}`
  }

  useEffect(() => {
    if (isDirty) {
      setDirty(idRef.current, true)
    } else {
      clearDirty(idRef.current)
    }

    return () => {
      clearDirty(idRef.current)
    }
  }, [clearDirty, isDirty, setDirty])
}

export function useGuardedNavigate() {
  const navigate = useNavigate()
  const { confirmIfDirty } = useUnsavedChanges()

  return useCallback((to, options) => {
    confirmIfDirty(() => navigate(to, options))
  }, [confirmIfDirty, navigate])
}

export function useGuardedSearchParams() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { confirmIfDirty } = useUnsavedChanges()

  const guardedSetSearchParams = useCallback((nextInit, navigateOptions) => {
    confirmIfDirty(() => setSearchParams(nextInit, navigateOptions))
  }, [confirmIfDirty, setSearchParams])

  return [searchParams, guardedSetSearchParams]
}
