import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

// Register service worker for offline + installable PWA (production only)
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  // If a new worker takes over a page that was already controlled (deploy
  // happened while the app was open), reload once to pick up fresh assets.
  const hadController = !!navigator.serviceWorker.controller;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (hadController) location.reload();
  });
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('./sw.js')
      .then((reg) => reg.update())
      .catch(() => {});
  });
}
