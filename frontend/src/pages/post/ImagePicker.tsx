import { useId, type ChangeEvent } from 'react'
import { IMAGE_ACCEPT, MAX_IMAGES } from '../../validation/postRules'

export type SelectedImage = {
  /** 並べ替えても同じ写真だと分かるようにするための番号（React の key に使う） */
  id: number
  file: File
  /** プレビュー用の URL（URL.createObjectURL）。不要になったら URL.revokeObjectURL で解放する */
  previewUrl: string
}

type Props = {
  images: SelectedImage[]
  error?: string
  onAdd: (files: File[]) => void
  onRemove: (id: number) => void
  /** direction: -1 で前へ、1 で後ろへ */
  onMove: (id: number, direction: -1 | 1) => void
}

/**
 * 写真の選択とプレビュー（design/Post Creation Screen.png）。
 * 並びがそのまま表示順になり、1枚目が一覧のサムネイルになる。
 * スマホではドラッグでの並べ替えが扱いにくいため、前後に移動するボタンで並べ替える
 */
export function ImagePicker({ images, error, onAdd, onRemove, onMove }: Props) {
  const inputId = useId()
  const isFull = images.length >= MAX_IMAGES

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    onAdd(Array.from(e.target.files ?? []))
    // 同じファイルをもう一度選んだときも change が起きるよう、選択をリセットする
    e.target.value = ''
  }

  return (
    <section className="post-section">
      <div className="post-label-row">
        <span className="post-label" id={`${inputId}-label`}>
          写真 <span className="post-required">必須</span>
        </span>
        <span className="post-counter">
          {images.length} / {MAX_IMAGES}枚
        </span>
      </div>

      <ul className="post-images" aria-labelledby={`${inputId}-label`}>
        <li>
          <label
            htmlFor={inputId}
            className={`post-image-add${error ? ' post-image-add-error' : ''}${isFull ? ' post-image-add-disabled' : ''}`}
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
              <path d="M4 8h3l1.5-2h7L17 8h3v11H4z" strokeLinejoin="round" />
              <circle cx="12" cy="13" r="3.2" />
            </svg>
            <span>{isFull ? '上限です' : '写真を選ぶ'}</span>
          </label>
          <input
            id={inputId}
            className="post-visually-hidden"
            type="file"
            accept={IMAGE_ACCEPT}
            multiple
            disabled={isFull}
            onChange={handleChange}
          />
        </li>

        {images.map((image, index) => (
          <li key={image.id} className="post-image">
            <img src={image.previewUrl} alt={`${index + 1}枚目の写真`} />
            {index === 0 && <span className="post-image-badge">サムネイル</span>}
            <button
              type="button"
              className="post-image-remove"
              aria-label={`${index + 1}枚目の写真を削除`}
              onClick={() => onRemove(image.id)}
            >
              ×
            </button>
            <div className="post-image-move">
              <button
                type="button"
                aria-label={`${index + 1}枚目の写真を前へ移動`}
                disabled={index === 0}
                onClick={() => onMove(image.id, -1)}
              >
                ‹
              </button>
              <button
                type="button"
                aria-label={`${index + 1}枚目の写真を後ろへ移動`}
                disabled={index === images.length - 1}
                onClick={() => onMove(image.id, 1)}
              >
                ›
              </button>
            </div>
          </li>
        ))}
      </ul>

      <p className="post-hint">JPEG・PNG、1枚 10MB まで。1枚目が一覧のサムネイルになります。</p>
      {error && <p className="post-field-error">{error}</p>}
    </section>
  )
}
