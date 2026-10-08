import { useState } from 'react'
import './postPhoto.css'

/**
 * 投稿の写真を1枚ずつ表示する（投稿の確認画面・詳細画面で共通。design/Check post content.png）。
 * 2枚以上なら、右上に「1 / 3」と枚数を出し、‹ › のボタンで切り替える。
 * 1枚のときは切り替えるものがないため、枚数表示もボタンも出さない。
 * 横スワイプは入れていない（入れる場合は確認画面と詳細画面の両方に同時に入れる。docs/post.md）
 *
 * @param urls 写真の URL（表示順）。確認画面ではまだ保存していない写真のプレビュー、詳細画面では /uploads/ の URL
 */
export function PostPhotoViewer({ urls }: { urls: string[] }) {
  const [index, setIndex] = useState(0)
  // 写真が減った場合（確認画面から入力画面に戻って削除した場合など）も、範囲外にならないようにする
  const current = Math.min(index, urls.length - 1)
  const url = urls[current]

  return (
    <section className="post-photo" aria-label="写真">
      {url && <img src={url} alt={urls.length > 1 ? `${current + 1}枚目の写真` : '写真'} />}
      {urls.length > 1 && (
        <>
          <span className="post-photo-count">
            {current + 1} / {urls.length}
          </span>
          <button
            type="button"
            className="post-photo-nav post-photo-prev"
            aria-label="前の写真"
            disabled={current === 0}
            onClick={() => setIndex(current - 1)}
          >
            ‹
          </button>
          <button
            type="button"
            className="post-photo-nav post-photo-next"
            aria-label="次の写真"
            disabled={current === urls.length - 1}
            onClick={() => setIndex(current + 1)}
          >
            ›
          </button>
        </>
      )}
    </section>
  )
}
