import { useEffect, useId, useState, type ChangeEvent } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { ApiError } from '../../api/client'
import { fetchMasters, type EnumOption, type MasterOption, type MastersResponse } from '../../api/masters'
import { fetchMyProfile, updateMyProfile, uploadProfileImage, type ProfileResponse } from '../../api/profile'
import { useAuth } from '../../auth/authContext'
import { IMAGE_ACCEPT, INVALID_INPUT_MESSAGE, validateImageFile } from '../../validation/postRules'
import {
  MAX_DISPLAY_NAME_LENGTH,
  MAX_HEIGHT_CM,
  MIN_HEIGHT_CM,
  toHeightCm,
  validateProfile,
  type ProfileFieldErrors,
  type ProfileForm,
} from '../../validation/profileRules'
import { ErrorBanner } from '../ErrorBanner'
import { PanelHeader } from '../post/PanelHeader'
import { goBack } from './goBack'
import '../auth.css'
import '../post/post.css'
import './settings.css'

type Panel = 'displayName' | 'gender' | 'heightCm' | 'ageGroup' | 'bodyTypeId' | 'personalColorId'

/** 選択パネルの1項目。value が null の項目は「未設定」 */
type Choice<T> = { value: T; label: string }

type SelectedImage = { file: File; previewUrl: string }

/**
 * プロフィール編集（docs/profile.md、design/EditProfile.png）。
 * 各行を押すと入力・選択のパネルを開き、画面上部の「保存」でまとめて保存する（それまではサーバーに送らない）。
 * 画面画像では「年齢」だが、仕様（docs/profile.md）どおり「年代」を選ぶ。
 */
function ProfileEditPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { token } = useAuth()
  const [masters, setMasters] = useState<MastersResponse | null>(null)
  const [profile, setProfile] = useState<ProfileResponse | null>(null)
  const [form, setForm] = useState<ProfileForm | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [image, setImage] = useState<SelectedImage | null>(null)
  const [panel, setPanel] = useState<Panel | null>(null)
  const [fieldErrors, setFieldErrors] = useState<ProfileFieldErrors>({})
  const [bannerMessage, setBannerMessage] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!token) return
    let cancelled = false
    Promise.all([fetchMyProfile(token), fetchMasters(token)])
      .then(([loadedProfile, loadedMasters]) => {
        if (cancelled) return
        setProfile(loadedProfile)
        setMasters(loadedMasters)
        setForm({
          displayName: loadedProfile.displayName,
          gender: loadedProfile.gender,
          heightCm: loadedProfile.heightCm?.toString() ?? '',
          ageGroup: loadedProfile.ageGroup,
          bodyTypeId: loadedProfile.bodyType?.id ?? null,
          personalColorId: loadedProfile.personalColor?.id ?? null,
        })
      })
      .catch((err: unknown) => {
        if (!cancelled) setLoadError(err instanceof ApiError ? err.message : 'プロフィールを読み込めませんでした。')
      })
    return () => {
      cancelled = true
    }
  }, [token])

  // 選び直したとき・画面を離れるときに、プレビュー用の URL を解放する
  useEffect(() => {
    return () => {
      if (image) URL.revokeObjectURL(image.previewUrl)
    }
  }, [image])

  const handleBack = () => goBack(navigate, location, '/settings')

  if (loadError) {
    return (
      <main className="settings-page">
        <EditHeader onBack={handleBack} />
        <div className="settings-body">
          <ErrorBanner message={loadError} />
        </div>
      </main>
    )
  }
  if (!form || !profile || !masters || !token) {
    return (
      <main className="settings-page">
        <EditHeader onBack={handleBack} />
        <p className="settings-loading">読み込み中…</p>
      </main>
    )
  }

  const updateForm = <K extends keyof ProfileForm>(key: K, value: ProfileForm[K]) => {
    setForm({ ...form, [key]: value })
    setFieldErrors((prev) => ({ ...prev, [key]: undefined }))
  }

  const handleSelectImage = (file: File) => {
    const message = validateImageFile(file)
    if (message) {
      setFieldErrors((prev) => ({ ...prev, image: message }))
      return
    }
    setImage({ file, previewUrl: URL.createObjectURL(file) })
    setFieldErrors((prev) => ({ ...prev, image: undefined }))
  }

  // 画像 → 項目の順に保存する。画像が受け付けられなかった場合は、項目も保存しない
  const handleSave = async () => {
    const errors = validateProfile(form)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      setBannerMessage(INVALID_INPUT_MESSAGE)
      return
    }
    setSaving(true)
    setBannerMessage(null)
    let step: 'image' | 'fields' = 'image'
    try {
      if (image) {
        // 画像は保存済みになるため、この後の項目の保存に失敗しても、画面には新しい画像を出しておく
        setProfile(await uploadProfileImage(image.file, token))
        setImage(null)
      }
      step = 'fields'
      await updateMyProfile(
        {
          displayName: form.displayName.trim(),
          gender: form.gender,
          heightCm: toHeightCm(form.heightCm),
          ageGroup: form.ageGroup,
          bodyTypeId: form.bodyTypeId,
          personalColorId: form.personalColorId,
        },
        token,
      )
      goBack(navigate, location, '/settings')
    } catch (err) {
      if (err instanceof ApiError) {
        // 画像の誤りは画像の行に、項目の誤りは各行に出す
        setFieldErrors(step === 'image' ? { image: err.message } : (err.body?.errors ?? {}))
        setBannerMessage(err.message)
      } else {
        setBannerMessage('保存できませんでした。時間をおいて再度お試しください。')
      }
      setSaving(false)
    }
  }

  // 非表示になった選択肢でも、設定済みなら表示して選べるようにする（docs/profile.md）
  const bodyTypes = withCurrent(masters.bodyTypes, profile.bodyType)
  const personalColors = withCurrent(masters.personalColors, profile.personalColor)

  if (panel === 'displayName' || panel === 'heightCm') {
    const isName = panel === 'displayName'
    return (
      <main className="post-page">
        <div className="post-panel">
          <PanelHeader title={isName ? '名前' : '身長'} onClose={() => setPanel(null)} />
          <div className="post-panel-body">
            {isName ? (
              <TextPanel
                label="名前"
                value={form.displayName}
                maxLength={MAX_DISPLAY_NAME_LENGTH}
                hint="@ユーザ名とは別の、自由に付けられる名前です"
                error={fieldErrors.displayName}
                onChange={(value) => updateForm('displayName', value)}
              />
            ) : (
              <TextPanel
                label="身長（cm）"
                value={form.heightCm}
                inputMode="numeric"
                hint={`${MIN_HEIGHT_CM}〜${MAX_HEIGHT_CM}cm。空欄にすると未設定になります`}
                error={fieldErrors.heightCm}
                onChange={(value) => updateForm('heightCm', value.replace(/[^0-9]/g, ''))}
              />
            )}
          </div>
        </div>
      </main>
    )
  }

  if (panel === 'gender' || panel === 'ageGroup') {
    const isGender = panel === 'gender'
    return (
      <ChoicePanel
        title={isGender ? '性別' : '年代'}
        choices={toEnumChoices(isGender ? masters.genders : masters.ageGroups)}
        selected={form[panel]}
        onSelect={(value) => {
          updateForm(panel, value)
          setPanel(null)
        }}
        onClose={() => setPanel(null)}
      />
    )
  }

  if (panel === 'bodyTypeId' || panel === 'personalColorId') {
    const isBodyType = panel === 'bodyTypeId'
    return (
      <ChoicePanel
        title={isBodyType ? '骨格タイプ' : 'パーソナルカラー'}
        choices={toMasterChoices(isBodyType ? bodyTypes : personalColors)}
        selected={form[panel]}
        onSelect={(value) => {
          updateForm(panel, value)
          setPanel(null)
        }}
        onClose={() => setPanel(null)}
      />
    )
  }

  const imageUrl = image?.previewUrl ?? profile.profileImageUrl

  return (
    <main className="settings-page">
      <EditHeader onBack={handleBack} saving={saving} onSave={handleSave} />

      {bannerMessage && (
        <div className="settings-banner">
          <ErrorBanner message={bannerMessage} />
        </div>
      )}

      <section className="profile-edit-image">
        <Avatar url={imageUrl} />
        <span className="profile-edit-image-label">プロフィール画像</span>
        <ImageChangeButton disabled={saving} onSelect={handleSelectImage} />
      </section>
      {fieldErrors.image && <p className="post-field-error profile-edit-image-error">{fieldErrors.image}</p>}

      <div className="profile-edit-rows">
        <Row
          label="名前"
          required
          value={form.displayName.trim() || undefined}
          error={fieldErrors.displayName}
          onClick={() => setPanel('displayName')}
        />
        <Row
          label="性別"
          value={labelOf(masters.genders, form.gender)}
          error={fieldErrors.gender}
          onClick={() => setPanel('gender')}
        />
        <Row
          label="身長"
          value={form.heightCm.trim() ? `${form.heightCm.trim()}cm` : undefined}
          error={fieldErrors.heightCm}
          onClick={() => setPanel('heightCm')}
        />
        <Row
          label="年代"
          value={labelOf(masters.ageGroups, form.ageGroup)}
          error={fieldErrors.ageGroup}
          onClick={() => setPanel('ageGroup')}
        />
        <Row
          label="骨格タイプ"
          value={bodyTypes.find((option) => option.id === form.bodyTypeId)?.name}
          error={fieldErrors.bodyTypeId}
          onClick={() => setPanel('bodyTypeId')}
        />
        <Row
          label="パーソナルカラー"
          value={personalColors.find((option) => option.id === form.personalColorId)?.name}
          error={fieldErrors.personalColorId}
          onClick={() => setPanel('personalColorId')}
        />
      </div>
    </main>
  )
}

function EditHeader({ onBack, saving = false, onSave }: { onBack: () => void; saving?: boolean; onSave?: () => void }) {
  return (
    <header className="settings-header">
      <button type="button" className="settings-back" aria-label="戻る" onClick={onBack}>
        ‹
      </button>
      <h1 className="settings-title settings-title-center">プロフィール編集</h1>
      {/* 読み込み中は保存できないが、見出しの位置がずれないよう同じ幅の場所を空けておく */}
      {onSave ? (
        <button type="button" className="settings-save" disabled={saving} onClick={onSave}>
          {saving ? '保存中…' : '保存'}
        </button>
      ) : (
        <span className="settings-save" aria-hidden="true" />
      )}
    </header>
  )
}

