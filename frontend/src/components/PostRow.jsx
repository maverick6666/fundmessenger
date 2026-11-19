import { formatTime } from '../lib/format'
import AiBadge from './AiBadge'

/**
 * 글 행 컴포넌트
 * Props: { post, onClick, showBoardBadge?, showCategoryBadge? }
 *
 * post: { id, title, authorName, boards: [{id, name}], semiCategories[], createdAt, updatedAt }
 */
export default function PostRow({ post, onClick, showBoardBadge = false, showCategoryBadge = false }) {
  return (
    <button
      onClick={onClick}
      className="w-full text-left px-4 py-3 hover:bg-slate-50 transition-colors cursor-pointer group"
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-sm font-medium text-slate-900 truncate group-hover:text-slate-600">
          {post.title}
        </span>
        <span className="text-xs text-slate-400 shrink-0">
          {formatTime(post.createdAt)}
        </span>
      </div>
      <div className="flex items-center gap-2 mt-1 flex-wrap">
        {showBoardBadge && post.boards?.length > 0 && post.boards.map(b => (
          <span key={b.id} className="text-xs px-1.5 py-0.5 bg-blue-50 text-blue-500 rounded font-medium">
            {b.name}
          </span>
        ))}
        {showCategoryBadge && post.semiCategories?.map(cat => (
          <span key={cat.id} className="text-xs px-1.5 py-0.5 bg-slate-100 text-slate-500 rounded font-medium">
            {cat.name}
          </span>
        ))}
        <AiBadge aiGenerated={post.aiGenerated} aiEdited={post.aiEdited} />
        <span className="text-xs text-slate-400">{post.authorName}</span>
        {post.likeCount > 0 && (
          <span className="text-xs text-rose-400 flex items-center gap-0.5">
            <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20"><path d="M3.172 5.172a4 4 0 015.656 0L10 6.343l1.172-1.171a4 4 0 115.656 5.656L10 17.657l-6.828-6.829a4 4 0 010-5.656z" /></svg>
            {post.likeCount}
          </span>
        )}
        {post.updatedAt && (
          <span className="text-xs text-slate-300">(수정됨)</span>
        )}
      </div>
    </button>
  )
}
