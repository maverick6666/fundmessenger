import useToastStore from '../store/toastStore'

/**
 * API 에러를 토스트로 표시하는 공통 유틸
 *
 * 사용법:
 *   try { await api.post(...) } catch (e) { handleApiError(e, '게시글 저장') }
 *
 * @param {Error} error - catch 블록의 에러 객체
 * @param {string} [context] - 사용자에게 보여줄 작업 컨텍스트 (예: '게시글 저장')
 */
export default function handleApiError(error, context) {
  const message = context
    ? `${context} 실패: ${error.message}`
    : error.message || '요청 처리 중 오류가 발생했습니다'

  useToastStore.getState().addToast({
    type: 'ERROR',
    title: '오류',
    body: message,
  })
}