function Avatar({ url }: { url: string | null }) {
  if (url) {
    return <img className="profile-edit-avatar" src={url} alt="プロフィール画像" />
  }
  return (
    <span className="profile-edit-avatar profile-edit-avatar-empty" role="img" aria-label="プロフィール画像（未設定）">
      <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4" aria-hidden="true">
        <circle cx="12" cy="8.5" r="3.5" />
        <path d="M5 20c1.1-3.4 3.7-5 7-5s5.9 1.6 7 5" strokeLinecap="round" />
      </svg>
    </span>
  )
}

function ImageChangeButton({ disabled, onSelect }: { disabled: boolean; onSelect: (file: File) => void }) {
  const inputId = useId()
  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) onSelect(file)
    // 同じファイルをもう一度選んだときも change が起きるよう、選択をリセットする
    e.target.value = ''
  }
  return (
    <>
      <label htmlFor={inputId} className="profile-edit-image-change" aria-disabled={disabled}>
        変更
      </label>
      <input
        id={inputId}
        className="post-visually-hidden"
        type="file"
        accept={IMAGE_ACCEPT}
        disabled={disabled}
        onChange={handleChange}
      />
    </>
  )
}

/** value が undefined なら「未設定」と表示する */
function Row({
  label,
  required = false,
  value,
  error,
  onClick,
}: {
  label: string
  required?: boolean
  value?: string
  error?: string
  onClick: () => void
}) {
  return (
    <div className="profile-edit-row-wrap">
      <button type="button" className={`profile-edit-row${error ? ' profile-edit-row-error' : ''}`} onClick={onClick}>
        <span className="profile-edit-row-label">
          {label}
          {required && <span className="profile-edit-required">必須</span>}
        </span>
        <span className={`profile-edit-row-value${value ? '' : ' profile-edit-row-value-empty'}`}>{value ?? '未設定'}</span>
        <span className="profile-edit-row-arrow" aria-hidden="true">
          ›
        </span>
      </button>
      {error && <p className="post-field-error profile-edit-row-message">{error}</p>}
    </div>
  )
}

function TextPanel({
  label,
  value,
  maxLength,
  inputMode,
  hint,
  error,
  onChange,
}: {
  label: string
  value: string
  maxLength?: number
  inputMode?: 'numeric'
  hint: string
  error?: string
  onChange: (value: string) => void
}) {
  const inputId = useId()
  return (
    <div className="post-section">
      <div className="post-label-row">
        <label className="post-label" htmlFor={inputId}>
          {label}
        </label>
        {maxLength !== undefined && (
          <span className="post-counter">
            {value.length} / {maxLength}
          </span>
        )}
      </div>
      <input
        id={inputId}
        className={`post-input${error ? ' post-input-error' : ''}`}
        type="text"
        inputMode={inputMode}
        value={value}
        aria-invalid={error ? true : undefined}
        autoFocus
        onChange={(e) => onChange(e.target.value)}
      />
      <p className="post-hint">{hint}</p>
      {error && <p className="post-field-error">{error}</p>}
    </div>
  )
}

/** 選択肢の一覧。先頭に「未設定」を置き、選んだらすぐに閉じる */
function ChoicePanel<T extends string | number>({
  title,
  choices,
  selected,
  onSelect,
  onClose,
}: {
  title: string
  choices: Choice<T>[]
  selected: T | null
  onSelect: (value: T | null) => void
  onClose: () => void
}) {
  const all: Choice<T | null>[] = [{ value: null, label: '未設定' }, ...choices]
  return (
    <main className="post-page">
      <div className="post-panel">
        <PanelHeader title={title} onClose={onClose} />
        <ul className="post-panel-body post-options" role="radiogroup" aria-label={title}>
          {all.map((choice) => (
            <li key={choice.value ?? 'none'}>
              <button
                type="button"
                role="radio"
                aria-checked={choice.value === selected}
                className="post-option"
                onClick={() => onSelect(choice.value)}
              >
                {choice.label}
                {choice.value === selected && <span aria-hidden="true">✓</span>}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </main>
  )
}

function toEnumChoices(options: EnumOption[]): Choice<string>[] {
  return options.map((option) => ({ value: option.code, label: option.label }))
}

function toMasterChoices(options: MasterOption[]): Choice<number>[] {
  return options.map((option) => ({ value: option.id, label: option.name }))
}

function labelOf(options: EnumOption[], code: string | null): string | undefined {
  return options.find((option) => option.code === code)?.label
}

/** 選択肢の一覧に、設定済みの選択肢が無ければ（非表示になったもの）末尾に加える */
function withCurrent(options: MasterOption[], current: MasterOption | null): MasterOption[] {
  if (!current || options.some((option) => option.id === current.id)) return options
  return [...options, current]
}

export default ProfileEditPage
