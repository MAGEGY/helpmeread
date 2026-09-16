import { SUPPORTED_LANGUAGES, type Language } from '../services/languages';

interface Props {
  selected: Language;
  onSelect: (lang: Language) => void;
  onClose: () => void;
}

export function LanguageSelector({ selected, onSelect, onClose }: Props) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>Select Language</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="lang-grid">
          {SUPPORTED_LANGUAGES.map((lang) => (
            <button
              key={lang.code}
              className={`lang-item ${selected.code === lang.code ? 'lang-active' : ''}`}
              onClick={() => {
                onSelect(lang);
                onClose();
              }}
            >
              <span className="lang-flag">{lang.flag}</span>
              <span className="lang-name">{lang.name}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
