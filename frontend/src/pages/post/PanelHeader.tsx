/** 選択パネルの見出し。「完了」で投稿作成の入力画面に戻る（選んだ内容はその場で反映済み） */
export function PanelHeader({ title, onClose }: { title: string; onClose: () => void }) {
  return (
    <header className="post-header">
      <button type="button" className="post-header-back" aria-label="入力画面に戻る" onClick={onClose}>
        ‹
      </button>
      <h1 className="post-title">{title}</h1>
      <button type="button" className="post-header-done" onClick={onClose}>
        完了
      </button>
    </header>
  )
}
