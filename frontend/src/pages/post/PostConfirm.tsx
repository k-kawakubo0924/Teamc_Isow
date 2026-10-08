import type { PostForm } from '../../validation/postRules'
import { ErrorBanner } from '../ErrorBanner'
import type { SelectedImage } from './ImagePicker'
import { PostPhotoViewer } from './PostPhotoViewer'

type Props = {
  images: SelectedImage[]
  form: PostForm
  /** 選んだファッションの種類の表示名 */
  fashionName: string
  bannerMessage: string | null
  submitting: boolean
  onBack: () => void
  onPost: () => void
}

/**
 * 投稿内容の確認（design/Check post content.png）。
 * 画面画像の「下書き保存」は未対応のため、代わりに入力画面へ戻るボタンを置く
 */
export function PostConfirm({ images, form, fashionName, bannerMessage, submitting, onBack, onPost }: Props) {
  return (
    <main className="post-page">
      <header className="post-header">
        <button type="button" className="post-header-back" aria-label="入力画面に戻る" disabled={submitting} onClick={onBack}>
          ‹
        </button>
        <h1 className="post-title">投稿確認</h1>
      </header>

      <div className="post-body">
        {bannerMessage && <ErrorBanner message={bannerMessage} />}

        {/* 写真の切り替えは詳細画面と共通 */}
        <PostPhotoViewer urls={images.map((image) => image.previewUrl)} />

        <dl className="auth-confirm-list post-confirm-list">
          <ConfirmRow label="題名" value={form.title.trim()} />
          <ConfirmRow label="ファッション" value={fashionName} />
          <ConfirmRow label="タグ" value={form.tags.join(' ／ ')} />
          <ConfirmRow label="着用アイテム" value={form.wornItems.trim()} />
          <ConfirmRow label="投稿説明" value={form.description.trim()} />
          <ConfirmRow label="参考情報" value={form.referenceUrl.trim()} />
        </dl>
      </div>

      <div className="post-footer post-footer-split">
        <button type="button" className="post-button post-button-secondary" disabled={submitting} onClick={onBack}>
          戻る
        </button>
        <button type="button" className="post-button post-button-primary" disabled={submitting} onClick={onPost}>
          {submitting ? '投稿中...' : '投稿する'}
        </button>
      </div>
    </main>
  )
}

/** 未入力の項目は「未設定」と表示する。改行はそのまま表示する */
function ConfirmRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="auth-confirm-row">
      <dt>{label}</dt>
      <dd className={value === '' ? 'post-confirm-empty' : 'post-confirm-value'}>{value === '' ? '未設定' : value}</dd>
    </div>
  )
}
