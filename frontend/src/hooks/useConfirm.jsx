import { useState, useCallback } from 'react'
import ConfirmModal from '../components/ConfirmModal'

/**
 * Promise 기반 확인 모달 훅
 * @returns {{ confirm: (message, opts?) => Promise<boolean>, modal: JSX.Element|null }}
 *
 * 사용법:
 *   const { confirm, modal } = useConfirm()
 *   const ok = await confirm('삭제하시겠습니까?', { confirmLabel: '삭제', danger: true })
 *   if (!ok) return
 *   // JSX에 {modal} 렌더
 */
export default function useConfirm() {
  const [state, setState] = useState({ open: false, message: '', resolve: null, confirmLabel: '확인', danger: false })

  const confirm = useCallback((message, { confirmLabel = '확인', danger = false } = {}) => {
    return new Promise((resolve) => {
      setState({ open: true, message, resolve, confirmLabel, danger })
    })
  }, [])

  const handleConfirm = () => {
    state.resolve?.(true)
    setState(prev => ({ ...prev, open: false }))
  }

  const handleCancel = () => {
    state.resolve?.(false)
    setState(prev => ({ ...prev, open: false }))
  }

  const modal = state.open ? (
    <ConfirmModal
      title="확인"
      message={state.message}
      onConfirm={handleConfirm}
      onClose={handleCancel}
      confirmLabel={state.confirmLabel}
      danger={state.danger}
    />
  ) : null

  return { confirm, modal }
}
