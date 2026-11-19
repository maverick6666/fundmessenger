import useToastStore from '../store/toastStore'
import ToastCard from './ToastCard'

export default function ToastStack() {
  const toasts = useToastStore(s => s.toasts)

  if (toasts.length === 0) return null

  return (
    <div className="fixed top-4 right-4 z-50 flex flex-col gap-3">
      {toasts.map(toast => (
        <ToastCard key={toast.toastId} toast={toast} />
      ))}
    </div>
  )
}
